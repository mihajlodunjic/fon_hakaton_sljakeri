package rs.fon.hakaton.audionav.storage

import rs.fon.hakaton.audionav.domain.CooldownEntry
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent

data class CooldownCheckResult(
    val isBlocked: Boolean,
    val remainingMs: Long,
)

class CooldownRepository(
    private val receiverRuntimeStorage: ReceiverRuntimeStorage,
) {

    private val cooldownEntries = linkedMapOf<String, CooldownEntry>()
    private val recentEvents = mutableListOf<DetectedBeaconEvent>()

    suspend fun initialize(): ReceiverRuntimeSnapshot {
        val snapshot = receiverRuntimeStorage.load()
        cooldownEntries.clear()
        snapshot.cooldownEntries.forEach { entry ->
            cooldownEntries[key(entry.beaconId, entry.messageCode)] = entry
        }
        recentEvents.clear()
        recentEvents.addAll(snapshot.recentEvents.take(MAX_RECENT_EVENTS))
        return currentSnapshot()
    }

    fun check(
        beaconId: String,
        messageCode: Short,
        now: Long,
    ): CooldownCheckResult {
        val entry = cooldownEntries[key(beaconId, messageCode)] ?: return CooldownCheckResult(
            isBlocked = false,
            remainingMs = 0L,
        )

        val remainingMs = COOLDOWN_WINDOW_MS - (now - entry.lastTriggeredAt)
        return if (remainingMs > 0L) {
            CooldownCheckResult(
                isBlocked = true,
                remainingMs = remainingMs,
            )
        } else {
            CooldownCheckResult(
                isBlocked = false,
                remainingMs = 0L,
            )
        }
    }

    suspend fun recordStableEvent(event: DetectedBeaconEvent) {
        if (event.wasAnnounced) {
            cooldownEntries[key(event.beaconId, event.messageCode)] = CooldownEntry(
                beaconId = event.beaconId,
                messageCode = event.messageCode,
                lastTriggeredAt = event.detectedAt,
            )
        }

        recentEvents.add(0, event)
        if (recentEvents.size > MAX_RECENT_EVENTS) {
            recentEvents.subList(MAX_RECENT_EVENTS, recentEvents.size).clear()
        }

        receiverRuntimeStorage.save(currentSnapshot())
    }

    fun recentEvents(): List<DetectedBeaconEvent> = recentEvents.toList()

    private fun currentSnapshot(): ReceiverRuntimeSnapshot {
        return ReceiverRuntimeSnapshot(
            cooldownEntries = cooldownEntries.values.toList(),
            recentEvents = recentEvents.toList(),
        )
    }

    private fun key(
        beaconId: String,
        messageCode: Short,
    ): String {
        return "$beaconId#$messageCode"
    }

    companion object {
        const val COOLDOWN_WINDOW_MS: Long = 10_000L
        const val MAX_RECENT_EVENTS: Int = 20
    }
}

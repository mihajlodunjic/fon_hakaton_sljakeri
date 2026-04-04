package rs.fon.hakaton.audionav.storage

import rs.fon.hakaton.audionav.domain.CooldownEntry
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent

data class ReceiverRuntimeSnapshot(
    val cooldownEntries: List<CooldownEntry> = emptyList(),
    val recentEvents: List<DetectedBeaconEvent> = emptyList(),
)

interface ReceiverRuntimeStorage {
    suspend fun load(): ReceiverRuntimeSnapshot

    suspend fun save(snapshot: ReceiverRuntimeSnapshot)
}

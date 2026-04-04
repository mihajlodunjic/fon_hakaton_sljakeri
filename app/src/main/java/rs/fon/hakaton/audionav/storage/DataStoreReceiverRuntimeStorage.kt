package rs.fon.hakaton.audionav.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import rs.fon.hakaton.audionav.domain.CooldownEntry
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

private val Context.receiverRuntimeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "receiver_runtime",
)

class DataStoreReceiverRuntimeStorage(
    private val dataStore: DataStore<Preferences>,
) : ReceiverRuntimeStorage {

    override suspend fun load(): ReceiverRuntimeSnapshot {
        val preferences = dataStore.data.first()

        val cooldownEntries = deserializeCooldownEntries(preferences[COOLDOWN_ENTRIES])
        val recentEvents = deserializeRecentEvents(preferences[RECENT_EVENTS])

        return ReceiverRuntimeSnapshot(
            cooldownEntries = cooldownEntries,
            recentEvents = recentEvents,
        )
    }

    override suspend fun save(snapshot: ReceiverRuntimeSnapshot) {
        dataStore.edit { preferences ->
            preferences[COOLDOWN_ENTRIES] = serializeCooldownEntries(snapshot.cooldownEntries)
            preferences[RECENT_EVENTS] = serializeRecentEvents(snapshot.recentEvents)
        }
    }

    companion object {
        private val COOLDOWN_ENTRIES = stringPreferencesKey("cooldown_entries")
        private val RECENT_EVENTS = stringPreferencesKey("recent_events")
        private const val RECORD_SEPARATOR = "\n"
        private const val FIELD_SEPARATOR = "|"

        fun fromContext(context: Context): DataStoreReceiverRuntimeStorage {
            return DataStoreReceiverRuntimeStorage(context.applicationContext.receiverRuntimeDataStore)
        }

        private fun serializeCooldownEntries(entries: List<CooldownEntry>): String {
            return entries.joinToString(separator = RECORD_SEPARATOR) { entry ->
                listOf(
                    entry.beaconId,
                    entry.messageCode.toString(),
                    entry.lastTriggeredAt.toString(),
                ).joinToString(separator = FIELD_SEPARATOR)
            }
        }

        private fun deserializeCooldownEntries(raw: String?): List<CooldownEntry> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }

            return raw.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split(FIELD_SEPARATOR)
                    if (parts.size != 3) {
                        return@mapNotNull null
                    }

                    val messageCode = parts[1].toShortOrNull() ?: return@mapNotNull null
                    val lastTriggeredAt = parts[2].toLongOrNull() ?: return@mapNotNull null

                    CooldownEntry(
                        beaconId = parts[0],
                        messageCode = messageCode,
                        lastTriggeredAt = lastTriggeredAt,
                    )
                }
                .toList()
        }

        private fun serializeRecentEvents(events: List<DetectedBeaconEvent>): String {
            return events.joinToString(separator = RECORD_SEPARATOR) { event ->
                listOf(
                    event.beaconId,
                    event.detectedAt.toString(),
                    event.rssi.toString(),
                    event.pointType.code.toString(),
                    event.priority.code.toString(),
                    event.messageCode.toString(),
                    if (event.wasAnnounced) "1" else "0",
                ).joinToString(separator = FIELD_SEPARATOR)
            }
        }

        private fun deserializeRecentEvents(raw: String?): List<DetectedBeaconEvent> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }

            return raw.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split(FIELD_SEPARATOR)
                    if (parts.size != 7) {
                        return@mapNotNull null
                    }

                    val detectedAt = parts[1].toLongOrNull() ?: return@mapNotNull null
                    val rssi = parts[2].toIntOrNull() ?: return@mapNotNull null
                    val pointType = PointType.fromCode(parts[3].toIntOrNull() ?: return@mapNotNull null)
                        ?: return@mapNotNull null
                    val priority = Priority.fromCode(parts[4].toIntOrNull() ?: return@mapNotNull null)
                        ?: return@mapNotNull null
                    val messageCode = parts[5].toShortOrNull() ?: return@mapNotNull null
                    val wasAnnounced = when (parts[6]) {
                        "1" -> true
                        "0" -> false
                        else -> return@mapNotNull null
                    }

                    DetectedBeaconEvent(
                        beaconId = parts[0],
                        detectedAt = detectedAt,
                        rssi = rssi,
                        pointType = pointType,
                        priority = priority,
                        messageCode = messageCode,
                        wasAnnounced = wasAnnounced,
                    )
                }
                .toList()
        }
    }
}

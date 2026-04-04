package rs.fon.hakaton.audionav.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.fon.hakaton.audionav.domain.CooldownEntry
import rs.fon.hakaton.audionav.domain.DetectedBeaconEvent
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreReceiverRuntimeStorageTest {

    @Test
    fun `save and load round-trip returns same receiver runtime snapshot`() = runTest {
        val storage = createStorage(backgroundScope)
        val snapshot = ReceiverRuntimeSnapshot(
            cooldownEntries = listOf(
                CooldownEntry(
                    beaconId = "beacon-a",
                    messageCode = 1,
                    lastTriggeredAt = 1_000L,
                ),
            ),
            recentEvents = listOf(
                DetectedBeaconEvent(
                    beaconId = "beacon-a",
                    detectedAt = 1_500L,
                    rssi = -60,
                    pointType = PointType.CROSSWALK,
                    priority = Priority.MEDIUM,
                    messageCode = 1,
                    wasAnnounced = true,
                ),
            ),
        )

        storage.save(snapshot)

        assertEquals(snapshot, storage.load())
    }

    @Test
    fun `load ignores invalid serialized records`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val storage = DataStoreReceiverRuntimeStorage(dataStore)

        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("cooldown_entries")] = "broken|entry"
            preferences[stringPreferencesKey("recent_events")] = "bad|event|payload"
        }
        val loaded = storage.load()

        assertTrue(loaded.cooldownEntries.isEmpty())
        assertTrue(loaded.recentEvents.isEmpty())
    }

    private fun createStorage(scope: CoroutineScope): DataStoreReceiverRuntimeStorage {
        val file = createTempDataStoreFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreReceiverRuntimeStorage(dataStore)
    }

    private fun createTempDataStoreFile(): File {
        val directory = Files.createTempDirectory("receiver-runtime").toFile()
        directory.deleteOnExit()
        return File(directory, "receiver.preferences_pb")
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private var currentPreferences: Preferences = emptyPreferences()

        override val data: Flow<Preferences>
            get() = flowOf(currentPreferences)

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            currentPreferences = transform(currentPreferences)
            return currentPreferences
        }
    }
}

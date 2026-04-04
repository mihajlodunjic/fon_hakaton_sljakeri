package rs.fon.hakaton.audionav.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreBeaconConfigStorageTest {

    @Test
    fun `save and load round-trip returns same beacon config`() = runTest {
        val storage = createStorage(backgroundScope)
        val config = validConfig()

        storage.save(config)

        assertEquals(config, storage.load())
    }

    @Test
    fun `load returns null for invalid partial preferences`() = runTest {
        val file = createTempDataStoreFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val storage = DataStoreBeaconConfigStorage(dataStore)

        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("beacon_id")] = "123e4567-e89b-12d3-a456-426614174000"
            preferences[stringPreferencesKey("beacon_label")] = "Only partial config"
            preferences[intPreferencesKey("beacon_point_type_code")] = PointType.CROSSWALK.code
            preferences[intPreferencesKey("beacon_priority_code")] = Priority.MEDIUM.code
        }

        assertNull(storage.load())
    }

    @Test
    fun `clearActiveFlag resets persisted active state`() = runTest {
        val storage = DataStoreBeaconConfigStorage(InMemoryPreferencesDataStore())
        storage.save(validConfig(isActive = true))

        storage.clearActiveFlag()

        assertEquals(false, storage.load()?.isActive)
    }

    private fun createStorage(scope: CoroutineScope): DataStoreBeaconConfigStorage {
        val file = createTempDataStoreFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreBeaconConfigStorage(dataStore)
    }

    private fun createTempDataStoreFile(): File {
        val directory = Files.createTempDirectory("beacon-config").toFile()
        directory.deleteOnExit()
        return File(directory, "beacon.preferences_pb")
    }

    private fun validConfig(isActive: Boolean = false): BeaconConfig {
        return BeaconConfig(
            beaconId = "123e4567-e89b-12d3-a456-426614174000",
            label = "Crosswalk A",
            pointType = PointType.CROSSWALK,
            priority = Priority.MEDIUM,
            messageCode = 1,
            azimuthDegrees = 90,
            isActive = isActive,
            lastUpdatedAt = 1234L,
        )
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

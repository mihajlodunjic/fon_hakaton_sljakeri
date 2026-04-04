package rs.fon.hakaton.audionav.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.BeaconConfigValidator
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

private val Context.beaconConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "beacon_config",
)

class DataStoreBeaconConfigStorage(
    private val dataStore: DataStore<Preferences>,
) : BeaconConfigStorage {

    override suspend fun save(config: BeaconConfig) {
        dataStore.edit { preferences ->
            preferences[BEACON_ID] = config.beaconId
            preferences[BEACON_LABEL] = config.label
            preferences[BEACON_POINT_TYPE_CODE] = config.pointType.code
            preferences[BEACON_PRIORITY_CODE] = config.priority.code
            preferences[BEACON_MESSAGE_CODE] = config.messageCode.toInt()
            preferences[BEACON_AZIMUTH_DEGREES] = config.azimuthDegrees
            preferences[BEACON_LAST_UPDATED_AT] = config.lastUpdatedAt
            preferences[BEACON_IS_ACTIVE] = config.isActive
        }
    }

    override suspend fun load(): BeaconConfig? {
        val preferences = dataStore.data.first()

        val beaconId = preferences[BEACON_ID] ?: return null
        val label = preferences[BEACON_LABEL] ?: return null
        val pointType = PointType.fromCode(preferences[BEACON_POINT_TYPE_CODE] ?: return null)
            ?: return null
        val priority = Priority.fromCode(preferences[BEACON_PRIORITY_CODE] ?: return null)
            ?: return null
        val messageCode = (preferences[BEACON_MESSAGE_CODE] ?: return null).toShort()
        val azimuthDegrees = preferences[BEACON_AZIMUTH_DEGREES] ?: return null
        val lastUpdatedAt = preferences[BEACON_LAST_UPDATED_AT] ?: return null
        val isActive = preferences[BEACON_IS_ACTIVE] ?: return null

        val config = BeaconConfig(
            beaconId = beaconId,
            label = label,
            pointType = pointType,
            priority = priority,
            messageCode = messageCode,
            azimuthDegrees = azimuthDegrees,
            isActive = isActive,
            lastUpdatedAt = lastUpdatedAt,
        )

        return if (BeaconConfigValidator.isValid(config)) {
            config
        } else {
            null
        }
    }

    override suspend fun clearActiveFlag() {
        dataStore.edit { preferences ->
            if (preferences.contains(BEACON_IS_ACTIVE)) {
                preferences[BEACON_IS_ACTIVE] = false
            }
        }
    }

    companion object {
        fun fromContext(context: Context): DataStoreBeaconConfigStorage {
            return DataStoreBeaconConfigStorage(context.applicationContext.beaconConfigDataStore)
        }

        private val BEACON_ID = stringPreferencesKey("beacon_id")
        private val BEACON_LABEL = stringPreferencesKey("beacon_label")
        private val BEACON_POINT_TYPE_CODE = intPreferencesKey("beacon_point_type_code")
        private val BEACON_PRIORITY_CODE = intPreferencesKey("beacon_priority_code")
        private val BEACON_MESSAGE_CODE = intPreferencesKey("beacon_message_code")
        private val BEACON_AZIMUTH_DEGREES = intPreferencesKey("beacon_azimuth_degrees")
        private val BEACON_LAST_UPDATED_AT = longPreferencesKey("beacon_last_updated_at")
        private val BEACON_IS_ACTIVE = booleanPreferencesKey("beacon_is_active")
    }
}

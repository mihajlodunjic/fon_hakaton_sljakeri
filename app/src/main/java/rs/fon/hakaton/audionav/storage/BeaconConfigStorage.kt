package rs.fon.hakaton.audionav.storage

import rs.fon.hakaton.audionav.domain.BeaconConfig

interface BeaconConfigStorage {
    suspend fun save(config: BeaconConfig)
    suspend fun load(): BeaconConfig?
    suspend fun clearActiveFlag()
}


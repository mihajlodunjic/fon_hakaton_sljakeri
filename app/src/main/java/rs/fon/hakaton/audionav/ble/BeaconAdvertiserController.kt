package rs.fon.hakaton.audionav.ble

import rs.fon.hakaton.audionav.domain.BeaconConfig

interface BeaconAdvertiserController {
    fun isSupported(): Boolean
    fun startAdvertising(config: BeaconConfig, onResult: (BeaconAdvertiseResult) -> Unit)
    fun stopAdvertising()
}

sealed interface BeaconAdvertiseResult {
    data object Started : BeaconAdvertiseResult
    data object Stopped : BeaconAdvertiseResult
    data class Failure(val code: Int, val message: String) : BeaconAdvertiseResult
}


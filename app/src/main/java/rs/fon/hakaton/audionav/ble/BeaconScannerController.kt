package rs.fon.hakaton.audionav.ble

import rs.fon.hakaton.audionav.domain.DecodedBeaconPayload

sealed interface BeaconScanEvent {
    data object Started : BeaconScanEvent
    data object Stopped : BeaconScanEvent

    data class BeaconDetected(
        val payload: DecodedBeaconPayload,
        val rssi: Int,
        val detectedAt: Long,
    ) : BeaconScanEvent

    data class Failure(
        val code: Int?,
        val message: String,
        val retryable: Boolean,
    ) : BeaconScanEvent
}

interface BeaconScannerController {
    fun isSupported(): Boolean

    fun startScanning(onEvent: (BeaconScanEvent) -> Unit)

    fun stopScanning()
}

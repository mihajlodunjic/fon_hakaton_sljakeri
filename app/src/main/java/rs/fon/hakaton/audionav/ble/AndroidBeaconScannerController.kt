package rs.fon.hakaton.audionav.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import rs.fon.hakaton.audionav.AppLogger
import rs.fon.hakaton.audionav.LogTag

class AndroidBeaconScannerController(
    context: Context,
) : BeaconScannerController {

    private val bluetoothManager = context.applicationContext.getSystemService(BluetoothManager::class.java)
    private var activeCallback: ScanCallback? = null
    private var activeEventCallback: ((BeaconScanEvent) -> Unit)? = null

    @SuppressLint("MissingPermission")
    override fun isSupported(): Boolean {
        val adapter = bluetoothManager?.adapter ?: return false
        return adapter.bluetoothLeScanner != null
    }

    @SuppressLint("MissingPermission")
    override fun startScanning(onEvent: (BeaconScanEvent) -> Unit) {
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null) {
            onEvent(
                BeaconScanEvent.Failure(
                    code = ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED,
                    message = mapScanFailure(ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED),
                    retryable = false,
                ),
            )
            return
        }

        stopScanningInternal(notifyStopped = false)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result, onEvent)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    handleScanResult(result, onEvent)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                AppLogger.e(LogTag.BLE_SCAN, "BLE scan failed with code=$errorCode")
                activeCallback = null
                activeEventCallback = null
                onEvent(
                    BeaconScanEvent.Failure(
                        code = errorCode,
                        message = mapScanFailure(errorCode),
                        retryable = isRetryable(errorCode),
                    ),
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        activeCallback = callback
        activeEventCallback = onEvent

        try {
            scanner.startScan(null, settings, callback)
            onEvent(BeaconScanEvent.Started)
        } catch (exception: SecurityException) {
            AppLogger.e(LogTag.BLE_SCAN, "Missing permission for BLE scan", exception)
            activeCallback = null
            activeEventCallback = null
            onEvent(
                BeaconScanEvent.Failure(
                    code = null,
                    message = "Nedostaju dozvole za BLE skeniranje.",
                    retryable = false,
                ),
            )
        } catch (exception: IllegalStateException) {
            AppLogger.e(LogTag.BLE_SCAN, "Bluetooth scanner is not in a valid state", exception)
            activeCallback = null
            activeEventCallback = null
            onEvent(
                BeaconScanEvent.Failure(
                    code = null,
                    message = "BLE skeniranje trenutno nije dostupno.",
                    retryable = true,
                ),
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        stopScanningInternal(notifyStopped = true)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanningInternal(notifyStopped: Boolean) {
        val callback = activeCallback
        val eventCallback = activeEventCallback
        activeCallback = null
        activeEventCallback = null

        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (callback != null && scanner != null) {
            scanner.stopScan(callback)
        }

        if (notifyStopped && callback != null) {
            eventCallback?.invoke(BeaconScanEvent.Stopped)
        }
    }

    private fun handleScanResult(
        result: ScanResult,
        onEvent: (BeaconScanEvent) -> Unit,
    ) {
        val payload = result.scanRecord
            ?.getManufacturerSpecificData(BeaconProtocol.MANUFACTURER_ID)
            ?: return

        when (val decodedResult = BeaconPayloadCodec.decode(payload)) {
            is PayloadDecodeResult.Success -> {
                onEvent(
                    BeaconScanEvent.BeaconDetected(
                        payload = decodedResult.payload,
                        rssi = result.rssi,
                        detectedAt = System.currentTimeMillis(),
                    ),
                )
            }

            is PayloadDecodeResult.Invalid -> {
                AppLogger.d(
                    LogTag.BLE_SCAN,
                    "Ignored invalid beacon payload: ${decodedResult.reason}",
                )
            }
        }
    }

    private fun mapScanFailure(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "BLE skeniranje je vec aktivno."
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> {
                "Uredaj ne podrzava BLE skeniranje."
            }

            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Doslo je do interne BLE greske."
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> {
                "BLE skeniranje nije moglo da se registruje."
            }

            else -> "BLE skeniranje nije uspelo. Kod greske: $errorCode"
        }
    }

    private fun isRetryable(errorCode: Int): Boolean {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR,
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> true

            else -> false
        }
    }
}

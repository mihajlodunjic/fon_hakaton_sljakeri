package rs.fon.hakaton.audionav.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.BeaconConfigValidator

class AndroidBeaconAdvertiserController(
    context: Context,
) : BeaconAdvertiserController {

    private val bluetoothManager = context.applicationContext.getSystemService(BluetoothManager::class.java)
    private var activeCallback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    override fun isSupported(): Boolean {
        val adapter = bluetoothManager?.adapter ?: return false
        if (!adapter.isMultipleAdvertisementSupported) {
            return false
        }

        return adapter.bluetoothLeAdvertiser != null
    }

    @SuppressLint("MissingPermission")
    override fun startAdvertising(
        config: BeaconConfig,
        onResult: (BeaconAdvertiseResult) -> Unit,
    ) {
        if (!BeaconConfigValidator.isValid(config)) {
            onResult(BeaconAdvertiseResult.Failure(-1, "Beacon konfiguracija nije validna."))
            return
        }

        val adapter = bluetoothManager?.adapter
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (adapter == null || advertiser == null || !adapter.isMultipleAdvertisementSupported) {
            onResult(
                BeaconAdvertiseResult.Failure(
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                    mapAdvertiseFailure(AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED),
                ),
            )
            return
        }

        activeCallback?.let { advertiser.stopAdvertising(it) }

        val payload = BeaconPayloadCodec.encode(config)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(BeaconProtocol.MANUFACTURER_ID, payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                activeCallback = this
                onResult(BeaconAdvertiseResult.Started)
            }

            override fun onStartFailure(errorCode: Int) {
                activeCallback = null
                onResult(BeaconAdvertiseResult.Failure(errorCode, mapAdvertiseFailure(errorCode)))
            }
        }

        advertiser.startAdvertising(settings, advertiseData, callback)
    }

    @SuppressLint("MissingPermission")
    override fun stopAdvertising() {
        val adapter = bluetoothManager?.adapter ?: return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val callback = activeCallback ?: return
        advertiser.stopAdvertising(callback)
        activeCallback = null
    }

    private fun mapAdvertiseFailure(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> {
                "Advertising je vec aktivan."
            }

            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> {
                "BLE payload je prevelik za advertising."
            }

            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> {
                "Uredaj ne podrzava BLE advertising."
            }

            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> {
                "Doslo je do interne BLE greske."
            }

            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> {
                "Previse BLE advertiser sesija je aktivno."
            }

            else -> "Advertising nije uspeo. Kod greske: $errorCode"
        }
    }
}

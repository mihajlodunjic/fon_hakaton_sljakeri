package rs.fon.hakaton.audionav.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import rs.fon.hakaton.audionav.ble.AndroidBeaconAdvertiserController
import rs.fon.hakaton.audionav.ble.AndroidBeaconScannerController
import rs.fon.hakaton.audionav.domain.AndroidHeadingSensorController
import rs.fon.hakaton.audionav.domain.RssiStabilizer
import rs.fon.hakaton.audionav.storage.CooldownRepository
import rs.fon.hakaton.audionav.storage.DataStoreBeaconConfigStorage
import rs.fon.hakaton.audionav.storage.DataStoreReceiverRuntimeStorage
import rs.fon.hakaton.audionav.tts.AndroidTtsAnnouncer

class AppViewModelFactory(
    private val appContext: Context,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        return AppViewModel(
            beaconConfigStorage = DataStoreBeaconConfigStorage.fromContext(appContext),
            beaconAdvertiserController = AndroidBeaconAdvertiserController(appContext),
            beaconScannerController = AndroidBeaconScannerController(appContext),
            cooldownRepository = CooldownRepository(
                receiverRuntimeStorage = DataStoreReceiverRuntimeStorage.fromContext(appContext),
            ),
            rssiStabilizer = RssiStabilizer(),
            headingSensorController = AndroidHeadingSensorController(appContext),
            ttsAnnouncer = AndroidTtsAnnouncer(appContext),
        ) as T
    }
}

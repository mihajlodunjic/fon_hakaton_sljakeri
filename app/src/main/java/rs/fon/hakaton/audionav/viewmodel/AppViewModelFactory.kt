package rs.fon.hakaton.audionav.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import rs.fon.hakaton.audionav.ble.AndroidBeaconAdvertiserController
import rs.fon.hakaton.audionav.ble.AndroidBeaconScannerController
import rs.fon.hakaton.audionav.storage.DataStoreBeaconConfigStorage

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
        ) as T
    }
}

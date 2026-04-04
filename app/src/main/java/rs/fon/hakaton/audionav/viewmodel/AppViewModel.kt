package rs.fon.hakaton.audionav.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rs.fon.hakaton.audionav.AppLogger
import rs.fon.hakaton.audionav.LogTag
import rs.fon.hakaton.audionav.domain.AppMode
import rs.fon.hakaton.audionav.domain.AppUiState
import rs.fon.hakaton.audionav.domain.BluetoothStatus
import rs.fon.hakaton.audionav.domain.PermissionStatus
import rs.fon.hakaton.audionav.domain.PermissionUiState

class AppViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun onModeSelected(mode: AppMode) {
        AppLogger.d(LogTag.APP, "Mode selected: $mode")
        _uiState.update { currentState ->
            currentState.copy(selectedMode = mode)
        }
    }

    fun onRefreshPermissionStateRequested() {
        AppLogger.d(LogTag.APP, "Permission state refresh requested")
    }

    fun onSystemStatusChanged(
        permissionUiState: PermissionUiState,
        bluetoothStatus: BluetoothStatus,
    ) {
        AppLogger.d(
            LogTag.APP,
            "System status changed: permission=${permissionUiState.status}, bluetooth=$bluetoothStatus",
        )

        val readinessMessage = when {
            bluetoothStatus == BluetoothStatus.UNAVAILABLE -> "BLE nije dostupan na ovom uređaju."
            permissionUiState.status == PermissionStatus.PERMANENTLY_DENIED -> {
                "Dozvole su trajno odbijene. Otvorite podešavanja aplikacije."
            }

            permissionUiState.status == PermissionStatus.MISSING -> {
                "Nedostaju potrebne Bluetooth dozvole."
            }

            bluetoothStatus == BluetoothStatus.DISABLED -> "Bluetooth je isključen."
            else -> "Uređaj je spreman za sledeću fazu implementacije."
        }

        val interactionReady = permissionUiState.status == PermissionStatus.GRANTED &&
            bluetoothStatus == BluetoothStatus.READY

        _uiState.update { currentState ->
            currentState.copy(
                permissionUiState = permissionUiState,
                bluetoothStatus = bluetoothStatus,
                readinessMessage = readinessMessage,
                beaconState = currentState.beaconState.copy(isReady = interactionReady),
                receiverState = currentState.receiverState.copy(isReady = interactionReady),
            )
        }
    }

    fun onStartBeaconClick() {
        AppLogger.d(LogTag.BLE_ADV, "Beacon placeholder start clicked")
        _uiState.update { currentState ->
            val ready = currentState.beaconState.isReady
            currentState.copy(
                selectedMode = AppMode.BEACON,
                beaconState = currentState.beaconState.copy(
                    statusText = if (ready) {
                        "Beacon placeholder aktiviran"
                    } else {
                        "Beacon nije spreman. Proverite dozvole i Bluetooth."
                    },
                ),
            )
        }
    }

    fun onStartReceiverClick() {
        AppLogger.d(LogTag.BLE_SCAN, "Receiver placeholder start clicked")
        _uiState.update { currentState ->
            val ready = currentState.receiverState.isReady
            currentState.copy(
                selectedMode = AppMode.RECEIVER,
                receiverState = currentState.receiverState.copy(
                    isScanning = ready,
                    statusText = if (ready) {
                        "Receiver placeholder scan aktiviran"
                    } else {
                        "Receiver nije spreman. Proverite dozvole i Bluetooth."
                    },
                ),
            )
        }
    }

    fun onStopClick(mode: AppMode) {
        AppLogger.d(LogTag.APP, "Stop placeholder clicked for $mode")
        _uiState.update { currentState ->
            when (mode) {
                AppMode.BEACON -> currentState.copy(
                    beaconState = currentState.beaconState.copy(statusText = "Idle"),
                )

                AppMode.RECEIVER -> currentState.copy(
                    receiverState = currentState.receiverState.copy(
                        isScanning = false,
                        statusText = "Not Scanning",
                    ),
                )

                AppMode.NONE -> currentState
            }
        }
    }
}

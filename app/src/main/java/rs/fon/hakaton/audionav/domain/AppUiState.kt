package rs.fon.hakaton.audionav.domain

enum class AppMode {
    NONE,
    BEACON,
    RECEIVER,
}

enum class PermissionStatus {
    GRANTED,
    MISSING,
    PERMANENTLY_DENIED,
}

enum class BluetoothStatus {
    READY,
    DISABLED,
    UNAVAILABLE,
    UNKNOWN,
}

data class PermissionUiState(
    val status: PermissionStatus = PermissionStatus.MISSING,
    val missingPermissions: List<String> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
)

data class BeaconScreenState(
    val isReady: Boolean = false,
    val statusText: String = "Idle",
)

data class ReceiverScreenState(
    val isReady: Boolean = false,
    val isScanning: Boolean = false,
    val statusText: String = "Not Scanning",
)

data class AppUiState(
    val selectedMode: AppMode = AppMode.NONE,
    val permissionUiState: PermissionUiState = PermissionUiState(),
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.UNKNOWN,
    val readinessMessage: String = "Proverite dozvole i Bluetooth status.",
    val beaconState: BeaconScreenState = BeaconScreenState(),
    val receiverState: ReceiverScreenState = ReceiverScreenState(),
)

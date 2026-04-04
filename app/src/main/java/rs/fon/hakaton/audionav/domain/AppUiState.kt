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

private val defaultPointType = PointType.CROSSWALK
private val defaultMessage = MessageCatalog
    .definitionsFor(defaultPointType)
    .first()

data class BeaconScreenState(
    val beaconId: String = "",
    val labelInput: String = "",
    val selectedPointType: PointType = defaultPointType,
    val selectedPriority: Priority = Priority.MEDIUM,
    val selectedMessageCode: Short = defaultMessage.messageCode,
    val availableMessages: List<MessageDefinition> = MessageCatalog.definitionsFor(defaultPointType),
    val isReady: Boolean = false,
    val isAdvertising: Boolean = false,
    val statusText: String = "Idle",
    val errorText: String? = null,
    val advertiserSupported: Boolean = false,
    val lastEncodedPayloadHex: String? = null,
)

data class ReceiverScreenState(
    val scannerSupported: Boolean = false,
    val isReady: Boolean = false,
    val isScanning: Boolean = false,
    val retryScheduled: Boolean = false,
    val statusText: String = "Not Scanning",
    val errorText: String? = null,
    val lastDetectedBeaconId: String? = null,
    val lastDetectedPointType: PointType? = null,
    val lastDetectedPriority: Priority? = null,
    val lastDetectedMessageCode: Short? = null,
    val lastDecodedText: String? = null,
    val lastDetectedAt: Long? = null,
    val lastRssi: Int? = null,
    val stabilizationProgress: Int = 0,
    val requiredStabilizationCount: Int = 3,
    val rssiThreshold: Int = -75,
    val lastGateDecisionText: String? = null,
    val lastEligibleForAnnouncement: Boolean? = null,
    val lastAnnouncementAt: Long? = null,
    val recentEvents: List<DetectedBeaconEvent> = emptyList(),
)

data class AppUiState(
    val selectedMode: AppMode = AppMode.NONE,
    val permissionUiState: PermissionUiState = PermissionUiState(),
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.UNKNOWN,
    val readinessMessage: String = "Proverite dozvole i Bluetooth status.",
    val beaconState: BeaconScreenState = BeaconScreenState(),
    val receiverState: ReceiverScreenState = ReceiverScreenState(),
)

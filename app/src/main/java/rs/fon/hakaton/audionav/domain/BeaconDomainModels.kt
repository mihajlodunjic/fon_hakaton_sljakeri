package rs.fon.hakaton.audionav.domain

enum class PointType(
    val code: Int,
    val displayName: String,
) {
    CROSSWALK(1, "Pe\u0161a\u010dki prelaz"),
    TRAFFIC_LIGHT(2, "Semafor"),
    STAIRS(3, "Stepenice"),
    ENTRANCE(4, "Ulaz"),
    BUS_STOP(5, "Autobusko stajali\u0161te"),
    POLE(6, "Stub"),
    ELEVATOR(7, "Lift"),
    WORKS(8, "Radovi"),
    COUNTER(9, "\u0160alter"),
    DOOR(10, "Vrata"),
    OBSTACLE(11, "Prepreka"),
    OTHER(12, "Ostalo");

    companion object {
        fun fromCode(code: Int): PointType? = entries.firstOrNull { it.code == code }
    }
}

enum class Priority(
    val code: Int,
    val displayName: String,
) {
    LOW(1, "Nizak"),
    MEDIUM(2, "Srednji"),
    HIGH(3, "Visok"),
    CRITICAL(4, "Kriti\u010dan");

    companion object {
        fun fromCode(code: Int): Priority? = entries.firstOrNull { it.code == code }
    }
}

data class BeaconConfig(
    val beaconId: String,
    val label: String,
    val pointType: PointType,
    val priority: Priority,
    val messageCode: Short,
    val azimuthDegrees: Int,
    val isActive: Boolean,
    val lastUpdatedAt: Long,
)

data class DetectedBeaconEvent(
    val beaconId: String,
    val detectedAt: Long,
    val rssi: Int,
    val pointType: PointType,
    val priority: Priority,
    val messageCode: Short,
    val wasAnnounced: Boolean,
)

data class ReceiverState(
    val scanningEnabled: Boolean,
    val lastDetectedBeaconId: String?,
    val lastDetectedMessageCode: Short?,
    val lastAnnouncementAt: Long?,
    val lastDetectedAt: Long?,
)

data class CooldownEntry(
    val beaconId: String,
    val messageCode: Short,
    val lastTriggeredAt: Long,
)

data class MessageDefinition(
    val messageCode: Short,
    val pointType: PointType,
    val operatorLabel: String,
    val subjectSingular: String,
    val subjectPlural: Boolean,
    val genericTtsText: String,
    val directionPromptStyle: DirectionPromptStyle,
    val behindSpeechPolicy: BehindSpeechPolicy,
)

enum class DirectionPromptStyle {
    DEFAULT,
    CROSSWALK,
    TRAFFIC_LIGHT,
}

enum class BehindSpeechPolicy {
    IMMEDIATE_DIRECTIONAL,
    PASS_CONFIRMED_MESSAGE,
}

data class DecodedBeaconPayload(
    val protocolVersion: Int,
    val beaconId: String,
    val pointType: PointType,
    val priority: Priority,
    val messageCode: Short,
    val azimuthDegrees: Int?,
)

enum class BeaconConfigValidationError {
    INVALID_BEACON_ID,
    NON_POSITIVE_MESSAGE_CODE,
    MESSAGE_NOT_DEFINED_FOR_POINT_TYPE,
    INVALID_AZIMUTH,
}

enum class DirectionLabel {
    AHEAD,
    LEFT,
    RIGHT,
    BEHIND,
    UNKNOWN,
}

enum class DirectionConfidence {
    HIGH,
    LOW,
}

data class DirectionEstimate(
    val direction: DirectionLabel,
    val relativeAngleDegrees: Int,
    val confidence: DirectionConfidence,
)

data class HeadingEstimate(
    val headingDegrees: Int,
    val confidence: DirectionConfidence,
    val sampleCount: Int,
)

fun DirectionLabel.toDisplayText(): String {
    return when (this) {
        DirectionLabel.AHEAD -> "Ispred"
        DirectionLabel.LEFT -> "Levo"
        DirectionLabel.RIGHT -> "Desno"
        DirectionLabel.BEHIND -> "Iza"
        DirectionLabel.UNKNOWN -> "Nepoznat"
    }
}

fun DirectionConfidence.toDisplayText(): String {
    return when (this) {
        DirectionConfidence.HIGH -> "Visoka"
        DirectionConfidence.LOW -> "Niska"
    }
}

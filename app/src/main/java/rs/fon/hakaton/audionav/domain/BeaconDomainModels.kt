package rs.fon.hakaton.audionav.domain

enum class PointType(
    val code: Int,
    val displayName: String,
) {
    CROSSWALK(1, "Pesacki prelaz"),
    TRAFFIC_LIGHT(2, "Semafor"),
    STAIRS(3, "Stepenice"),
    ENTRANCE(4, "Ulaz"),
    BUS_STOP(5, "Autobusko stajaliste"),
    POLE(6, "Stub"),
    ELEVATOR(7, "Lift"),
    WORKS(8, "Radovi"),
    COUNTER(9, "Salter"),
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
    CRITICAL(4, "Kritican");

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
    val ttsText: String,
)

data class DecodedBeaconPayload(
    val protocolVersion: Int,
    val beaconId: String,
    val pointType: PointType,
    val priority: Priority,
    val messageCode: Short,
)

enum class BeaconConfigValidationError {
    INVALID_BEACON_ID,
    NON_POSITIVE_MESSAGE_CODE,
    MESSAGE_NOT_DEFINED_FOR_POINT_TYPE,
}

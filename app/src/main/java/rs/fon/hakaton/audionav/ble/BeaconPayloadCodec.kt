package rs.fon.hakaton.audionav.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.BeaconConfigValidator
import rs.fon.hakaton.audionav.domain.DecodedBeaconPayload
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

object BeaconProtocol {
    const val PROTOCOL_VERSION: Byte = 1
    const val MANUFACTURER_ID: Int = 0x13A7
    const val PAYLOAD_LENGTH: Int = 21
}

sealed interface PayloadDecodeResult {
    data class Success(val payload: DecodedBeaconPayload) : PayloadDecodeResult
    data class Invalid(val reason: InvalidPayloadReason) : PayloadDecodeResult
}

enum class InvalidPayloadReason {
    WRONG_LENGTH,
    UNSUPPORTED_PROTOCOL_VERSION,
    INVALID_UUID,
    UNKNOWN_POINT_TYPE,
    UNKNOWN_PRIORITY,
}

object BeaconPayloadCodec {

    fun encode(config: BeaconConfig): ByteArray {
        require(BeaconConfigValidator.isValid(config)) {
            "BeaconConfig is invalid and cannot be encoded."
        }

        val uuid = UUID.fromString(config.beaconId)
        return ByteBuffer
            .allocate(BeaconProtocol.PAYLOAD_LENGTH)
            .order(ByteOrder.BIG_ENDIAN)
            .put(BeaconProtocol.PROTOCOL_VERSION)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .put(config.pointType.code.toByte())
            .put(config.priority.code.toByte())
            .putShort(config.messageCode)
            .array()
    }

    fun decode(payload: ByteArray): PayloadDecodeResult {
        if (payload.size != BeaconProtocol.PAYLOAD_LENGTH) {
            return PayloadDecodeResult.Invalid(InvalidPayloadReason.WRONG_LENGTH)
        }

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val protocolVersion = buffer.get()
        if (protocolVersion != BeaconProtocol.PROTOCOL_VERSION) {
            return PayloadDecodeResult.Invalid(InvalidPayloadReason.UNSUPPORTED_PROTOCOL_VERSION)
        }

        val beaconId = decodeUuid(buffer) ?: return PayloadDecodeResult.Invalid(
            InvalidPayloadReason.INVALID_UUID,
        )

        val pointType = PointType.fromCode(buffer.get().toInt() and 0xFF)
            ?: return PayloadDecodeResult.Invalid(InvalidPayloadReason.UNKNOWN_POINT_TYPE)

        val priority = Priority.fromCode(buffer.get().toInt() and 0xFF)
            ?: return PayloadDecodeResult.Invalid(InvalidPayloadReason.UNKNOWN_PRIORITY)

        val messageCode = buffer.short

        return PayloadDecodeResult.Success(
            payload = DecodedBeaconPayload(
                protocolVersion = protocolVersion.toInt(),
                beaconId = beaconId,
                pointType = pointType,
                priority = priority,
                messageCode = messageCode,
            ),
        )
    }

    private fun decodeUuid(buffer: ByteBuffer): String? {
        return runCatching {
            val mostSignificantBits = buffer.long
            val leastSignificantBits = buffer.long
            UUID(mostSignificantBits, leastSignificantBits).toString()
        }.getOrNull()
    }
}

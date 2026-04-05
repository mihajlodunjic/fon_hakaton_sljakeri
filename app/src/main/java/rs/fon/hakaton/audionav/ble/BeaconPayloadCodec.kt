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
    const val PROTOCOL_VERSION_V1: Byte = 1
    const val PROTOCOL_VERSION_V2: Byte = 2
    const val PROTOCOL_VERSION: Byte = PROTOCOL_VERSION_V2
    const val MANUFACTURER_ID: Int = 0x13A7
    const val V1_PAYLOAD_LENGTH: Int = 21
    const val V2_PAYLOAD_LENGTH: Int = 23
    const val PAYLOAD_LENGTH: Int = V2_PAYLOAD_LENGTH
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
    INVALID_AZIMUTH,
}

object BeaconPayloadCodec {

    fun encode(config: BeaconConfig): ByteArray {
        require(BeaconConfigValidator.isValid(config)) {
            "BeaconConfig is invalid and cannot be encoded."
        }

        val uuid = UUID.fromString(config.beaconId)
        val protocolVersion = if (config.azimuthDegrees != null) {
            BeaconProtocol.PROTOCOL_VERSION_V2
        } else {
            BeaconProtocol.PROTOCOL_VERSION_V1
        }
        val buffer = ByteBuffer
            .allocate(
                if (protocolVersion == BeaconProtocol.PROTOCOL_VERSION_V2) {
                    BeaconProtocol.V2_PAYLOAD_LENGTH
                } else {
                    BeaconProtocol.V1_PAYLOAD_LENGTH
                },
            )
            .order(ByteOrder.BIG_ENDIAN)
            .put(protocolVersion)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .put(config.pointType.code.toByte())
            .put(config.priority.code.toByte())
            .putShort(config.messageCode)

        if (protocolVersion == BeaconProtocol.PROTOCOL_VERSION_V2) {
            buffer.putShort(config.azimuthDegrees!!.toShort())
        }

        return buffer.array()
    }

    fun decode(payload: ByteArray): PayloadDecodeResult {
        if (payload.size != BeaconProtocol.V1_PAYLOAD_LENGTH &&
            payload.size != BeaconProtocol.V2_PAYLOAD_LENGTH
        ) {
            return PayloadDecodeResult.Invalid(InvalidPayloadReason.WRONG_LENGTH)
        }

        val protocolVersion = payload.first()
        val expectedLength = when (protocolVersion) {
            BeaconProtocol.PROTOCOL_VERSION_V1 -> BeaconProtocol.V1_PAYLOAD_LENGTH
            BeaconProtocol.PROTOCOL_VERSION_V2 -> BeaconProtocol.V2_PAYLOAD_LENGTH
            else -> return PayloadDecodeResult.Invalid(InvalidPayloadReason.UNSUPPORTED_PROTOCOL_VERSION)
        }
        if (payload.size != expectedLength) {
            return PayloadDecodeResult.Invalid(InvalidPayloadReason.WRONG_LENGTH)
        }

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buffer.get()
        val beaconId = decodeUuid(buffer) ?: return PayloadDecodeResult.Invalid(
            InvalidPayloadReason.INVALID_UUID,
        )

        val pointType = PointType.fromCode(buffer.get().toInt() and 0xFF)
            ?: return PayloadDecodeResult.Invalid(InvalidPayloadReason.UNKNOWN_POINT_TYPE)

        val priority = Priority.fromCode(buffer.get().toInt() and 0xFF)
            ?: return PayloadDecodeResult.Invalid(InvalidPayloadReason.UNKNOWN_PRIORITY)

        val messageCode = buffer.short
        val azimuthDegrees = if (protocolVersion == BeaconProtocol.PROTOCOL_VERSION_V2) {
            buffer.short.toInt() and 0xFFFF
        } else {
            null
        }
        if (azimuthDegrees != null && azimuthDegrees !in 0..359) {
            return PayloadDecodeResult.Invalid(InvalidPayloadReason.INVALID_AZIMUTH)
        }

        return PayloadDecodeResult.Success(
            payload = DecodedBeaconPayload(
                protocolVersion = protocolVersion.toInt(),
                beaconId = beaconId,
                pointType = pointType,
                priority = priority,
                messageCode = messageCode,
                azimuthDegrees = azimuthDegrees,
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

package rs.fon.hakaton.audionav.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.fon.hakaton.audionav.domain.BeaconConfig
import rs.fon.hakaton.audionav.domain.PointType
import rs.fon.hakaton.audionav.domain.Priority

class BeaconPayloadCodecTest {

    @Test
    fun `encode and decode round-trip preserves beacon payload values`() {
        val config = validConfig()

        val encoded = BeaconPayloadCodec.encode(config)
        val decoded = BeaconPayloadCodec.decode(encoded)

        assertTrue(decoded is PayloadDecodeResult.Success)
        val payload = (decoded as PayloadDecodeResult.Success).payload
        assertEquals(BeaconProtocol.PROTOCOL_VERSION.toInt(), payload.protocolVersion)
        assertEquals(config.beaconId, payload.beaconId)
        assertEquals(config.pointType, payload.pointType)
        assertEquals(config.priority, payload.priority)
        assertEquals(config.messageCode, payload.messageCode)
    }

    @Test
    fun `encode stores UUID as 16 raw bytes and decode restores same canonical UUID`() {
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val config = validConfig(beaconId = uuid.toString())

        val encoded = BeaconPayloadCodec.encode(config)
        val uuidBytes = encoded.copyOfRange(1, 17)
        val buffer = ByteBuffer.wrap(uuidBytes).order(ByteOrder.BIG_ENDIAN)
        val reconstructed = UUID(buffer.long, buffer.long)

        assertEquals(uuid, reconstructed)

        val decoded = BeaconPayloadCodec.decode(encoded) as PayloadDecodeResult.Success
        assertEquals(uuid.toString(), decoded.payload.beaconId)
    }

    @Test
    fun `decode returns wrong length for non 21 byte payloads`() {
        val result = BeaconPayloadCodec.decode(ByteArray(20))

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.WRONG_LENGTH),
            result,
        )
    }

    @Test
    fun `decode returns unsupported protocol version for incompatible payload`() {
        val payload = BeaconPayloadCodec.encode(validConfig()).copyOf()
        payload[0] = 2

        val result = BeaconPayloadCodec.decode(payload)

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.UNSUPPORTED_PROTOCOL_VERSION),
            result,
        )
    }

    @Test
    fun `decode returns unknown point type for unsupported code`() {
        val payload = BeaconPayloadCodec.encode(validConfig()).copyOf()
        payload[17] = 99.toByte()

        val result = BeaconPayloadCodec.decode(payload)

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.UNKNOWN_POINT_TYPE),
            result,
        )
    }

    @Test
    fun `decode returns unknown priority for unsupported code`() {
        val payload = BeaconPayloadCodec.encode(validConfig()).copyOf()
        payload[18] = 99.toByte()

        val result = BeaconPayloadCodec.decode(payload)

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.UNKNOWN_PRIORITY),
            result,
        )
    }

    private fun validConfig(
        beaconId: String = "123e4567-e89b-12d3-a456-426614174000",
    ): BeaconConfig {
        return BeaconConfig(
            beaconId = beaconId,
            label = "Crosswalk A",
            pointType = PointType.CROSSWALK,
            priority = Priority.MEDIUM,
            messageCode = 1,
            isActive = false,
            lastUpdatedAt = 0L,
        )
    }
}


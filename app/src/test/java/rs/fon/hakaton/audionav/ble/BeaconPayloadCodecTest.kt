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
        assertEquals(config.azimuthDegrees, payload.azimuthDegrees)
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
    fun `decode returns wrong length for non 21 or 23 byte payloads`() {
        val result = BeaconPayloadCodec.decode(ByteArray(20))

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.WRONG_LENGTH),
            result,
        )
    }

    @Test
    fun `decode returns unsupported protocol version for incompatible payload`() {
        val payload = BeaconPayloadCodec.encode(validConfig()).copyOf()
        payload[0] = 99.toByte()

        val result = BeaconPayloadCodec.decode(payload)

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.UNSUPPORTED_PROTOCOL_VERSION),
            result,
        )
    }

    @Test
    fun `decode supports legacy v1 payload without azimuth`() {
        val legacyPayload = ByteBuffer
            .allocate(BeaconProtocol.V1_PAYLOAD_LENGTH)
            .order(ByteOrder.BIG_ENDIAN)
            .put(BeaconProtocol.PROTOCOL_VERSION_V1)
            .putLong(UUID.fromString("123e4567-e89b-12d3-a456-426614174000").mostSignificantBits)
            .putLong(UUID.fromString("123e4567-e89b-12d3-a456-426614174000").leastSignificantBits)
            .put(PointType.CROSSWALK.code.toByte())
            .put(Priority.MEDIUM.code.toByte())
            .putShort(1)
            .array()

        val result = BeaconPayloadCodec.decode(legacyPayload)

        assertTrue(result is PayloadDecodeResult.Success)
        val payload = (result as PayloadDecodeResult.Success).payload
        assertEquals(1, payload.protocolVersion)
        assertEquals(null, payload.azimuthDegrees)
    }

    @Test
    fun `encode without azimuth uses v1 payload and decodes with null azimuth`() {
        val config = validConfig(azimuthDegrees = null)

        val encoded = BeaconPayloadCodec.encode(config)
        val decoded = BeaconPayloadCodec.decode(encoded)

        assertEquals(BeaconProtocol.V1_PAYLOAD_LENGTH, encoded.size)
        assertEquals(BeaconProtocol.PROTOCOL_VERSION_V1, encoded.first())
        assertTrue(decoded is PayloadDecodeResult.Success)
        val payload = (decoded as PayloadDecodeResult.Success).payload
        assertEquals(1, payload.protocolVersion)
        assertEquals(null, payload.azimuthDegrees)
    }

    @Test
    fun `decode rejects invalid azimuth in v2 payload`() {
        val payload = BeaconPayloadCodec.encode(validConfig()).copyOf()
        payload[21] = 0x01
        payload[22] = 0x90.toByte()

        val result = BeaconPayloadCodec.decode(payload)

        assertEquals(
            PayloadDecodeResult.Invalid(InvalidPayloadReason.INVALID_AZIMUTH),
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
        azimuthDegrees: Int? = 90,
    ): BeaconConfig {
        return BeaconConfig(
            beaconId = beaconId,
            label = "Crosswalk A",
            pointType = PointType.CROSSWALK,
            priority = Priority.MEDIUM,
            messageCode = 1,
            azimuthDegrees = azimuthDegrees,
            isActive = false,
            lastUpdatedAt = 0L,
        )
    }
}

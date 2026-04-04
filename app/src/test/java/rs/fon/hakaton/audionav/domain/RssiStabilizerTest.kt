package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssiStabilizerTest {

    @Test
    fun `three consecutive reads above threshold produce stable result`() {
        val stabilizer = RssiStabilizer()
        val payload = payload(beaconId = "beacon-a")

        val first = stabilizer.observe(payload, rssi = -70, detectedAt = 100L)
        val second = stabilizer.observe(payload, rssi = -69, detectedAt = 200L)
        val third = stabilizer.observe(payload, rssi = -68, detectedAt = 300L)

        assertEquals(1, (first as RssiStabilizationResult.Tracking).progress)
        assertEquals(2, (second as RssiStabilizationResult.Tracking).progress)
        assertTrue(third is RssiStabilizationResult.Stable)
    }

    @Test
    fun `read below threshold resets tracking`() {
        val stabilizer = RssiStabilizer()
        val payload = payload(beaconId = "beacon-a")

        stabilizer.observe(payload, rssi = -70, detectedAt = 100L)
        val rejected = stabilizer.observe(payload, rssi = -80, detectedAt = 200L)
        val next = stabilizer.observe(payload, rssi = -70, detectedAt = 300L)

        assertEquals(
            RssiRejectionReason.BELOW_THRESHOLD,
            (rejected as RssiStabilizationResult.Rejected).reason,
        )
        assertEquals(1, (next as RssiStabilizationResult.Tracking).progress)
    }

    @Test
    fun `signal gap over two seconds resets tracking`() {
        val stabilizer = RssiStabilizer()
        val payload = payload(beaconId = "beacon-a")

        stabilizer.observe(payload, rssi = -70, detectedAt = 100L)
        stabilizer.observe(payload, rssi = -69, detectedAt = 200L)
        val rejected = stabilizer.observe(payload, rssi = -68, detectedAt = 2_500L)
        val next = stabilizer.observe(payload, rssi = -67, detectedAt = 2_600L)

        assertEquals(
            RssiRejectionReason.SIGNAL_GAP_RESET,
            (rejected as RssiStabilizationResult.Rejected).reason,
        )
        assertEquals(2, (next as RssiStabilizationResult.Tracking).progress)
    }

    @Test
    fun `different beacons are tracked independently`() {
        val stabilizer = RssiStabilizer()
        val beaconA = payload(beaconId = "beacon-a")
        val beaconB = payload(beaconId = "beacon-b")

        stabilizer.observe(beaconA, rssi = -70, detectedAt = 100L)
        stabilizer.observe(beaconB, rssi = -70, detectedAt = 150L)
        val secondA = stabilizer.observe(beaconA, rssi = -69, detectedAt = 200L)
        val secondB = stabilizer.observe(beaconB, rssi = -69, detectedAt = 250L)

        assertEquals(2, (secondA as RssiStabilizationResult.Tracking).progress)
        assertEquals(2, (secondB as RssiStabilizationResult.Tracking).progress)
    }

    private fun payload(beaconId: String): DecodedBeaconPayload {
        return DecodedBeaconPayload(
            protocolVersion = 1,
            beaconId = beaconId,
            pointType = PointType.CROSSWALK,
            priority = Priority.MEDIUM,
            messageCode = 1,
        )
    }
}

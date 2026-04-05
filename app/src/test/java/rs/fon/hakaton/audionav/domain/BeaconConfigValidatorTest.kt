package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeaconConfigValidatorTest {

    @Test
    fun `null azimuth is valid`() {
        val config = validConfig(azimuthDegrees = null)

        assertTrue(BeaconConfigValidator.isValid(config))
    }

    @Test
    fun `azimuth outside allowed range is invalid`() {
        val config = validConfig(azimuthDegrees = 400)

        assertFalse(BeaconConfigValidator.isValid(config))
    }

    private fun validConfig(azimuthDegrees: Int?): BeaconConfig {
        return BeaconConfig(
            beaconId = "123e4567-e89b-12d3-a456-426614174000",
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

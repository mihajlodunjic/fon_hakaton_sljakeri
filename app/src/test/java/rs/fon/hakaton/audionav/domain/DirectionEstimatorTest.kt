package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionEstimatorTest {

    @Test
    fun `zero delta maps to behind`() {
        val estimate = DirectionEstimator.estimate(90, 90, DirectionConfidence.HIGH)
        assertEquals(DirectionLabel.BEHIND, estimate.direction)
    }

    @Test
    fun `positive delta maps to right`() {
        val estimate = DirectionEstimator.estimate(90, 0, DirectionConfidence.HIGH)
        assertEquals(DirectionLabel.RIGHT, estimate.direction)
        assertEquals(90, estimate.relativeAngleDegrees)
    }

    @Test
    fun `negative delta maps to left`() {
        val estimate = DirectionEstimator.estimate(270, 0, DirectionConfidence.HIGH)
        assertEquals(DirectionLabel.LEFT, estimate.direction)
        assertEquals(-90, estimate.relativeAngleDegrees)
    }

    @Test
    fun `large delta maps to ahead`() {
        val estimate = DirectionEstimator.estimate(180, 0, DirectionConfidence.HIGH)
        assertEquals(DirectionLabel.AHEAD, estimate.direction)
    }

    @Test
    fun `low confidence maps to unknown`() {
        val estimate = DirectionEstimator.estimate(90, 0, DirectionConfidence.LOW)
        assertEquals(DirectionLabel.UNKNOWN, estimate.direction)
    }
}

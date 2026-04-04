package rs.fon.hakaton.audionav.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HeadingEstimateCalculatorTest {

    private val calculator = HeadingEstimateCalculator()

    @Test
    fun `circular mean works around zero and 359 degrees`() {
        val estimate = calculator.calculate(
            headingSamples = listOf(
                HeadingSample(100L, 359f),
                HeadingSample(200L, 0f),
                HeadingSample(300L, 1f),
                HeadingSample(400L, 359f),
                HeadingSample(500L, 2f),
            ),
            gyroSamples = listOf(
                GyroSample(450L, 5f),
            ),
            accelSamples = listOf(
                AccelSample(450L, 0.4f),
            ),
            nowMs = 500L,
        )

        assertNotNull(estimate)
        assertEquals(DirectionConfidence.HIGH, estimate?.confidence)
        assertEquals(0, estimate?.headingDegrees)
    }

    @Test
    fun `confidence drops with high angular speed`() {
        val estimate = calculator.calculate(
            headingSamples = listOf(
                HeadingSample(100L, 80f),
                HeadingSample(200L, 82f),
                HeadingSample(300L, 84f),
                HeadingSample(400L, 85f),
                HeadingSample(500L, 87f),
            ),
            gyroSamples = listOf(
                GyroSample(450L, 60f),
            ),
            accelSamples = listOf(
                AccelSample(450L, 0.2f),
            ),
            nowMs = 500L,
        )

        assertEquals(DirectionConfidence.LOW, estimate?.confidence)
    }

    @Test
    fun `confidence drops with high acceleration deviation`() {
        val estimate = calculator.calculate(
            headingSamples = listOf(
                HeadingSample(100L, 80f),
                HeadingSample(200L, 82f),
                HeadingSample(300L, 84f),
                HeadingSample(400L, 85f),
                HeadingSample(500L, 87f),
            ),
            gyroSamples = listOf(
                GyroSample(450L, 5f),
            ),
            accelSamples = listOf(
                AccelSample(450L, 2.2f),
            ),
            nowMs = 500L,
        )

        assertEquals(DirectionConfidence.LOW, estimate?.confidence)
    }
}

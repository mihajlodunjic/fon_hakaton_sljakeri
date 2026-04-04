package rs.fon.hakaton.audionav.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

data class HeadingSample(
    val timestampMs: Long,
    val headingDegrees: Float,
)

data class GyroSample(
    val timestampMs: Long,
    val angularSpeedDegPerSec: Float,
)

data class AccelSample(
    val timestampMs: Long,
    val gravityDeviationMs2: Float,
)

class HeadingEstimateCalculator(
    private val headingWindowMs: Long = HEADING_WINDOW_MS,
    private val motionWindowMs: Long = MOTION_WINDOW_MS,
    private val minHeadingSamples: Int = MIN_HEADING_SAMPLES,
    private val maxCircularSpreadDegrees: Float = MAX_CIRCULAR_SPREAD_DEGREES,
    private val maxAngularSpeedDegPerSec: Float = MAX_ANGULAR_SPEED_DEG_PER_SEC,
    private val maxGravityDeviationMs2: Float = MAX_GRAVITY_DEVIATION_MS2,
) {

    fun calculate(
        headingSamples: List<HeadingSample>,
        gyroSamples: List<GyroSample>,
        accelSamples: List<AccelSample>,
        nowMs: Long,
    ): HeadingEstimate? {
        val recentHeadingSamples = headingSamples.filter { nowMs - it.timestampMs <= headingWindowMs }
        if (recentHeadingSamples.isEmpty()) {
            return null
        }

        val headingMean = circularMeanDegrees(recentHeadingSamples.map { it.headingDegrees.toDouble() })
        val circularSpread = recentHeadingSamples
            .map { angularDistanceDegrees(it.headingDegrees.toDouble(), headingMean) }
            .maxOrNull()
            ?: 180.0

        val recentGyroSamples = gyroSamples.filter { nowMs - it.timestampMs <= motionWindowMs }
        val recentAccelSamples = accelSamples.filter { nowMs - it.timestampMs <= motionWindowMs }

        val averageAngularSpeed = recentGyroSamples
            .map { it.angularSpeedDegPerSec.toDouble() }
            .average()
            .takeUnless { it.isNaN() }
            ?: 0.0
        val averageGravityDeviation = recentAccelSamples
            .map { it.gravityDeviationMs2.toDouble() }
            .average()
            .takeUnless { it.isNaN() }
            ?: 0.0

        val confidence = if (
            recentHeadingSamples.size >= minHeadingSamples &&
            circularSpread <= maxCircularSpreadDegrees &&
            averageAngularSpeed <= maxAngularSpeedDegPerSec &&
            averageGravityDeviation <= maxGravityDeviationMs2
        ) {
            DirectionConfidence.HIGH
        } else {
            DirectionConfidence.LOW
        }

        return HeadingEstimate(
            headingDegrees = headingMean.roundToIntDegrees(),
            confidence = confidence,
            sampleCount = recentHeadingSamples.size,
        )
    }

    private fun circularMeanDegrees(values: List<Double>): Double {
        val sinSum = values.sumOf { kotlin.math.sin(Math.toRadians(it)) }
        val cosSum = values.sumOf { kotlin.math.cos(Math.toRadians(it)) }
        var angle = Math.toDegrees(atan2(sinSum, cosSum))
        if (angle < 0) {
            angle += 360.0
        }
        return angle
    }

    private fun angularDistanceDegrees(left: Double, right: Double): Double {
        val difference = abs(((left - right + 540.0) % 360.0) - 180.0)
        return difference
    }

    companion object {
        const val HEADING_WINDOW_MS: Long = 1_000L
        const val MOTION_WINDOW_MS: Long = 500L
        const val MIN_HEADING_SAMPLES: Int = 5
        const val MAX_CIRCULAR_SPREAD_DEGREES: Float = 18f
        const val MAX_ANGULAR_SPEED_DEG_PER_SEC: Float = 35f
        const val MAX_GRAVITY_DEVIATION_MS2: Float = 1.5f
    }
}

private fun Double.roundToIntDegrees(): Int {
    val rounded = kotlin.math.round(this).toInt() % 360
    return if (rounded < 0) rounded + 360 else rounded
}

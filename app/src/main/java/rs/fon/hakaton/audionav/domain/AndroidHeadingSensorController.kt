package rs.fon.hakaton.audionav.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class AndroidHeadingSensorController(
    context: Context,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val calculator: HeadingEstimateCalculator = HeadingEstimateCalculator(),
) : HeadingSensorController {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val headingSamples = ArrayDeque<HeadingSample>()
    private val gyroSamples = ArrayDeque<GyroSample>()
    private val accelSamples = ArrayDeque<AccelSample>()

    @Volatile
    private var latestHeadingEstimate: HeadingEstimate? = null
    private var running: Boolean = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val timestampMs = event.timestamp / 1_000_000L
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuthRadians = orientation[0].toDouble()
                    var headingDegrees = Math.toDegrees(azimuthRadians)
                    if (headingDegrees < 0) {
                        headingDegrees += 360.0
                    }
                    headingSamples.addLast(
                        HeadingSample(
                            timestampMs = timestampMs,
                            headingDegrees = headingDegrees.toFloat(),
                        ),
                    )
                }

                Sensor.TYPE_GYROSCOPE -> {
                    val speedRadPerSec = sqrt(
                        event.values[0].toDouble().pow(2.0) +
                            event.values[1].toDouble().pow(2.0) +
                            event.values[2].toDouble().pow(2.0),
                    )
                    gyroSamples.addLast(
                        GyroSample(
                            timestampMs = timestampMs,
                            angularSpeedDegPerSec = Math.toDegrees(speedRadPerSec).toFloat(),
                        ),
                    )
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    val magnitude = sqrt(
                        event.values[0].toDouble().pow(2.0) +
                            event.values[1].toDouble().pow(2.0) +
                            event.values[2].toDouble().pow(2.0),
                    )
                    accelSamples.addLast(
                        AccelSample(
                            timestampMs = timestampMs,
                            gravityDeviationMs2 = abs(magnitude - SensorManager.GRAVITY_EARTH).toFloat(),
                        ),
                    )
                }
            }

            pruneOldSamples(currentTimeMs = timestampMs)
            latestHeadingEstimate = calculator.calculate(
                headingSamples = headingSamples.toList(),
                gyroSamples = gyroSamples.toList(),
                accelSamples = accelSamples.toList(),
                nowMs = timestampMs,
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun start() {
        if (running) {
            return
        }
        val manager = sensorManager ?: return
        val rotation = rotationVectorSensor ?: return

        manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        gyroscopeSensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometerSensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        running = true
    }

    override fun stop() {
        if (!running) {
            return
        }
        sensorManager?.unregisterListener(listener)
        running = false
        headingSamples.clear()
        gyroSamples.clear()
        accelSamples.clear()
        latestHeadingEstimate = null
    }

    override fun latestEstimate(): HeadingEstimate? {
        pruneOldSamples(currentTimeMs = timeProvider())
        latestHeadingEstimate = calculator.calculate(
            headingSamples = headingSamples.toList(),
            gyroSamples = gyroSamples.toList(),
            accelSamples = accelSamples.toList(),
            nowMs = timeProvider(),
        )
        return latestHeadingEstimate
    }

    private fun pruneOldSamples(currentTimeMs: Long) {
        while (headingSamples.isNotEmpty() && currentTimeMs - headingSamples.first().timestampMs > HeadingEstimateCalculator.HEADING_WINDOW_MS) {
            headingSamples.removeFirst()
        }
        while (gyroSamples.isNotEmpty() && currentTimeMs - gyroSamples.first().timestampMs > HeadingEstimateCalculator.MOTION_WINDOW_MS) {
            gyroSamples.removeFirst()
        }
        while (accelSamples.isNotEmpty() && currentTimeMs - accelSamples.first().timestampMs > HeadingEstimateCalculator.MOTION_WINDOW_MS) {
            accelSamples.removeFirst()
        }
    }
}

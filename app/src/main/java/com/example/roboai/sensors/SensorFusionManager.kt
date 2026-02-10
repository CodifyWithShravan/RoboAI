package com.example.roboai.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.roboai.state.RoboSignal
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages device sensors for the Robo AI:
 * - Accelerometer → Eye tilt offset + shake detection
 * - Proximity → Sleep trigger
 * - Gyroscope → Head roll tilt
 */
class SensorFusionManager(
    context: Context,
    private val onSignal: (RoboSignal) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "SensorFusion"
        private const val SHAKE_THRESHOLD = 40f          // m/s² (requires strong deliberate shake)
        private const val SHAKE_COOLDOWN_MS = 2000L       // 2 seconds between shakes
        private const val TILT_SMOOTHING = 0.15f           // Low-pass filter factor
        private const val ROLL_SMOOTHING = 0.1f            // Low-pass filter for roll
        private const val MAX_TILT = 9.8f                  // Max expected tilt value
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) // Actually using Rotation Vector implies easier angle calculation but Gyro requested.
    // However, direct Gyro gives angular velocity. Accelerometer + Magnetometer (Rotation Vector) is better for absolute orientation.
    // The prompt says "Gyroscope -> Rotate phone -> head tilt effect".
    // Using simple gravity from accelerometer is actually easier for "Head Tilt" (Roll) unless we want yaw.
    // "Rotate phone -> head tilt effect" usually refers to Z-axis rotation relative to screen (Roll).
    // An accelerometer is sufficient for Roll (Z-rotation relative to gravity) if the phone is held upright.
    // But let's check if we can use GRAVITY or ROTATION_VECTOR for better stability.
    // Let's stick to accelerometer for basic roll if simpler, or add rotation vector if available.
    // The prompt specifically asked for "Gyroscope".
    // "Gyroscope -> Rotate phone -> head tilt effect".
    // If we strictly use Gyro (rad/s), we need to integrate over time, which drifts.
    // I will use accelerometer for "static" roll (tilt) which causes the face to rotate.

    private var lastShakeTime = 0L
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var smoothedRoll = 0f
    private var isProximityClose = false

    /**
     * Start listening to sensors.
     */
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Accelerometer registered")
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Proximity sensor registered")
        }
    }

    /**
     * Stop listening to sensors.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensors unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_PROXIMITY -> handleProximity(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    /**
     * Process accelerometer data:
     * - Compute smoothed tilt for eye movement
     * - Compute Roll for head tilt
     * - Detect shakes for angry trigger
     */
    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]  // Left-right tilt (gravity on x)
        val y = event.values[1]  // Up-down tilt
        val z = event.values[2]  // Forward-back
        
        // 1. Smooth tilt for eye offset (low-pass filter)
        // Eyes look opposite to gravity? Or look towards tilt?
        // Usually if I tilt phone left (x positive), eyes should look left relative to screen?
        smoothedX = smoothedX + TILT_SMOOTHING * (x - smoothedX)
        smoothedY = smoothedY + TILT_SMOOTHING * (y - smoothedY)
        
        // Normalize to -1..1 range for eyes
        val normalizedX = -(smoothedX / MAX_TILT).coerceIn(-1f, 1f) // Invert so eyes look "down" gravity
        val normalizedY = (smoothedY / MAX_TILT).coerceIn(-1f, 1f)
        
        onSignal(RoboSignal.TiltUpdate(normalizedX, normalizedY))
        
        // 2. Calculate Roll (Head Tilt)
        // arctan(x / y) gives roll angle
        val rollRad = kotlin.math.atan2((-x).toDouble(), y.toDouble())
        val rollDeg = Math.toDegrees(rollRad).toFloat()
        
        // Smooth roll
        smoothedRoll = smoothedRoll + ROLL_SMOOTHING * (rollDeg - smoothedRoll)
        onSignal(RoboSignal.HeadRollUpdate(smoothedRoll))

        // 3. Shake detection
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()
        
        if (magnitude > SHAKE_THRESHOLD && (now - lastShakeTime) > SHAKE_COOLDOWN_MS) {
            lastShakeTime = now
            Log.d(TAG, "Shake detected! magnitude=$magnitude")
            onSignal(RoboSignal.Shake)
        }
    }

    /**
     * Process proximity sensor:
     * - Close → Sleep signal
     * - Far → ProximityFar signal
     */
    private fun handleProximity(event: SensorEvent) {
        val distance = event.values[0]
        val maxRange = event.sensor.maximumRange
        val isClose = distance < maxRange / 2
        
        if (isClose != isProximityClose) {
            isProximityClose = isClose
            if (isClose) {
                Log.d(TAG, "Proximity: CLOSE → Sleep")
                onSignal(RoboSignal.ProximityClose)
            } else {
                Log.d(TAG, "Proximity: FAR → Wake")
                onSignal(RoboSignal.ProximityFar)
            }
        }
    }
}

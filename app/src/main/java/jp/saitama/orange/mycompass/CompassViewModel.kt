package jp.saitama.orange.mycompass

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompassViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _sensorAvailable = MutableStateFlow(true)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable.asStateFlow()

    init {
        if (accelerometer == null || magnetometer == null) {
            _sensorAvailable.value = false
        }
    }

    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
                }
            }
            updateOrientation()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun updateOrientation() {
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        if (success) {
            // Remap coordinate system for portrait mode
            val remappedRotationMatrix = FloatArray(9)
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedRotationMatrix
            )

            SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)

            // Convert radians to degrees and normalize to 0-360
            val azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalizedAzimuth = (azimuthInDegrees + 360) % 360

            viewModelScope.launch {
                _azimuth.value = normalizedAzimuth
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
package jp.saitama.orange.mycompass

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DestinationInfo(
    val destination: Destination,
    val bearing: Float,
    val distance: Float
)

class CompassViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Rotation Vector sensor (preferred - uses sensor fusion with automatic noise reduction)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Fallback to old method if Rotation Vector not available
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val useRotationVector = rotationVectorSensor != null

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _sensorAvailable = MutableStateFlow(true)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _destinationInfoList = MutableStateFlow<List<DestinationInfo>>(emptyList())
    val destinationInfoList: StateFlow<List<DestinationInfo>> = _destinationInfoList.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                viewModelScope.launch {
                    _currentLocation.value = location
                }
            }
        }
    }

    init {
        // Check sensor availability
        if (useRotationVector) {
            if (rotationVectorSensor == null) {
                _sensorAvailable.value = false
            }
        } else {
            if (accelerometer == null || magnetometer == null) {
                _sensorAvailable.value = false
            }
        }
    }

    fun startListening() {
        if (useRotationVector) {
            // Use Rotation Vector sensor (preferred)
            rotationVectorSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            // Fallback to accelerometer + magnetometer
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        startLocationUpdates()
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun updateDestinations(destinations: List<Destination>) {
        viewModelScope.launch {
            val currentLoc = _currentLocation.value
            if (currentLoc != null) {
                val infoList = destinations.map { dest ->
                    val bearing = calculateBearing(
                        currentLoc.latitude,
                        currentLoc.longitude,
                        dest.latitude,
                        dest.longitude
                    )
                    val distance = calculateDistance(
                        currentLoc.latitude,
                        currentLoc.longitude,
                        dest.latitude,
                        dest.longitude
                    )
                    DestinationInfo(dest, bearing, distance)
                }
                _destinationInfoList.value = infoList
            } else {
                _destinationInfoList.value = emptyList()
            }
        }
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lonDiff = Math.toRadians(lon2 - lon1)

        val y = sin(lonDiff) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(lonDiff)
        val bearing = Math.toDegrees(atan2(y, x))

        return ((bearing + 360) % 360).toFloat()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return (earthRadius * c).toFloat()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (useRotationVector) {
                // Rotation Vector sensor (preferred method)
                if (it.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    updateOrientationFromRotationVector(it.values)
                }
            } else {
                // Fallback to accelerometer + magnetometer
                when (it.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    }
                }
                updateOrientationFromAccelMag()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun updateOrientationFromRotationVector(rotationVector: FloatArray) {
        // Convert rotation vector to rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

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

    private fun updateOrientationFromAccelMag() {
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
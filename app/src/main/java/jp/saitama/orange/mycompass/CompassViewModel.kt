package jp.saitama.orange.mycompass

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.GeomagneticField
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
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
    private var isListening = false
    private var isSensorRegistered = false
    private var wantsLocationUpdates = false
    private var isLocationUpdating = false

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _sensorAvailable = MutableStateFlow(true)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _destinationInfoList = MutableStateFlow<List<DestinationInfo>>(emptyList())
    val destinationInfoList: StateFlow<List<DestinationInfo>> = _destinationInfoList.asStateFlow()

    // Current geomagnetic declination in degrees (east-positive)
    private val _declinationDeg = MutableStateFlow(0f)
    val declinationDeg: StateFlow<Float> = _declinationDeg.asStateFlow()

    // Smoothing and face-down hysteresis
    private var lastAzimuth: Float? = null
    private var isFaceDown: Boolean = false
    private val FACE_DOWN_ON_THRESHOLD = -0.2f
    private val FACE_DOWN_OFF_THRESHOLD = 0.2f
    private val AZIMUTH_SMOOTH_ALPHA = 0.9f // higher = smoother, 0.85–0.95 recommended

    // Expose tilt (pitch/roll) for UI visualization (degrees)
    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()
    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    // Heading accuracy (±deg) when available from rotation-vector; otherwise null
    private val _headingAccuracyDeg = MutableStateFlow<Float?>(null)
    val headingAccuracyDeg: StateFlow<Float?> = _headingAccuracyDeg.asStateFlow()
    // Last reported sensor accuracy status (UNRELIABLE=0..HIGH=3)
    private val _sensorAccuracy = MutableStateFlow<Int?>(null)
    val sensorAccuracy: StateFlow<Int?> = _sensorAccuracy.asStateFlow()

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
        if (isListening) return
        isListening = true
        isSensorRegistered = false

        if (useRotationVector) {
            rotationVectorSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                isSensorRegistered = true
            }
        } else {
            var registered = false
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                registered = true
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                registered = true
            }
            isSensorRegistered = registered
        }

        if (wantsLocationUpdates) {
            startLocationUpdates()
        }
    }

    fun stopListening() {
        if (!isListening && !isLocationUpdating) {
            return
        }

        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        } else if (isListening) {
            sensorManager.unregisterListener(this)
        }

        isListening = false
        lastAzimuth = null
        stopLocationUpdates()
    }

    fun setLocationTrackingEnabled(shouldTrack: Boolean) {
        if (shouldTrack == wantsLocationUpdates) return
        wantsLocationUpdates = shouldTrack
        if (!isListening) {
            return
        }
        if (shouldTrack) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (!wantsLocationUpdates || isLocationUpdating) return
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // First, get last known location immediately
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        viewModelScope.launch {
                            _currentLocation.value = it
                        }
                    }
                }
            } catch (_: SecurityException) {}

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                3000L
            )
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(5f)
                .setWaitForAccurateLocation(false)
                .build()

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
                isLocationUpdating = true
            } catch (_: SecurityException) {
                isLocationUpdating = false
            }
        }
    }

    private fun stopLocationUpdates() {
        if (!isLocationUpdating) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdating = false
    }

    fun updateDestinations(destinations: List<Destination>) {
        viewModelScope.launch {
            val currentLoc = _currentLocation.value
            if (currentLoc != null) {
                // Compute geomagnetic declination (degrees) at current location/time
                val declinationDeg = try {
                    val field = GeomagneticField(
                        currentLoc.latitude.toFloat(),
                        currentLoc.longitude.toFloat(),
                        currentLoc.altitude.toFloat(),
                        System.currentTimeMillis()
                    )
                    field.declination
                } catch (e: Exception) {
                    0f
                }
                _declinationDeg.value = declinationDeg
                val infoList = destinations.map { dest ->
                    val trueBearing = calculateBearing(
                        currentLoc.latitude,
                        currentLoc.longitude,
                        dest.latitude,
                        dest.longitude
                    )
                    // Convert true-bearing to magnetic-bearing so it matches sensor azimuth
                    val bearing = ((trueBearing - declinationDeg + 360f) % 360f)
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
                    // Estimated heading accuracy (radians) may be provided in values[4]
                    if (it.values.size >= 5) {
                        val accRad = it.values[4]
                        val accDeg = if (accRad.isFinite() && accRad >= 0f) {
                            Math.toDegrees(accRad.toDouble()).toFloat().coerceAtLeast(0f)
                        } else {
                            null
                        }
                        viewModelScope.launch { _headingAccuracyDeg.value = accDeg }
                    } else {
                        viewModelScope.launch { _headingAccuracyDeg.value = null }
                    }
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
        viewModelScope.launch {
            _sensorAccuracy.value = accuracy
        }
    }

    private fun updateOrientationFromRotationVector(rotationVector: FloatArray) {
        // Get rotation matrix from rotation vector (already tilt-compensated)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        // Face-down hysteresis based on Z axis (device coords)
        val z = rotationMatrix[8]
        isFaceDown = if (isFaceDown) {
            if (z > FACE_DOWN_OFF_THRESHOLD) false else true
        } else {
            if (z < FACE_DOWN_ON_THRESHOLD) true else false
        }

        // Remap to screen coordinates based on current display rotation
        val outR = FloatArray(9)
        val (axisX, axisZ) = remapAxesForDisplay()
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            axisX,
            if (isFaceDown) SensorManager.AXIS_MINUS_Z else axisZ,
            outR
        )

        SensorManager.getOrientation(outR, orientationAngles)

        var azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        val normalizedAzimuth = (azimuthInDegrees + 360f) % 360f
        val smoothed = smoothAzimuth(normalizedAzimuth)

        viewModelScope.launch {
            _azimuth.value = smoothed
            _pitch.value = pitchDeg
            _roll.value = rollDeg
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
            // Face-down hysteresis based on Z axis (device coords)
            val z = rotationMatrix[8]
            isFaceDown = if (isFaceDown) {
                if (z > FACE_DOWN_OFF_THRESHOLD) false else true
            } else {
                if (z < FACE_DOWN_ON_THRESHOLD) true else false
            }

            // Remap to screen coordinates
            val outR = FloatArray(9)
            val (axisX, axisZ) = remapAxesForDisplay()
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                axisX,
                if (isFaceDown) SensorManager.AXIS_MINUS_Z else axisZ,
                outR
            )

            SensorManager.getOrientation(outR, orientationAngles)

            var azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

            val normalizedAzimuth = (azimuthInDegrees + 360f) % 360f
            val smoothed = smoothAzimuth(normalizedAzimuth)

            viewModelScope.launch {
                _azimuth.value = smoothed
                _pitch.value = pitchDeg
                _roll.value = rollDeg
            }
        }

    }

    private fun remapAxesForDisplay(): Pair<Int, Int> {
        val dm = getApplication<Application>().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        return when (display?.rotation) {
            Surface.ROTATION_0 -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Z)
            Surface.ROTATION_90 -> Pair(SensorManager.AXIS_Y, SensorManager.AXIS_Z)
            Surface.ROTATION_180 -> Pair(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_Z)
            Surface.ROTATION_270 -> Pair(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_Z)
            else -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Z)
        }
    }

    private fun smoothAzimuth(newAngle: Float): Float {
        val prev = lastAzimuth
        if (prev == null) {
            lastAzimuth = newAngle
            return newAngle
        }
        var delta = newAngle - prev
        // Wrap to [-180, 180)
        delta = ((delta + 540f) % 360f) - 180f
        val smoothed = (prev + (1 - AZIMUTH_SMOOTH_ALPHA) * delta + 360f) % 360f
        lastAzimuth = smoothed
        return smoothed
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}

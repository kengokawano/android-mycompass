package jp.saitama.orange.mycompass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)

    // Use True North for display (if false, use Magnetic North)
    private val _useTrueNorth = MutableStateFlow(false)
    val useTrueNorth: StateFlow<Boolean> = _useTrueNorth.asStateFlow()

    // Distance unit: "meter" or "mile"
    private val _distanceUnit = MutableStateFlow("meter")
    val distanceUnit: StateFlow<String> = _distanceUnit.asStateFlow()

    // Stride length in cm for step calculation
    private val _strideLengthCm = MutableStateFlow(70)
    val strideLengthCm: StateFlow<Int> = _strideLengthCm.asStateFlow()

    // Compass image type (0 for compass01, 1 for compass02)
    private val _compassType = MutableStateFlow(0)
    val compassType: StateFlow<Int> = _compassType.asStateFlow()

    init {
        viewModelScope.launch {
            // Load persisted values once on startup
            val settings = dataStore.settingsFlow.first()
            _useTrueNorth.value = settings.useTrueNorth
            _distanceUnit.value = settings.distanceUnit
            _strideLengthCm.value = settings.strideLengthCm
            _compassType.value = settings.compassType
        }
    }

    fun setUseTrueNorth(enabled: Boolean) {
        _useTrueNorth.value = enabled
        persist()
    }

    fun setDistanceUnit(unit: String) {
        _distanceUnit.value = unit
        persist()
    }

    fun setStrideLengthCm(lengthCm: Int) {
        _strideLengthCm.value = lengthCm
        persist()
    }

    fun setCompassType(type: Int) {
        _compassType.value = type
        persist()
    }

    private fun persist() {
        viewModelScope.launch {
            dataStore.saveSettings(
                useTrueNorth = _useTrueNorth.value,
                distanceUnit = _distanceUnit.value,
                strideLengthCm = _strideLengthCm.value,
                compassType = _compassType.value
            )
        }
    }
}

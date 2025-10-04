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

    init {
        viewModelScope.launch {
            // Load persisted values once on startup
            val settings = dataStore.settingsFlow.first()
            _useTrueNorth.value = settings.useTrueNorth
            _distanceUnit.value = settings.distanceUnit
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

    private fun persist() {
        viewModelScope.launch {
            dataStore.saveSettings(
                useTrueNorth = _useTrueNorth.value,
                distanceUnit = _distanceUnit.value
            )
        }
    }
}

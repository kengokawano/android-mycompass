package jp.saitama.orange.mycompass

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DestinationViewModel : ViewModel() {
    private val _destinations = MutableStateFlow<List<Destination>>(emptyList())
    val destinations: StateFlow<List<Destination>> = _destinations.asStateFlow()

    private var nextId = 1

    fun addDestination(name: String, latitude: Double, longitude: Double): Boolean {
        if (_destinations.value.size >= 3) {
            return false
        }

        val newDestination = Destination(
            id = nextId++,
            name = name,
            latitude = latitude,
            longitude = longitude
        )

        _destinations.value = _destinations.value + newDestination
        return true
    }

    fun removeDestination(id: Int) {
        _destinations.value = _destinations.value.filter { it.id != id }
    }

    fun updateDestination(id: Int, name: String, latitude: Double, longitude: Double) {
        _destinations.value = _destinations.value.map {
            if (it.id == id) {
                it.copy(name = name, latitude = latitude, longitude = longitude)
            } else {
                it
            }
        }
    }

    fun canAddMore(): Boolean {
        return _destinations.value.size < 3
    }
}
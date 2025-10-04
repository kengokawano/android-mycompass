package jp.saitama.orange.mycompass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DestinationViewModel(private val dataStore: DestinationDataStore) : ViewModel() {
    private val _destinations = MutableStateFlow<List<Destination>>(emptyList())
    val destinations: StateFlow<List<Destination>> = _destinations.asStateFlow()

    private var nextId = 1

    init {
        viewModelScope.launch {
            val savedDestinations = dataStore.destinationsFlow.first()
            _destinations.value = savedDestinations
            nextId = (savedDestinations.maxOfOrNull { it.id } ?: 0) + 1
        }
    }

    fun addDestination(name: String, latitude: Double, longitude: Double): Boolean {
        if (_destinations.value.size >= Destination.MAX_DESTINATIONS) {
            return false
        }

        val newDestination = Destination(
            id = nextId++,
            name = name,
            latitude = latitude,
            longitude = longitude
        )

        val updatedList = _destinations.value + newDestination
        _destinations.value = updatedList
        viewModelScope.launch {
            dataStore.saveDestinations(updatedList)
        }
        return true
    }

    fun removeDestination(id: Int) {
        val updatedList = _destinations.value.filter { it.id != id }
        _destinations.value = updatedList
        viewModelScope.launch {
            dataStore.saveDestinations(updatedList)
        }
    }

    fun updateDestination(id: Int, name: String, latitude: Double, longitude: Double) {
        val updatedList = _destinations.value.map {
            if (it.id == id) {
                it.copy(name = name, latitude = latitude, longitude = longitude)
            } else {
                it
            }
        }
        _destinations.value = updatedList
        viewModelScope.launch {
            dataStore.saveDestinations(updatedList)
        }
    }

    fun canAddMore(): Boolean {
        return _destinations.value.size < Destination.MAX_DESTINATIONS
    }
}
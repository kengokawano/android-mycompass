package jp.saitama.orange.mycompass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _arEnabled = MutableStateFlow(false)
    val arEnabled: StateFlow<Boolean> = _arEnabled.asStateFlow()

    fun setArEnabled(enabled: Boolean) {
        _arEnabled.value = enabled
    }
}

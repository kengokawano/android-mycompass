package jp.saitama.orange.mycompass

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val useTrueNorth: Boolean = false,
    val distanceUnit: String = "meter",
    val strideLengthCm: Int = 70,
    val compassType: Int = 0
)

class SettingsDataStore(context: Context) {

    private val dataStore = context.settingsDataStore

    private object Keys {
        val USE_TRUE_NORTH = booleanPreferencesKey("use_true_north")
        val DIST_UNIT = stringPreferencesKey("distance_unit")
        val STRIDE_LENGTH_CM = intPreferencesKey("stride_length_cm")
        val COMPASS_TYPE = intPreferencesKey("compass_type")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            AppSettings(
                useTrueNorth = prefs[Keys.USE_TRUE_NORTH] ?: false,
                distanceUnit = prefs[Keys.DIST_UNIT] ?: "meter",
                strideLengthCm = prefs[Keys.STRIDE_LENGTH_CM] ?: 70,
                compassType = prefs[Keys.COMPASS_TYPE] ?: 0
            )
        }

    suspend fun saveSettings(useTrueNorth: Boolean, distanceUnit: String, strideLengthCm: Int, compassType: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.USE_TRUE_NORTH] = useTrueNorth
            prefs[Keys.DIST_UNIT] = distanceUnit
            prefs[Keys.STRIDE_LENGTH_CM] = strideLengthCm
            prefs[Keys.COMPASS_TYPE] = compassType
        }
    }
}

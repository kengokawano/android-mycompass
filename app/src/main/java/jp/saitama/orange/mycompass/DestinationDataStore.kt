package jp.saitama.orange.mycompass

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "destinations")

class DestinationDataStore(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        private val DESTINATIONS_KEY = stringPreferencesKey("destinations_json")
    }

    val destinationsFlow: Flow<List<Destination>> = dataStore.data
        .map {
            val jsonString = it[DESTINATIONS_KEY]
            if (jsonString != null) {
                Json.decodeFromString<List<Destination>>(jsonString)
            } else {
                emptyList()
            }
        }

    suspend fun saveDestinations(destinations: List<Destination>) {
        val jsonString = Json.encodeToString(destinations)
        dataStore.edit {
            it[DESTINATIONS_KEY] = jsonString
        }
    }
}
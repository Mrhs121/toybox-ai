package com.toybox.llmchat.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toybox.llmchat.data.model.ApiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.configStore: DataStore<Preferences> by preferencesDataStore("api_configs")

class ConfigRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val configsKey = stringPreferencesKey("configs")

    val configs: Flow<List<ApiConfig>> = context.configStore.data.map { prefs ->
        val raw = prefs[configsKey] ?: "[]"
        json.decodeFromString<List<ApiConfig>>(raw)
    }

    suspend fun save(configs: List<ApiConfig>) {
        context.configStore.edit { prefs ->
            prefs[configsKey] = json.encodeToString(configs)
        }
    }

    suspend fun add(config: ApiConfig) {
        context.configStore.edit { prefs ->
            val list = json.decodeFromString<List<ApiConfig>>(prefs[configsKey] ?: "[]")
            prefs[configsKey] = json.encodeToString(list + config)
        }
    }

    suspend fun update(config: ApiConfig) {
        context.configStore.edit { prefs ->
            val list = json.decodeFromString<List<ApiConfig>>(prefs[configsKey] ?: "[]")
            prefs[configsKey] = json.encodeToString(list.map { if (it.id == config.id) config else it })
        }
    }

    suspend fun delete(id: String) {
        context.configStore.edit { prefs ->
            val list = json.decodeFromString<List<ApiConfig>>(prefs[configsKey] ?: "[]")
            prefs[configsKey] = json.encodeToString(list.filter { it.id != id })
        }
    }
}

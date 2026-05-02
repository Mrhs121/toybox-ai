package com.toybox.llmchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toybox.llmchat.data.model.ApiConfig
import com.toybox.llmchat.data.repository.ConfigRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ConfigRepository(application)

    val configs: StateFlow<List<ApiConfig>> = repo.configs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addConfig(name: String, baseUrl: String, apiKey: String, modelId: String, modelName: String) {
        viewModelScope.launch {
            repo.add(
                ApiConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    baseUrl = baseUrl.trimEnd('/'),
                    apiKey = apiKey,
                    modelId = modelId,
                    modelName = modelName
                )
            )
        }
    }

    fun updateConfig(config: ApiConfig) {
        viewModelScope.launch {
            repo.update(config.copy(baseUrl = config.baseUrl.trimEnd('/')))
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            repo.delete(id)
        }
    }
}

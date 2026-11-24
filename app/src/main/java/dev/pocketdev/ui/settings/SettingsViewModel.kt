package dev.pocketdev.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.settings.AgentSettings
import dev.pocketdev.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container

    val themeMode: StateFlow<ThemeMode> = container.themeMode
    val workspaceRoot: String = container.projectsRoot.absolutePath

    val agentSettings: StateFlow<AgentSettings> = container.agentSettings

    private val _agentSettingsError = MutableStateFlow<String?>(null)
    val agentSettingsError: StateFlow<String?> = _agentSettingsError.asStateFlow()

    val apiKeyConfigured: StateFlow<Boolean> = container.apiKeyConfigured

    private val _apiKeyError = MutableStateFlow<String?>(null)
    val apiKeyError: StateFlow<String?> = _apiKeyError.asStateFlow()

    private val _apiKeyDraft = MutableStateFlow("")
    val apiKeyDraft: StateFlow<String> = _apiKeyDraft.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = container.setThemeMode(mode)

    fun setAgentSettings(provider: String, model: String) {
        runCatching {
            container.setAgentSettings(AgentSettings(provider.trim(), model.trim()))
        }.onSuccess {
            _agentSettingsError.value = null
        }.onFailure {
            _agentSettingsError.value = it.message ?: "Invalid provider/model"
        }
    }

    fun setApiKeyDraft(value: String) {
        _apiKeyDraft.value = value
    }

    fun saveApiKey(value: String) {
        runCatching { container.saveApiKey(value) }
            .onSuccess {
                _apiKeyError.value = null
                _apiKeyDraft.value = ""
            }
            .onFailure {
                _apiKeyError.value = it.message ?: "Unable to save API key"
            }
    }

    fun clearApiKey() {
        runCatching { container.clearApiKey() }
            .onSuccess {
                _apiKeyError.value = null
                _apiKeyDraft.value = ""
            }
            .onFailure {
                _apiKeyError.value = it.message ?: "Unable to clear API key"
            }
    }

    fun clearProjects() {
        container.projectStore.replaceAll(emptyList())
    }
}
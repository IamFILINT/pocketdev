package dev.pocketdev.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container

    val themeMode: StateFlow<ThemeMode> = container.themeMode
    val workspaceRoot: String = container.projectsRoot.absolutePath

    /**
     * In-memory only. Never persisted until the Keystore-backed secret store lands.
     */
    private val _apiKeyDraft = MutableStateFlow("")
    val apiKeyDraft: StateFlow<String> = _apiKeyDraft.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = container.setThemeMode(mode)

    fun setApiKeyDraft(value: String) {
        _apiKeyDraft.value = value
    }

    fun clearProjects() {
        container.projectStore.replaceAll(emptyList())
    }
}
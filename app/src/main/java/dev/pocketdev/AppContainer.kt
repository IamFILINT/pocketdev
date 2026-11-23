package dev.pocketdev

import android.content.Context
import dev.pocketdev.core.runtime.RuntimePaths
import dev.pocketdev.core.settings.AgentSettings
import dev.pocketdev.core.workspace.ProjectStore
import dev.pocketdev.runtime.OpenCodeController
import dev.pocketdev.runtime.RuntimeController
import dev.pocketdev.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Process-wide object graph for the app shell.
 *
 * Only non-secret state is persisted here. API keys are deliberately held in memory
 * until the Keystore-backed secret store is implemented.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Canonical layout from docs/BOOTSTRAP_LAYOUT.md; projects live outside $PREFIX. */
    val paths: RuntimePaths = RuntimePaths(appContext.filesDir).apply { ensureBaseDirectories() }

    /** Directory that contains every project's on-disk workspace. */
    val projectsRoot: File = paths.projects

    /** Registry of project metadata (id, name, root, createdAt). */
    val projectStore: ProjectStore = ProjectStore(paths.state)

    /** Android-side runtime wiring: provisioning, environment, linker-hop launch. */
    val runtime: RuntimeController = RuntimeController(appContext)

    /** OpenCode Bionic acquisition and process control (ADR-0003). */
    val openCode: OpenCodeController = OpenCodeController(appContext)

    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _agentSettings = MutableStateFlow(
        AgentSettings.fromStorage(
            preferences.getString(KEY_AGENT_PROVIDER, null),
            preferences.getString(KEY_AGENT_MODEL, null),
        ),
    )
    val agentSettings: StateFlow<AgentSettings> = _agentSettings.asStateFlow()

    /** Persists only non-secret provider/model preferences. */
    fun setAgentSettings(settings: AgentSettings) {
        preferences.edit()
            .putString(KEY_AGENT_PROVIDER, settings.provider)
            .putString(KEY_AGENT_MODEL, settings.model)
            .apply()
        _agentSettings.value = settings
    }

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromStorage(preferences.getString(KEY_THEME_MODE, null)),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "pocketdev.settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AGENT_PROVIDER = "agent_provider"
        const val KEY_AGENT_MODEL = "agent_model"
    }
}
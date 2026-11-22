package dev.pocketdev

import android.content.Context
import dev.pocketdev.core.runtime.RuntimePaths
import dev.pocketdev.core.workspace.ProjectStore
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

    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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
    }
}
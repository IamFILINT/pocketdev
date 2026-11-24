package dev.pocketdev

import android.content.Context
import dev.pocketdev.credentials.ApiKeyStore
import dev.pocketdev.credentials.KeystoreApiKeyStore
import dev.pocketdev.core.runtime.RuntimePaths
import dev.pocketdev.core.settings.AgentSettings
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.core.workspace.ProjectStore
import dev.pocketdev.runtime.OpenCodeController
import dev.pocketdev.runtime.RuntimeAgentConfig
import dev.pocketdev.runtime.RuntimeController
import dev.pocketdev.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ProjectContext(
    val projectId: String?,
    val projectName: String,
    val rootPath: String,
)

/**
 * Process-wide object graph for the app shell.
 *
 * Non-secret preferences use SharedPreferences; API keys use Android Keystore.
 */
class AppContainer(
    context: Context,
    apiKeyStore: ApiKeyStore = KeystoreApiKeyStore(context.applicationContext),
) {

    private val appContext = context.applicationContext
    private val secretStore = apiKeyStore

    /** Canonical layout from docs/BOOTSTRAP_LAYOUT.md; projects live outside $PREFIX. */
    val paths: RuntimePaths = RuntimePaths(appContext.filesDir).apply { ensureBaseDirectories() }

    /** Directory that contains every project's on-disk workspace. */
    val projectsRoot: File = paths.projects

    /** Registry of project metadata (id, name, root, createdAt). */
    val projectStore: ProjectStore = ProjectStore(paths.state)

    private val _selectedProject = MutableStateFlow(projectStore.load().firstOrNull())
    val selectedProject: StateFlow<Project?> = _selectedProject.asStateFlow()

    fun activeProjectRoot(): File = validatedProjectRoot()

    fun validatedProjectRoot(project: Project? = selectedProject.value, baseRoot: File = projectsRoot): File =
        if (project == null) {
            validatedDirectory(baseRoot, baseRoot)
        } else {
            validatedDirectory(File(project.rootPath), baseRoot)
        }

    fun validatedRootPath(path: String, baseRoot: File = projectsRoot): File =
        validatedDirectory(File(path), baseRoot)

    fun projectContext(
        project: Project? = selectedProject.value,
        baseRoot: File = projectsRoot,
    ): ProjectContext {
        val root = validatedProjectRoot(project, baseRoot)
        return ProjectContext(
            projectId = project?.id,
            projectName = project?.name ?: "Shared projects root",
            rootPath = root.absolutePath,
        )
    }

    fun projectContextOrNull(
        project: Project? = selectedProject.value,
        baseRoot: File = projectsRoot,
    ): ProjectContext? = runCatching { projectContext(project, baseRoot) }.getOrNull()

    private fun validatedDirectory(candidate: File, baseRoot: File): File {
        val root = runCatching { candidate.canonicalFile }
            .getOrElse { throw IllegalArgumentException("Invalid project root: ${candidate.path}", it) }
        val base = runCatching { baseRoot.canonicalFile }
            .getOrElse { throw IllegalArgumentException("Invalid projects root: ${baseRoot.path}", it) }
        if (root != base && !root.path.startsWith(base.path + File.separator)) {
            throw IllegalArgumentException("Project root is outside the workspace: ${candidate.path}")
        }
        if (!root.isDirectory) {
            throw IllegalStateException("Project directory does not exist: ${candidate.path}")
        }
        return root
    }

    fun selectProject(id: String?) {
        _selectedProject.value = projectStore.load().firstOrNull { it.id == id }
    }

    fun syncSelectedProject() {
        val projects = projectStore.load()
        if (selectedProject.value == null || projects.none { it.id == selectedProject.value?.id }) {
            _selectedProject.value = projects.firstOrNull()
        }
    }

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

    private val _apiKeyConfigured = MutableStateFlow(hasStoredApiKey())
    val apiKeyConfigured: StateFlow<Boolean> = _apiKeyConfigured.asStateFlow()

    /** Persists only non-secret provider/model preferences. */
    fun setAgentSettings(settings: AgentSettings) {
        preferences.edit()
            .putString(KEY_AGENT_PROVIDER, settings.provider)
            .putString(KEY_AGENT_MODEL, settings.model)
            .apply()
        _agentSettings.value = settings
        syncAgentRuntimeConfig()
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "API key must not be blank" }
        secretStore.save(trimmed)
        _apiKeyConfigured.value = true
        syncAgentRuntimeConfig()
    }

    fun clearApiKey() {
        secretStore.clear()
        _apiKeyConfigured.value = false
        syncAgentRuntimeConfig()
    }

    fun currentApiKey(): String? =
        runCatching { secretStore.read()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

    private fun hasStoredApiKey(): Boolean =
        runCatching { currentApiKey() != null }.getOrDefault(false)

    private fun syncAgentRuntimeConfig() {
        openCode.setRuntimeConfig(
            RuntimeAgentConfig(agentSettings.value.modelSelection, currentApiKey()),
        )
    }

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromStorage(preferences.getString(KEY_THEME_MODE, null)),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    init {
        syncAgentRuntimeConfig()
    }

    private companion object {
        const val PREFERENCES_NAME = "pocketdev.settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AGENT_PROVIDER = "agent_provider"
        const val KEY_AGENT_MODEL = "agent_model"
    }
}
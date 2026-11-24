package dev.pocketdev.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.ProjectContext
import dev.pocketdev.core.agent.OpenCodeSession
import dev.pocketdev.core.agent.OpenCodeSessionApi
import dev.pocketdev.core.agent.PermissionDecision
import dev.pocketdev.core.agent.PermissionRequest
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.core.settings.AgentModelSelection
import dev.pocketdev.core.settings.AgentSettings
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.runtime.AgentRuntime
import dev.pocketdev.runtime.OpenCodeServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Agent workspace coordinator: installs/supervises the server and attaches the
 * session stream once the server is actually listening.
 *
 * The task state comes only from real events (see [AgentSessionViewModel]); this
 * ViewModel never invents progress while installing or starting.
 */
class AgentWorkspaceViewModel @JvmOverloads constructor(
    application: Application,
    private val runtime: AgentRuntime = (application as PocketDevApplication).container.openCode,
    val projectsRoot: File? = null,
    private val selectedProjectFlow: StateFlow<Project?>? = null,
    private val agentSettingsFlow: StateFlow<AgentSettings>? = null,
) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container
    private val modelFlow = agentSettingsFlow ?: container.agentSettings
    private val projectFlow = selectedProjectFlow ?: container.selectedProject
    private val baseRoot = projectsRoot ?: container.projectsRoot
    private val _selectedContext = MutableStateFlow(
        runCatching { container.projectContext(projectFlow.value, baseRoot) }.getOrNull(),
    )
    val selectedProjectContext: StateFlow<ProjectContext?> = _selectedContext.asStateFlow()

    private val _serverContext = MutableStateFlow<ProjectContext?>(null)
    val serverContext: StateFlow<ProjectContext?> = _serverContext.asStateFlow()

    val agentState = runtime.agentState
    val serverState: StateFlow<OpenCodeServerState> = runtime.serverState

    private val session = AgentSessionViewModel(application)
    val taskState: StateFlow<TaskState> = session.taskState
    val streaming: StateFlow<Boolean> = session.streaming
    val streamError: StateFlow<String?> = session.error
    val permissionRequest: StateFlow<PermissionRequest?> = session.permissionRequest

    private val _sessions = MutableStateFlow<List<OpenCodeSession>>(emptyList())
    val sessions: StateFlow<List<OpenCodeSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var attachedUrl: String? = null
    private var api: OpenCodeSessionApi? = null
    private var appliedSessionId: String? = null
    private var appliedModel: AgentModelSelection? = null

    init {
        viewModelScope.launch {
            projectFlow.collect { project ->
                _selectedContext.value = runCatching { container.projectContext(project, baseRoot) }.getOrNull()
            }
        }
        viewModelScope.launch {
            runtime.serverState.collect { state ->
                when (state) {
                    is OpenCodeServerState.Listening -> attach(state.connection.baseUrl)
                    OpenCodeServerState.Stopped,
                    is OpenCodeServerState.Failed,
                    is OpenCodeServerState.Exited -> {
                        detach()
                        _serverContext.value = null
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            modelFlow.collect { settings ->
                val model = settings.modelSelection
                if (model == appliedModel) return@collect
                appliedSessionId = null
                applySelectedModel(model)
            }
        }
    }

    fun installAgent() = runBusy { runtime.install() }

    fun startServer() = runBusy {
        val context = _selectedContext.value
            ?: throw IllegalStateException("Selected project root is invalid")
        runtime.startServer(File(context.rootPath))
        _serverContext.value = context
    }

    fun stopServer() = runBusy {
        runtime.stopServer()
        _serverContext.value = null
    }

    fun selectSession(id: String) {
        _activeSessionId.value = id
        applySelectedModel(modelFlow.value.modelSelection)
    }

    fun respondToPermission(decision: PermissionDecision) = session.respondToPermission(decision)

    fun createSession() = runSend { client ->
        val model = selectedModel()
        val created = client.createSession(model = model)
        appliedModel = model
        appliedSessionId = created.id
        refreshSessions(client)
        _activeSessionId.value = created.id
    }

    fun sendPrompt(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) {
            _actionError.value = "Prompt must not be blank"
            return
        }
        runSend { client ->
            val model = selectedModel()
            val sessionId = _activeSessionId.value ?: client.createSession(model = model).id.also { newId ->
                appliedModel = model
                appliedSessionId = newId
                refreshSessions(client)
                _activeSessionId.value = newId
            }
            client.prompt(sessionId, prompt)
        }
    }

    override fun onCleared() {
        session.stop()
    }

    private fun attach(baseUrl: String) {
        if (attachedUrl == baseUrl) return
        attachedUrl = baseUrl
        val client = runtime.newApiClient() ?: return
        api = client
        session.start(client)
        viewModelScope.launch(Dispatchers.IO) {
            refreshSessions(client)
            applySelectedModel(selectedModel())
        }
    }

    private fun detach() {
        if (attachedUrl == null) return
        attachedUrl = null
        api = null
        _activeSessionId.value = null
        appliedSessionId = null
        appliedModel = null
        session.stop()
        _sessions.value = emptyList()
    }

    private fun selectedModel(): AgentModelSelection? = modelFlow.value.modelSelection

    private fun applySelectedModel(model: AgentModelSelection?) {
        appliedModel = model
        val sessionId = _activeSessionId.value
        val client = api
        if (model == null || sessionId == null || client == null) return
        if (appliedSessionId == sessionId && appliedModel == model) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { client.switchModel(sessionId, model) }
                .onSuccess {
                    appliedSessionId = sessionId
                    appliedModel = model
                }
                .onFailure { _actionError.value = it.message ?: it::class.simpleName }
        }
    }

    private fun refreshSessions(client: OpenCodeSessionApi) {
        runCatching { client.listSessions() }
            .onSuccess { loaded ->
                _sessions.value = loaded
                if (_activeSessionId.value == null || loaded.none { it.id == _activeSessionId.value }) {
                    _activeSessionId.value = loaded.firstOrNull()?.id
                }
            }
            .onFailure { _actionError.value = it.message ?: it::class.simpleName }
    }

    private fun runBusy(block: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _actionError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                _actionError.value = e.message ?: e::class.simpleName
            } finally {
                _busy.value = false
            }
        }
    }

    private fun runSend(block: (OpenCodeSessionApi) -> Unit) {
        if (_sending.value) return
        val client = api ?: run {
            _actionError.value = "Agent server is not listening"
            return
        }
        _sending.value = true
        _actionError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(client)
            } catch (e: Exception) {
                _actionError.value = e.message ?: e::class.simpleName
            } finally {
                _sending.value = false
            }
        }
    }
}

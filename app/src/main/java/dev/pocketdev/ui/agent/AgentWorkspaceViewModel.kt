package dev.pocketdev.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.agent.OpenCodeSession
import dev.pocketdev.core.agent.OpenCodeSessionApi
import dev.pocketdev.core.ai.TaskState
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
    val projectsRoot: File = (application as PocketDevApplication).container.projectsRoot,
) : AndroidViewModel(application) {

    val agentState = runtime.agentState
    val serverState: StateFlow<OpenCodeServerState> = runtime.serverState

    private val session = AgentSessionViewModel(application)
    val taskState: StateFlow<TaskState> = session.taskState
    val streaming: StateFlow<Boolean> = session.streaming
    val streamError: StateFlow<String?> = session.error

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

    init {
        viewModelScope.launch {
            runtime.serverState.collect { state ->
                when (state) {
                    is OpenCodeServerState.Listening -> attach(state.connection.baseUrl)
                    else -> detach()
                }
            }
        }
    }

    fun installAgent() = runBusy { runtime.install() }

    fun startServer() = runBusy { runtime.startServer(projectsRoot) }

    fun stopServer() = runBusy { runtime.stopServer() }

    fun selectSession(id: String) {
        _activeSessionId.value = id
    }

    fun createSession() = runSend { client ->
        val created = client.createSession()
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
            val sessionId = _activeSessionId.value ?: client.createSession().id.also {
                refreshSessions(client)
                _activeSessionId.value = it
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
        viewModelScope.launch(Dispatchers.IO) { refreshSessions(client) }
    }

    private fun detach() {
        if (attachedUrl == null) return
        attachedUrl = null
        api = null
        _activeSessionId.value = null
        session.stop()
        _sessions.value = emptyList()
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

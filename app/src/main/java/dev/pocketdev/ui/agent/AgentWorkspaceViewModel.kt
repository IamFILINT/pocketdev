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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var attachedUrl: String? = null

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

    fun installAgent() = runBusy {
        runtime.install()
    }

    fun startServer() = runBusy {
        runtime.startServer(projectsRoot)
    }

    fun stopServer() = runBusy {
        runtime.stopServer()
    }

    override fun onCleared() {
        session.stop()
    }

    private fun attach(baseUrl: String) {
        if (attachedUrl == baseUrl) return
        attachedUrl = baseUrl
        val api = runtime.newApiClient() ?: return
        session.start(api)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.listSessions() }
                .onSuccess { _sessions.value = it }
                .onFailure { _actionError.value = it.message ?: it::class.simpleName }
        }
    }

    private fun detach() {
        if (attachedUrl == null) return
        attachedUrl = null
        session.stop()
        _sessions.value = emptyList()
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
}

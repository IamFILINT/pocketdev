package dev.pocketdev.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.runtime.RuntimeState
import dev.pocketdev.core.terminal.TerminalSnapshot
import dev.pocketdev.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TerminalSessionInfo(
    val id: String,
    val name: String,
)

data class TerminalUiState(
    val rootPath: String = "",
    val sessions: List<TerminalSessionInfo> = emptyList(),
    val activeId: String? = null,
    val snapshot: TerminalSnapshot = TerminalSnapshot(emptyList(), 80, 24),
    val error: String? = null,
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container
    private val sessions = LinkedHashMap<String, TerminalSession>()
    private val listeners = mutableMapOf<String, (TerminalSnapshot) -> Unit>()
    private var nextSessionNumber = 1
    private var shutdown = false

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        createSession()
    }

    fun createSession() {
        val root = container.activeProjectRoot()
        val runtime = container.runtime
        if (runtime.state.value !is RuntimeState.Ready) {
            _uiState.update {
                it.copy(
                    rootPath = root.absolutePath,
                    error = "Bionic runtime is not ready; provision the runtime before opening a terminal.",
                )
            }
            return
        }
        val plan = try {
            runtime.launchPlan(runtime.bionicShell(), "-i", workingDir = root)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(rootPath = root.absolutePath, error = "Could not build terminal launch plan: ${e.message}")
            }
            return
        }
        val session = TerminalSession(
            name = "bash-${nextSessionNumber++}",
            plan = plan,
        )
        val id = session.id
        val listener: (TerminalSnapshot) -> Unit = { snapshot ->
            if (_uiState.value.activeId == id) _uiState.update { it.copy(snapshot = snapshot) }
        }
        listeners[id] = listener
        session.addSnapshotListener(listener)
        sessions[id] = session
        _uiState.update {
            it.copy(
                rootPath = root.absolutePath,
                sessions = sessions.values.map { TerminalSessionInfo(it.id, it.name) },
                activeId = id,
                snapshot = session.snapshot(),
                error = null,
            )
        }
    }

    fun selectSession(id: String) {
        val session = sessions[id] ?: return
        _uiState.update { it.copy(activeId = id, snapshot = session.snapshot()) }
    }

    fun closeSession(id: String) {
        val session = sessions.remove(id) ?: return
        listeners.remove(id)?.let(session::removeSnapshotListener)
        session.close()
        val replacement = sessions.values.lastOrNull()
        if (replacement == null) {
            createSession()
            return
        }
        _uiState.update {
            it.copy(
                sessions = sessions.values.map { TerminalSessionInfo(it.id, it.name) },
                activeId = replacement.id,
                snapshot = replacement.snapshot(),
            )
        }
    }

    fun send(text: String) {
        activeSession()?.send(text)
    }

    fun resize(cols: Int, rows: Int) {
        activeSession()?.resize(cols, rows)
    }

    fun plainText(): String = activeSession()?.plainText().orEmpty()

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun shutdown() {
        if (shutdown) return
        shutdown = true
        listeners.forEach { (id, listener) -> sessions[id]?.removeSnapshotListener(listener) }
        sessions.values.forEach { it.close() }
        sessions.clear()
        listeners.clear()
    }

    override fun onCleared() {
        shutdown()
        super.onCleared()
    }

    private fun activeSession(): TerminalSession? = sessions[_uiState.value.activeId]
}

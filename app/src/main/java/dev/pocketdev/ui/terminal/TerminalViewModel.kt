package dev.pocketdev.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.runtime.RuntimeState
import dev.pocketdev.core.terminal.TerminalSnapshot
import dev.pocketdev.terminal.PtyExitInfo
import dev.pocketdev.terminal.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TerminalSessionInfo(
    val id: String,
    val name: String,
    val exit: PtyExitInfo = PtyExitInfo(),
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
    private val sessionLock = Any()
    private val sessions = LinkedHashMap<String, TerminalSession>()
    private val listeners = mutableMapOf<String, (TerminalSnapshot) -> Unit>()
    private var nextSessionNumber = 1
    @Volatile private var shutdown = false

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
        val session = try {
            val plan = runtime.launchPlan(runtime.bionicShell(), "-i", workingDir = root)
            TerminalSession(name = "bash-${nextSessionNumber++}", plan = plan)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(rootPath = root.absolutePath, error = "Could not start terminal: ${e.message}")
            }
            return
        }

        val id = session.id
        val listener: (TerminalSnapshot) -> Unit = { snapshot ->
            if (_uiState.value.activeId == id) _uiState.update { it.copy(snapshot = snapshot) }
        }
        synchronized(sessionLock) {
            if (shutdown) {
                session.close()
                return
            }
            listeners[id] = listener
            sessions[id] = session
        }
        session.addSnapshotListener(listener)
        _uiState.update {
            it.copy(
                rootPath = root.absolutePath,
                sessions = sessionInfos(),
                activeId = id,
                snapshot = session.snapshot(),
                error = null,
            )
        }
    }

    fun selectSession(id: String) {
        val session = synchronized(sessionLock) { sessions[id] }
        if (session == null) {
            _uiState.update { it.copy(activeId = null) }
            return
        }
        _uiState.update { it.copy(activeId = id, snapshot = session.snapshot()) }
    }

    fun closeSession(id: String) {
        val removed = synchronized(sessionLock) {
            val session = sessions.remove(id)
            if (session != null) listeners.remove(id)?.let(session::removeSnapshotListener)
            session
        } ?: return

        removed.close()
        val replacement = synchronized(sessionLock) { sessions.values.lastOrNull() }
        if (replacement == null) {
            if (!shutdown) createSession()
            return
        }
        _uiState.update {
            it.copy(
                sessions = synchronized(sessionLock) { sessionInfos() },
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

    fun sessionExit(id: String): PtyExitInfo? = synchronized(sessionLock) { sessions[id]?.exitInfo() }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun shutdown() {
        val toClose = synchronized(sessionLock) {
            if (shutdown) return
            shutdown = true
            val current = sessions.values.toList()
            listeners.forEach { (id, listener) -> sessions[id]?.removeSnapshotListener(listener) }
            sessions.clear()
            listeners.clear()
            current
        }
        toClose.forEach { it.close() }
    }

    override fun onCleared() {
        shutdown()
        super.onCleared()
    }

    private fun activeSession(): TerminalSession? {
        val id = _uiState.value.activeId ?: return null
        return synchronized(sessionLock) { sessions[id] }
    }

    private fun sessionInfos(): List<TerminalSessionInfo> = synchronized(sessionLock) {
        sessions.values.map { TerminalSessionInfo(it.id, it.name, it.exitInfo()) }
    }
}

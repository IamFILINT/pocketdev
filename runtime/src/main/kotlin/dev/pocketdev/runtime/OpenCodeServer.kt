package dev.pocketdev.runtime

import dev.pocketdev.core.agent.OpenCodeServerConnection
import dev.pocketdev.core.agent.OpenCodeServeOutputParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** Lifecycle of a supervised `opencode serve` process. */
sealed interface OpenCodeServerState {
    data object Stopped : OpenCodeServerState
    data object Starting : OpenCodeServerState

    /** The server is listening; [connection] carries its URL and password. */
    data class Listening(val connection: OpenCodeServerConnection) : OpenCodeServerState

    /** The process never became ready (launch failure, non-loopback URL, early exit). */
    data class Failed(val message: String) : OpenCodeServerState

    /** The process ended after having listened. */
    data class Exited(val exitCode: Int) : OpenCodeServerState
}

/**
 * Supervises one `opencode serve` child process for the lifetime of a project.
 *
 * Responsibilities:
 * - start the agent through a [ProcessLauncher] (the linker hop in production);
 * - read stdout/stderr, parse the upstream readiness lines
 *   (`server listening on …`, `server password …`) with [OpenCodeServeOutputParser];
 * - publish real state transitions to [state], never invented progress;
 * - keep draining output so the child cannot block on a full pipe;
 * - end with [Exited]/[Failed] when the process dies.
 *
 * All methods are blocking or thread-confined; callers drive them off the main thread.
 */
class OpenCodeServer(
    private val launcher: ProcessLauncher,
    private val onOutputLine: (String) -> Unit = {},
) {

    private val _state = MutableStateFlow<OpenCodeServerState>(OpenCodeServerState.Stopped)
    val state: StateFlow<OpenCodeServerState> = _state.asStateFlow()

    private val lock = Any()
    private val connectionSeen = AtomicBoolean(false)

    @Volatile
    private var processRef: Process? = null

    @Volatile
    private var readerThread: Thread? = null

    @Volatile
    private var waiterThread: Thread? = null

    /**
     * Starts the agent binary as `serve --hostname 127.0.0.1` inside [projectDir].
     * Stops any previous instance first.
     */
    fun start(binary: File, projectDir: File, args: List<String> = DEFAULT_SERVE_ARGS): OpenCodeServerState {
        stop()
        synchronized(lock) {
            connectionSeen.set(false)
            _state.value = OpenCodeServerState.Starting
        }

        val process = try {
            launcher.launch(projectDir, binary, args)
        } catch (e: Exception) {
            val message = "failed to launch OpenCode: ${e.message ?: e::class.java.simpleName}"
            synchronized(lock) { _state.value = OpenCodeServerState.Failed(message) }
            return _state.value
        }
        processRef = process

        val parser = OpenCodeServeOutputParser()
        val reader = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        onOutputLine(line)
                        val connection = parser.onLine(line) ?: continue
                        if (connectionSeen.compareAndSet(false, true)) {
                            synchronized(lock) {
                                if (_state.value is OpenCodeServerState.Starting) {
                                    _state.value = OpenCodeServerState.Listening(connection)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed on destroy; the waiter records the terminal state.
            }
        }, "opencode-serve-reader").apply { isDaemon = true }

        val waiter = Thread({
            val code = try {
                process.waitFor()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@Thread
            }
            reader.join(JOIN_TIMEOUT_MS)
            synchronized(lock) {
                val current = _state.value
                if (current is OpenCodeServerState.Starting || current is OpenCodeServerState.Listening) {
                    _state.value = if (connectionSeen.get()) {
                        OpenCodeServerState.Exited(code)
                    } else {
                        OpenCodeServerState.Failed("OpenCode exited before listening (exit $code)")
                    }
                }
            }
        }, "opencode-serve-waiter").apply { isDaemon = true }

        readerThread = reader
        waiterThread = waiter
        reader.start()
        waiter.start()
        return _state.value
    }

    /** Terminates the supervised process, if any, and returns to [OpenCodeServerState.Stopped]. */
    fun stop() {
        val process = processRef
        processRef = null
        if (process != null && process.isAlive) {
            process.destroy()
        }
        readerThread?.join(JOIN_TIMEOUT_MS)
        waiterThread?.join(JOIN_TIMEOUT_MS)
        readerThread = null
        waiterThread = null
        synchronized(lock) { _state.value = OpenCodeServerState.Stopped }
    }

    fun isRunning(): Boolean = processRef?.isAlive == true

    companion object {
        const val DEFAULT_HOSTNAME = "127.0.0.1"
        val DEFAULT_SERVE_ARGS = listOf("serve", "--hostname", DEFAULT_HOSTNAME)
        private const val JOIN_TIMEOUT_MS = 2_000L
    }
}

package dev.pocketdev.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.core.agent.OpenCodeSessionApi
import dev.pocketdev.core.agent.ServerSentEvent
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.core.ai.TaskStateMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Drives one OpenCode session's task state from the real SSE stream.
 *
 * Only events delivered by [OpenCodeSessionApi.streamEvents] can change
 * [taskState]; illegal/out-of-order events are ignored by [TaskStateMapper].
 * Streaming runs off the main thread and is stopped with [stop].
 */
class AgentSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _taskState = MutableStateFlow(TaskState.IDLE)
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var streamJob: Job? = null
    private var generation = 0

    /** Opens the event stream; returns immediately. Safe to call again (restarts). */
    fun start(api: OpenCodeSessionApi, sessionId: String? = null) {
        stop()
        val current = ++generation
        _error.value = null
        _streaming.value = true
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                runInterruptible {
                    api.streamEvents(sessionId) { event ->
                        applyEvent(event)
                        true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current == generation) _error.value = e.message ?: e::class.simpleName
            } finally {
                if (current == generation) {
                    _streaming.value = false
                    streamJob = null
                }
            }
        }
    }

    fun stop() {
        generation++
        streamJob?.cancel()
        streamJob = null
        _streaming.value = false
    }

    fun applyEvent(event: ServerSentEvent) {
        val next = TaskStateMapper.fromEvent(event, _taskState.value)
        if (next != null) _taskState.value = next
    }

    fun reset() {
        _taskState.value = TaskState.IDLE
    }
}

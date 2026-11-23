package dev.pocketdev.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.core.agent.ServerSentEvent
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.core.ai.TaskStateMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _taskState = MutableStateFlow(TaskState.IDLE)
    val taskState: StateFlow<TaskState> = _taskState.asStateFlow()

    fun applyEvent(event: ServerSentEvent) {
        val next = TaskStateMapper.fromEvent(event, _taskState.value)
        if (next != null) _taskState.value = next
    }

    fun reset() {
        _taskState.value = TaskState.IDLE
    }
}

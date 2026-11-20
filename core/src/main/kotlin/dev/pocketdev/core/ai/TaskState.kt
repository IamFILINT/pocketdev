package dev.pocketdev.core.ai

/**
 * State of a single AI agent task as surfaced by the OpenCode event stream
 * (master prompt §10.3). The UI may only show a state that an actual OpenCode
 * session event has produced.
 */
enum class TaskState {
    IDLE,
    INITIALIZING,
    THINKING,
    TOOL_RUNNING,
    WAITING_FOR_PERMISSION,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

/**
 * Allowed task-state transitions. Kept explicit so that fake or out-of-order
 * progress cannot be rendered by accident.
 */
object TaskStateMachine {

    private val transitions: Map<TaskState, Set<TaskState>> = mapOf(
        TaskState.IDLE to setOf(TaskState.INITIALIZING, TaskState.CANCELLED),
        TaskState.INITIALIZING to setOf(
            TaskState.THINKING,
            TaskState.RUNNING,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.THINKING to setOf(
            TaskState.TOOL_RUNNING,
            TaskState.WAITING_FOR_PERMISSION,
            TaskState.RUNNING,
            TaskState.COMPLETED,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.TOOL_RUNNING to setOf(
            TaskState.THINKING,
            TaskState.WAITING_FOR_PERMISSION,
            TaskState.RUNNING,
            TaskState.COMPLETED,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.WAITING_FOR_PERMISSION to setOf(
            TaskState.TOOL_RUNNING,
            TaskState.THINKING,
            TaskState.CANCELLED,
            TaskState.FAILED,
        ),
        TaskState.RUNNING to setOf(
            TaskState.THINKING,
            TaskState.TOOL_RUNNING,
            TaskState.WAITING_FOR_PERMISSION,
            TaskState.COMPLETED,
            TaskState.FAILED,
            TaskState.CANCELLED,
        ),
        TaskState.COMPLETED to setOf(TaskState.IDLE),
        TaskState.FAILED to setOf(TaskState.IDLE),
        TaskState.CANCELLED to setOf(TaskState.IDLE),
    )

    fun canTransition(from: TaskState, to: TaskState): Boolean =
        transitions[from]?.contains(to) == true

    fun requireTransition(from: TaskState, to: TaskState): TaskState {
        require(canTransition(from, to)) { "Illegal task transition: $from -> $to" }
        return to
    }
}
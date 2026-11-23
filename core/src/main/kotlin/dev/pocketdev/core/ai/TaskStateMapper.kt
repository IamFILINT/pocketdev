package dev.pocketdev.core.ai

import dev.pocketdev.core.agent.ServerSentEvent
import dev.pocketdev.core.agent.type
import dev.pocketdev.core.json.Json

/**
 * Maps an OpenCode SSE event to the next [TaskState], if the event implies a
 * state change and the transition is allowed by [TaskStateMachine].
 *
 * Returns null when the event does not imply a state change or when the implied
 * transition would be illegal (out-of-order events are ignored rather than
 * crashing the stream).
 */
object TaskStateMapper {

    fun fromEvent(event: ServerSentEvent, current: TaskState): TaskState? {
        val implied = impliedState(event) ?: return null
        return if (TaskStateMachine.canTransition(current, implied)) implied else null
    }

    private fun impliedState(event: ServerSentEvent): TaskState? {
        val name = event.type ?: "message"
        return when (name) {
            "session.created" -> TaskState.INITIALIZING
            "session.idle" -> TaskState.COMPLETED
            "session.error" -> TaskState.FAILED
            "permission.asked" -> TaskState.WAITING_FOR_PERMISSION
            "permission.replied" -> TaskState.TOOL_RUNNING
            "session.updated" -> sessionUpdatedState(event)
            "message.part.updated" -> messagePartState(event)
            else -> null
        }
    }

    private fun sessionUpdatedState(event: ServerSentEvent): TaskState? {
        val status = stringField(event.data, "status")
            ?: stringField(event.data, "state")
        return when (status?.lowercase()) {
            "busy", "running" -> TaskState.RUNNING
            "idle" -> TaskState.COMPLETED
            else -> null
        }
    }

    private fun messagePartState(event: ServerSentEvent): TaskState? {
        val type = stringField(event.data, "type")
            ?: stringField(event.data, "partType")
        return when (type?.lowercase()) {
            "reasoning" -> TaskState.THINKING
            "tool", "tool_use", "tool_result" -> TaskState.TOOL_RUNNING
            "text" -> TaskState.RUNNING
            else -> null
        }
    }

    private fun stringField(json: String, name: String): String? {
        if (json.isBlank()) return null
        return try {
            val root = Json.parse(json)
            when (root) {
                is Map<*, *> -> (root[name] as? String)
                    ?: (root["properties"] as? Map<*, *>)?.get(name) as? String
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

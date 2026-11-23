package dev.pocketdev.core.agent

import dev.pocketdev.core.json.Json
import dev.pocketdev.core.json.asArray
import dev.pocketdev.core.json.asObject
import dev.pocketdev.core.json.asString
import dev.pocketdev.core.json.field

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val action: String,
    val resources: List<String>,
    val message: String?,
) {

    companion object {

        fun fromEvent(event: ServerSentEvent): PermissionRequest? {
            if (event.type != "permission.asked") return null
            val root = runCatching { Json.parseObject(event.data) }.getOrNull() ?: return null
            val payload = root.field("data")?.asObject() ?: root
            val id = payload.field("id")?.asString() ?: return null
            val sessionId = payload.field("sessionID")?.asString() ?: return null
            val action = payload.field("action")?.asString() ?: return null
            val resources = payload.field("resources")?.asArray().orEmpty().mapNotNull { it?.asString() }
            return PermissionRequest(
                id = id,
                sessionId = sessionId,
                action = action,
                resources = resources,
                message = payload.field("message")?.asString(),
            )
        }
    }
}

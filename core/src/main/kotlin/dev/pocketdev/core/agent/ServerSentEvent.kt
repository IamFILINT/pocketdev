package dev.pocketdev.core.agent

import dev.pocketdev.core.json.Json
import dev.pocketdev.core.json.asString
import dev.pocketdev.core.json.field

/**
 * One Server-Sent Event as defined by the SSE spec and emitted by OpenCode's
 * `GET /api/event` endpoint.
 *
 * Only the fields PocketDev consumes are modelled; unknown fields are ignored.
 */
data class ServerSentEvent(
    /** Event type from the `event:` field, or null when absent (defaults to "message"). */
    val event: String?,
    /** Accumulated `data:` payload, joined with newlines. */
    val data: String,
    /** Last event id from the `id:` field, if present. */
    val id: String?,
)

val ServerSentEvent.type: String?
    get() = event?.takeIf { it != "message" }
        ?: runCatching { Json.parseObject(data).field("type")?.asString() }.getOrNull()
        ?: event

/**
 * Stateless parser for Server-Sent Event streams.
 *
 * Operates on the *entire* stream text and returns the complete list of events.
 * A trailing event without a terminating blank line is still returned. Empty
 * events (no data) are dropped, matching browser EventSource behaviour.
 */
object ServerSentEvents {

    fun parse(text: String): List<ServerSentEvent> {
        val events = mutableListOf<ServerSentEvent>()
        val lines = text.split('\n')
        var eventName: String? = null
        var id: String? = null
        val data = StringBuilder()
        var hasData = false

        fun flush() {
            if (hasData) {
                events.add(ServerSentEvent(event = eventName, data = data.toString(), id = id))
            }
            eventName = null
            id = null
            data.setLength(0)
            hasData = false
        }

        for (raw in lines) {
            val line = raw.removeSuffix("\r")
            when {
                line.isEmpty() -> flush()
                line.startsWith(":") -> { /* comment, ignore */ }
                else -> {
                    val colon = line.indexOf(':')
                    val field = if (colon < 0) line else line.substring(0, colon)
                    val value = if (colon < 0) "" else {
                        val v = line.substring(colon + 1)
                        if (v.startsWith(" ")) v.substring(1) else v
                    }
                    when (field) {
                        "event" -> eventName = value
                        "data" -> {
                            if (hasData) data.append('\n')
                            data.append(value)
                            hasData = true
                        }
                        "id" -> id = value
                        // "retry" and unknown fields ignored
                    }
                }
            }
        }
        flush()
        return events
    }
}

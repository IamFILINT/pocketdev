package dev.pocketdev.core.agent

import dev.pocketdev.core.settings.AgentModelSelection

/**
 * Session operations PocketDev needs from a running OpenCode server.
 *
 * Extracted so UI coordinators can be exercised with fakes on the JVM or emulator
 * without a live agent; [OpenCodeApiClient] is the production implementation.
 */
interface OpenCodeSessionApi {

    /** `GET /api/session`. */
    fun listSessions(limit: Int = 50): List<OpenCodeSession>

    /** `POST /api/session`. */
    fun createSession(
        title: String? = null,
        model: AgentModelSelection? = null,
    ): OpenCodeSession

    /** `POST /api/session/{id}/model`. */
    fun switchModel(
        sessionId: String,
        model: AgentModelSelection,
    )

    /** `POST /api/session/{id}/prompt`. */
    fun prompt(sessionId: String, text: String): String

    /** `POST /api/session/{id}/permission/{requestID}/reply`. */
    fun replyPermission(sessionId: String, requestId: String, decision: PermissionDecision)

    /**
     * Streams `GET /api/event`, delivering each parsed event to [onEvent] until the
     * handler returns false or the server closes the stream. Blocking.
     */
    fun streamEvents(sessionId: String? = null, onEvent: (ServerSentEvent) -> Boolean): Int
}

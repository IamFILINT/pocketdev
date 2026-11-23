package dev.pocketdev.core.agent

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
    fun createSession(title: String? = null): OpenCodeSession

    /** `POST /api/session/{id}/prompt`. */
    fun prompt(sessionId: String, text: String): String

    /**
     * Streams `GET /api/event`, delivering each parsed event to [onEvent] until the
     * handler returns false or the server closes the stream. Blocking.
     */
    fun streamEvents(sessionId: String? = null, onEvent: (ServerSentEvent) -> Boolean): Int
}

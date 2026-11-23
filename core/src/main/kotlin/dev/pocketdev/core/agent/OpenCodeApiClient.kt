package dev.pocketdev.core.agent

import dev.pocketdev.core.json.Json
import dev.pocketdev.core.json.asArray
import dev.pocketdev.core.json.asLong
import dev.pocketdev.core.json.asObject
import dev.pocketdev.core.json.asString
import dev.pocketdev.core.json.field
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

/** A single HTTP request/response pair used by [HttpTransport]. */
class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

class HttpResponse(
    val status: Int,
    val body: String,
)

/**
 * Executes one HTTP call. Implemented over [HttpURLConnection] so `:core` needs no
 * third-party HTTP client and behaves identically on the JVM and Android.
 */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse

    /**
     * Opens the response body as a stream. Default implementation buffers via
     * [execute]; transports that support true streaming override this. Callers
     * must close the returned stream.
     */
    @Throws(IOException::class)
    fun openStream(request: HttpRequest): InputStream =
        ByteArrayInputStream(execute(request).body.toByteArray(StandardCharsets.UTF_8))
}

/** Default [HttpTransport] backed by [HttpURLConnection]. */
class HttpUrlConnectionTransport(
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : HttpTransport {

    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val body = request.body
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    override fun openStream(request: HttpRequest): InputStream {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = 0
        connection.instanceFollowRedirects = false
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

        val body = request.body
        if (body != null) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }

        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw OpenCodeApiException(status, "${request.method} ${request.url}: HTTP $status")
        }
        // Caller owns the stream; closing it releases the connection.
        return connection.inputStream
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000
    }
}

/** Thrown when the OpenCode server answers with a non-2xx status or malformed JSON. */
class OpenCodeApiException(
    val status: Int,
    message: String,
) : RuntimeException(message)

/** `/api/info` projection PocketDev needs for health/readiness display. */
data class OpenCodeServerInfo(
    val version: String,
    val pid: Long,
    val hostname: String?,
    val urls: List<String>,
)

/** Minimal `/api/session` list/create projection. */
data class OpenCodeSession(
    val id: String,
    val title: String?,
    val parentID: String?,
)

/**
 * HTTP client for the pinned OpenCode Bionic server (`opencode serve`).
 *
 * Authentication is HTTP Basic with upstream's fixed username `opencode` and the
 * per-run password captured by [OpenCodeServeOutputParser]. All calls are blocking;
 * callers must dispatch off the main thread.
 */
class OpenCodeApiClient(
    baseUrl: String,
    private val password: String,
    private val username: String = "opencode",
    private val transport: HttpTransport = HttpUrlConnectionTransport(),
) : OpenCodeSessionApi {
    val baseUrl: String = baseUrl.trimEnd('/')

    init {
        require(password.isNotBlank()) { "password must not be blank" }
        require(username.isNotBlank()) { "username must not be blank" }
    }

    /** Calls `GET /api/info`; used as the health probe for a supervised server. */
    fun info(): OpenCodeServerInfo {
        val response = get("/api/info")
        val root = Json.parseObject(response.body)
        return OpenCodeServerInfo(
            version = root.field("version")?.asString() ?: "",
            pid = root.field("pid")?.asLong() ?: 0L,
            hostname = root.field("hostname")?.asString(),
            urls = root.field("urls")?.asArray().orEmpty().mapNotNull { it?.asString() },
        )
    }

    /** Calls `GET /api/session`. */
    override fun listSessions(limit: Int): List<OpenCodeSession> {
        val response = get("/api/session?limit=$limit")
        val data = Json.parseObject(response.body).field("data")?.asArray().orEmpty()
        return data.mapNotNull { entry ->
            val obj = entry?.asObject() ?: return@mapNotNull null
            val id = obj.field("id")?.asString() ?: return@mapNotNull null
            OpenCodeSession(
                id = id,
                title = obj.field("title")?.asString(),
                parentID = obj.field("parentID")?.asString(),
            )
        }
    }

    /** Calls `POST /api/session`. */
    override fun createSession(title: String?): OpenCodeSession {
        val body = StringBuilder().apply {
            append('{')
            if (title != null) {
                append("\"title\":")
                appendString(this, title)
            }
            append('}')
        }.toString()
        val response = post("/api/session", body)
        val data = Json.parseObject(response.body).field("data")?.asObject()
            ?: throw OpenCodeApiException(response.status, "session.create returned no data")
        val id = data.field("id")?.asString()
            ?: throw OpenCodeApiException(response.status, "session.create returned no id")
        return OpenCodeSession(id, data.field("title")?.asString(), data.field("parentID")?.asString())
    }

    /**
     * Calls `POST /api/session/{sessionID}/prompt`. Returns the raw response body;
     * event-stream processing belongs to the AI workspace milestone.
     */
    override fun prompt(sessionId: String, text: String): String {
        val body = StringBuilder().apply {
            append("{\"text\":")
            appendString(this, text)
            append('}')
        }.toString()
        return post("/api/session/${encodePathSegment(sessionId)}/prompt", body).body
    }

    /**
     * Opens the OpenCode SSE event stream (`GET /api/event`) and delivers each
     * parsed event to [onEvent] until the stream closes or [onEvent] returns false.
     *
     * Returns the number of events delivered. Blocking: dispatch off the main thread.
     * The optional [sessionId] filters events to one session when the server supports it.
     */
    override fun streamEvents(sessionId: String?, onEvent: (ServerSentEvent) -> Boolean): Int {
        val path = if (sessionId == null) "/api/event" else "/api/event?sessionID=${encodePathSegment(sessionId)}"
        val request = HttpRequest("GET", baseUrl + path, authHeaders())
        val stream = transport.openStream(request)
        var delivered = 0
        try {
            val reader = stream.bufferedReader(StandardCharsets.UTF_8)
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            // Accumulate a full event (terminated by a blank line) before parsing.
            val pending = StringBuilder()
            var eventName: String? = null
            var id: String? = null
            val data = StringBuilder()
            var hasData = false

            fun emit(): Boolean {
                if (!hasData) return true
                val ev = ServerSentEvent(event = eventName, data = data.toString(), id = id)
                delivered++
                val cont = onEvent(ev)
                eventName = null
                id = null
                data.setLength(0)
                hasData = false
                return cont
            }

            var running = true
            while (running) {
                val raw = reader.readLine() ?: break
                val line = raw.removeSuffix("\r")
                when {
                    line.isEmpty() -> {
                        if (!emit()) { running = false }
                    }
                    line.startsWith(":") -> { /* comment */ }
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
                        }
                    }
                }
            }
            // Flush a trailing event without a terminating blank line.
            if (hasData && running) emit()
        } finally {
            stream.close()
        }
        return delivered
    }

    private fun get(path: String): HttpResponse =
        execute(HttpRequest("GET", baseUrl + path, authHeaders()))

    private fun post(path: String, jsonBody: String): HttpResponse =
        execute(
            HttpRequest(
                method = "POST",
                url = baseUrl + path,
                headers = authHeaders() + mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                ),
                body = jsonBody.toByteArray(StandardCharsets.UTF_8),
            ),
        )

    private fun execute(request: HttpRequest): HttpResponse {
        val response = transport.execute(request)
        if (response.status !in 200..299) {
            throw OpenCodeApiException(response.status, "${request.method} ${request.url}: ${response.body.take(200)}")
        }
        return response
    }

    private fun authHeaders(): Map<String, String> {
        val token = Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
        return mapOf(
            "Authorization" to "Basic $token",
            "Accept" to "application/json",
        )
    }

    private fun encodePathSegment(segment: String): String =
        java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8.name())

    private fun appendString(target: StringBuilder, value: String) {
        target.append('"')
        for (char in value) {
            when (char) {
                '"' -> target.append("\\\"")
                '\\' -> target.append("\\\\")
                '\n' -> target.append("\\n")
                '\r' -> target.append("\\r")
                '\t' -> target.append("\\t")
                '\b' -> target.append("\\b")
                '\u000C' -> target.append("\\f")
                else -> if (char < ' ') {
                    target.append("\\u%04x".format(char.code))
                } else {
                    target.append(char)
                }
            }
        }
        target.append('"')
    }
}

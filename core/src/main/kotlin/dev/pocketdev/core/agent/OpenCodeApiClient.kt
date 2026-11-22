package dev.pocketdev.core.agent

import dev.pocketdev.core.json.Json
import java.io.IOException
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
    password: String,
    username: String = "opencode",
    private val transport: HttpTransport = HttpUrlConnectionTransport(),
) {
    val baseUrl: String = baseUrl.trimEnd('/')

    init {
        require(password.isNotBlank()) { "password must not be blank" }
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
    fun listSessions(limit: Int = 50): List<OpenCodeSession> {
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
    fun createSession(title: String? = null): OpenCodeSession {
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
    fun prompt(sessionId: String, text: String): String {
        val body = StringBuilder().apply {
            append("{\"text\":")
            appendString(this, text)
            append('}')
        }.toString()
        return post("/api/session/${encodePathSegment(sessionId)}/prompt", body).body
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

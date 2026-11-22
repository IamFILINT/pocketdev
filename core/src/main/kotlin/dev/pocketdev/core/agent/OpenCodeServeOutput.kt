package dev.pocketdev.core.agent

import java.net.URI

/**
 * Connection details captured from `opencode serve`.
 *
 * Upstream v2.3.2 (foreground/default mode) prints two plain stdout lines once the
 * HTTP server is ready:
 *
 * ```
 * server listening on http://127.0.0.1:4096
 * server password <base64url>
 * ```
 *
 * The password authenticates every API request with HTTP Basic auth; upstream
 * pins the username to `opencode` (`ServerAuth.configLayer`).
 */
data class OpenCodeServerConnection(
    /** Base URL printed by the server, e.g. `http://127.0.0.1:4096`. */
    val url: String,
    /** TCP port the server bound. */
    val port: Int,
    /** HTTP Basic username; upstream always uses `opencode`. */
    val username: String = DEFAULT_USERNAME,
    /** HTTP Basic password generated per server run. */
    val password: String,
) {
    init {
        require(url.startsWith("http://")) { "url must be http:// for a loopback server: $url" }
        require(port in 1..65535) { "port out of range: $port" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
    }

    val baseUrl: String = url.trimEnd('/')

    private companion object {
        const val DEFAULT_USERNAME = "opencode"
    }
}

/**
 * Incremental parser for the `opencode serve` readiness output.
 *
 * Fed one line at a time from the supervised process reader thread. Tolerates
 * unrelated log/ANSI noise and only completes once both the listening URL and the
 * password have been seen. A non-loopback URL is rejected: PocketDev always binds
 * the agent server to localhost and never exposes it.
 */
class OpenCodeServeOutputParser {

    /** Set when the server printed a URL that is not a loopback address. */
    var error: String? = null
        private set

    private var url: String? = null
    private var password: String? = null
    private var connectionValue: OpenCodeServerConnection? = null

    /** The completed connection, or null until both lines have been parsed. */
    val connection: OpenCodeServerConnection? get() = connectionValue

    /**
     * Consumes one stdout/stderr line. Returns the completed connection the moment
     * it becomes available, otherwise null.
     */
    fun onLine(rawLine: String): OpenCodeServerConnection? {
        if (connectionValue != null) return null
        val line = stripAnsi(rawLine).trim()
        if (line.isEmpty()) return null

        val listenPrefix = "server listening on "
        val passwordPrefix = "server password "
        when {
            line.contains(listenPrefix) -> {
                val candidate = line.substringAfterLast(listenPrefix).trim()
                val parsed = parseUrl(candidate)
                if (parsed == null) {
                    error = "server reported an unusable URL: $candidate"
                } else {
                    url = parsed
                }
            }

            line.contains(passwordPrefix) -> {
                val candidate = line.substringAfterLast(passwordPrefix).trim()
                if (candidate.isNotEmpty()) password = candidate
            }
        }

        val currentUrl = url ?: return null
        val currentPassword = password ?: return null
        val uri = try {
            URI(currentUrl)
        } catch (e: java.net.URISyntaxException) {
            error = "server reported a malformed URL: $currentUrl"
            return null
        }
        if (uri.host !in LOOPBACK_HOSTS) {
            error = "refusing non-loopback server URL: $currentUrl"
            return null
        }
        return OpenCodeServerConnection(
            url = currentUrl,
            port = uri.port.takeIf { it > 0 } ?: 80,
            password = currentPassword,
        ).also { connectionValue = it }
    }

    private fun parseUrl(candidate: String): String? =
        if (candidate.startsWith("http://")) candidate else null

    private fun stripAnsi(line: String): String {
        if (!line.contains('\u001B')) return line
        return ANSI_PATTERN.replace(line, "")
    }

    private companion object {
        val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1", "[::1]")
        // CSI sequences plus single-character ESC escapes.
        val ANSI_PATTERN = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B[@-~]")
    }
}

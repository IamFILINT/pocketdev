package dev.pocketdev.core.security

/**
 * Redaction helpers for anything that might reach logs or crash reports.
 *
 * Security rule (master prompt §17): never log API keys, passwords, tokens, or
 * signing material.
 */
object SecretRedaction {

    private const val MASK = "****"
    private val SENSITIVE_QUERY_KEYS = setOf(
        "api_key", "apikey", "key", "token", "access_token", "secret", "password", "sig", "signature",
    )

    /**
     * Redacts a secret, keeping only a tiny non-identifying prefix/suffix for
     * debugging purposes. Short secrets become fully masked.
     */
    fun redact(secret: String): String {
        if (secret.isEmpty()) return ""
        if (secret.length <= 8) return MASK
        return secret.take(3) + "…" + secret.takeLast(2)
    }

    /**
     * Removes values of sensitive query parameters from a URL, preserving the rest
     * of the URL so it can still be logged usefully.
     */
    fun redactUrl(url: String): String {
        if (url.isEmpty()) return url
        val queryIndex = url.indexOf('?')
        if (queryIndex == -1) return url
        val base = url.substring(0, queryIndex)
        val query = url.substring(queryIndex + 1)
        val redactedQuery = query.split('&').joinToString("&") { pair ->
            val key = pair.substringBefore('=')
            if (key.lowercase() in SENSITIVE_QUERY_KEYS && '=' in pair) {
                "$key=$MASK"
            } else {
                pair
            }
        }
        return "$base?$redactedQuery"
    }
}
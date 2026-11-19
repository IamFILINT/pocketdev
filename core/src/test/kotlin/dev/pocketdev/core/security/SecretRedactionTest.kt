package dev.pocketdev.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactionTest {

    @Test
    fun `empty secret stays empty`() {
        assertEquals("", SecretRedaction.redact(""))
    }

    @Test
    fun `short secrets are fully masked`() {
        assertEquals("****", SecretRedaction.redact("abc"))
        assertEquals("****", SecretRedaction.redact("12345678"))
    }

    @Test
    fun `long secrets keep only non-identifying edges`() {
        val redacted = SecretRedaction.redact("sk-abcdefghijklmnop")
        assertFalse(redacted.contains("abcdefghijklmnop"))
        assertEquals("sk-…op", redacted)
    }

    @Test
    fun `sensitive query parameters are redacted`() {
        val url = "https://api.example.test/v1/run?api_key=SUPERSECRET&model=x"
        val redacted = SecretRedaction.redactUrl(url)
        assertFalse(redacted.contains("SUPERSECRET"))
        assertEquals("https://api.example.test/v1/run?api_key=****&model=x", redacted)
    }

    @Test
    fun `urls without query are unchanged`() {
        assertEquals("https://example.test/path", SecretRedaction.redactUrl("https://example.test/path"))
    }

    @Test
    fun `non-sensitive parameters are preserved`() {
        val url = "http://127.0.0.1:4096/session?limit=10"
        assertEquals(url, SecretRedaction.redactUrl(url))
    }
}
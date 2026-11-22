package dev.pocketdev.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenCodeServeOutputParserTest {

    @Test
    fun parsesDocumentedV232Output() {
        val parser = OpenCodeServeOutputParser()
        assertNull(parser.onLine("opencode v2.3.2"))
        assertNull(parser.onLine("server listening on http://127.0.0.1:4096"))
        val connection = parser.onLine("server password abcd-efgh_ijkl")

        assertNotNull(connection)
        assertEquals("http://127.0.0.1:4096", connection!!.url)
        assertEquals(4096, connection.port)
        assertEquals("opencode", connection.username)
        assertEquals("abcd-efgh_ijkl", connection.password)
    }

    @Test
    fun passwordBeforeUrlStillCompletes() {
        val parser = OpenCodeServeOutputParser()
        parser.onLine("server password secret")
        val connection = parser.onLine("server listening on http://localhost:5173")
        assertNotNull(connection)
        assertEquals(5173, connection!!.port)
    }

    @Test
    fun ignoresAnsiNoiseAndLogLines() {
        val parser = OpenCodeServeOutputParser()
        parser.onLine("\u001B[32mready\u001B[0m")
        parser.onLine("some unrelated log line")
        parser.onLine("server listening on http://127.0.0.1:4096")
        val connection = parser.onLine("server password pw123")
        assertNotNull(connection)
        assertEquals("pw123", connection!!.password)
    }

    @Test
    fun incompleteUntilBothLinesSeen() {
        val parser = OpenCodeServeOutputParser()
        parser.onLine("server listening on http://127.0.0.1:4096")
        assertNull(parser.connection)
        assertNull(parser.error)
    }

    @Test
    fun refusesNonLoopbackUrl() {
        val parser = OpenCodeServeOutputParser()
        parser.onLine("server listening on http://192.168.1.20:4096")
        parser.onLine("server password pw123")
        assertNull(parser.connection)
        assertNotNull(parser.error)
    }

    @Test
    fun returnsConnectionOnlyOnce() {
        val parser = OpenCodeServeOutputParser()
        parser.onLine("server listening on http://127.0.0.1:4096")
        val first = parser.onLine("server password pw123")
        val second = parser.onLine("server password pw123")
        assertNotNull(first)
        assertNull(second)
    }
}

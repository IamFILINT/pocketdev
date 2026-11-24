package dev.pocketdev.core.agent

import com.sun.net.httpserver.HttpServer
import dev.pocketdev.core.settings.AgentModelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.io.ByteArrayInputStream

class OpenCodeApiClientTest {

    private class CapturingTransport : HttpTransport {
        val requests = CopyOnWriteArrayList<HttpRequest>()
        var response: HttpResponse = HttpResponse(200, "{}")

        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            return response
        }
    }

    private fun basicAuth(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))

    @Test
    fun sendsBasicAuthWithFixedUsername() {
        val transport = CapturingTransport()
        val client = OpenCodeApiClient("http://127.0.0.1:4096", "secret-pw", transport = transport)

        client.info()

        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("http://127.0.0.1:4096/api/info", request.url)
        assertEquals(basicAuth("opencode", "secret-pw"), request.headers["Authorization"])
    }

    @Test
    fun infoParsesServerInfo() {
        val transport = CapturingTransport().apply {
            response = HttpResponse(
                200,
                """{"version":"2.3.2","pid":7,"hostname":null,"urls":["http://127.0.0.1:4096"]}""",
            )
        }
        val info = OpenCodeApiClient("http://127.0.0.1:4096/", "pw", transport = transport).info()

        assertEquals("2.3.2", info.version)
        assertEquals(7L, info.pid)
        assertNull(info.hostname)
        assertEquals(listOf("http://127.0.0.1:4096"), info.urls)
    }

    @Test
    fun listSessionsProjectsStableFields() {
        val transport = CapturingTransport().apply {
            response = HttpResponse(
                200,
                """{"data":[{"id":"ses_a","title":"Hello","parentID":null},{"id":"ses_b"}],"cursor":{"next":null,"previous":null}}""",
            )
        }
        val sessions = OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport).listSessions()

        assertEquals(listOf("ses_a", "ses_b"), sessions.map { it.id })
        assertEquals("Hello", sessions[0].title)
        assertNull(sessions[1].title)
    }

    @Test
    fun createSessionSendsTitleJson() {
        val transport = CapturingTransport().apply {
            response = HttpResponse(200, """{"data":{"id":"ses_new","title":"My session","parentID":null}}""")
        }
        val session = OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
            .createSession(title = "My session")

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertTrue(request.body!!.toString(StandardCharsets.UTF_8).contains("\"My session\""))
        assertEquals("ses_new", session.id)
    }

    @Test
    fun createSessionSendsSelectedProviderAndModel() {
        val transport = CapturingTransport().apply {
            response = HttpResponse(200, """{"data":{"id":"ses_model","title":null,"parentID":null}}""")
        }
        val session = OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
            .createSession(model = AgentModelSelection("acme", "coder"))

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "{\"model\":{\"providerID\":\"acme\",\"id\":\"coder\"}}",
            request.body!!.toString(StandardCharsets.UTF_8),
        )
        assertEquals("ses_model", session.id)
    }

    @Test
    fun switchModelPostsWireShapeForActiveSession() {
        val transport = CapturingTransport().apply { response = HttpResponse(200, """{"data":{}}""") }
        OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
            .switchModel("ses a/b", AgentModelSelection("acme", "coder", "fast"))

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("http://127.0.0.1:4096/api/session/ses+a%2Fb/model", request.url)
        assertEquals(
            "{\"model\":{\"providerID\":\"acme\",\"id\":\"coder\",\"variant\":\"fast\"}}",
            request.body!!.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun promptEscapesTextAndEncodesSessionId() {
        val transport = CapturingTransport().apply { response = HttpResponse(200, """{"data":{}}""") }
        OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
            .prompt("ses a/b", "line1\nline2 \"quoted\"")

        val request = transport.requests.single()
        assertEquals("http://127.0.0.1:4096/api/session/ses+a%2Fb/prompt", request.url)
        val body = request.body!!.toString(StandardCharsets.UTF_8)
        assertTrue(body.contains("\\n"))
        assertTrue(body.contains("\\\"quoted\\\""))
    }

    @Test
    fun replyPermissionPostsDecisionToPermissionEndpoint() {
        val transport = CapturingTransport().apply { response = HttpResponse(204, "") }
        OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
            .replyPermission("ses 1", "per/1", PermissionDecision.ALWAYS)

        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals(
            "http://127.0.0.1:4096/api/session/ses+1/permission/per%2F1/reply",
            request.url,
        )
        assertEquals(
            "{\"decision\":\"always\"}",
            request.body!!.toString(StandardCharsets.UTF_8),
        )
    }

    @Test(expected = OpenCodeApiException::class)
    fun non2xxThrowsWithStatus() {
        val transport = CapturingTransport().apply { response = HttpResponse(401, """{"message":"Authentication required"}""") }
        OpenCodeApiClient("http://127.0.0.1:4096", "wrong", transport = transport).info()
    }

    @Test
    fun worksAgainstRealHttpServer() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/api/info") { exchange ->
                val auth = exchange.requestHeaders.getFirst("Authorization")
                if (auth != basicAuth("opencode", "real-pw")) {
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                val body = """{"version":"2.3.2","pid":1,"urls":["http://127.0.0.1"]}"""
                    .toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            val port = server.address.port

            val client = OpenCodeApiClient("http://127.0.0.1:$port", "real-pw")
            val info = client.info()

            assertEquals("2.3.2", info.version)
            assertEquals(1L, info.pid)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun streamEventsParsesSseAndStopsWhenHandlerReturnsFalse() {
        val body = "event: update\ndata: first\n\nevent: update\ndata: second\n\n"
        val transport = object : HttpTransport {
            override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200, "")
            override fun openStream(request: HttpRequest): java.io.InputStream =
                ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
        }
        val client = OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
        val received = mutableListOf<ServerSentEvent>()
        val delivered = client.streamEvents { ev ->
            received.add(ev)
            received.size < 1 // stop after first
        }
        assertEquals(1, delivered)
        assertEquals(1, received.size)
        assertEquals("first", received[0].data)
        assertEquals("update", received[0].event)
    }

    @Test
    fun streamEventsDeliversAllWhenHandlerAlwaysTrue() {
        val body = "data: a\n\ndata: b\n\ndata: c\n"
        val transport = object : HttpTransport {
            override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200, "")
            override fun openStream(request: HttpRequest): java.io.InputStream =
                ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
        }
        val client = OpenCodeApiClient("http://127.0.0.1:4096", "pw", transport = transport)
        val received = mutableListOf<String>()
        val delivered = client.streamEvents { ev -> received.add(ev.data); true }
        assertEquals(3, delivered)
        assertEquals(listOf("a", "b", "c"), received)
    }
}

package dev.pocketdev

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.agent.OpenCodeApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LoopbackHttpTest {

    @Test
    fun openCodeApiClientUsesRealAndroidHttpTransportToLoopback() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val requestPath = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        val serverFailure = AtomicReference<Throwable>()
        val served = CountDownLatch(1)

        val serverThread = Thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 15_000
                    val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                    val requestLine = reader.readLine().orEmpty()
                    requestPath.set(requestLine.split(' ').getOrNull(1))
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Authorization:", ignoreCase = true)) {
                            authorization.set(line.substringAfter(':').trim())
                        }
                    }

                    val body =
                        """{"version":"test-version","pid":1234,"hostname":"loopback","urls":["http://127.0.0.1:$port"]}"""
                    val writer = socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: application/json\r\n")
                    writer.write("Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(body)
                    writer.flush()
                }
            } catch (error: Throwable) {
                serverFailure.set(error)
            } finally {
                served.countDown()
            }
        }
        serverThread.start()

        try {
            val client = OpenCodeApiClient("http://127.0.0.1:$port", "test-password")
            val info = client.info()

            assertTrue("loopback server did not finish", served.await(20, TimeUnit.SECONDS))
            serverFailure.get()?.let { throw AssertionError("loopback server failed", it) }
            assertEquals("test-version", info.version)
            assertEquals(1234L, info.pid)
            assertEquals("loopback", info.hostname)
            assertEquals(listOf("http://127.0.0.1:$port"), info.urls)
            assertEquals("/api/info", requestPath.get())
            assertEquals(
                "Basic " + Base64.getEncoder()
                    .encodeToString("opencode:test-password".toByteArray(StandardCharsets.UTF_8)),
                authorization.get(),
            )
        } finally {
            server.close()
            serverThread.join(5_000)
        }
    }
}

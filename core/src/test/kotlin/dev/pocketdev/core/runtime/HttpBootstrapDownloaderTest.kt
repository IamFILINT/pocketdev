package dev.pocketdev.core.runtime

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress

class HttpBootstrapDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private var server: HttpServer? = null

    @After
    fun stopServer() {
        server?.stop(0)
    }

    private fun startServer(ignoreRange: Boolean = false): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/bootstrap.zip") { exchange -> serve(exchange, ignoreRange) }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/bootstrap.zip"
    }

    private fun serve(exchange: HttpExchange, ignoreRange: Boolean) {
        val range = exchange.requestHeaders.getFirst("Range")
        if (!ignoreRange && range != null && range.startsWith("bytes=")) {
            val start = range.removePrefix("bytes=").removeSuffix("-").toInt()
            val body = payload.copyOfRange(start, payload.size)
            exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
            exchange.sendResponseHeaders(206, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } else {
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
    }

    @Test
    fun `fresh download writes the whole payload and reports progress`() {
        val url = startServer()
        val destination = File(tmp.root, "bootstrap.zip")
        var lastProgress = 0L

        HttpBootstrapDownloader().downloadFile(url, destination, 0) { lastProgress = it }

        assertArrayEquals(payload, destination.readBytes())
        assertEquals(payload.size.toLong(), lastProgress)
    }

    @Test
    fun `resume appends from the offset without re-reading the prefix`() {
        val url = startServer()
        val destination = File(tmp.root, "bootstrap.zip")
        File(tmp.root, "bootstrap.zip").writeBytes(payload.copyOfRange(0, 1000))
        var lastProgress = 0L

        HttpBootstrapDownloader().downloadFile(url, destination, 1000) { lastProgress = it }

        assertArrayEquals(payload, destination.readBytes())
        assertEquals(payload.size.toLong(), lastProgress)
    }

    @Test
    fun `server that ignores Range is rejected instead of mixing responses`() {
        val url = startServer(ignoreRange = true)
        val destination = File(tmp.root, "bootstrap.zip")
        File(tmp.root, "bootstrap.zip").writeBytes(payload.copyOfRange(0, 1000))

        assertThrows(IOException::class.java) {
            HttpBootstrapDownloader().downloadFile(url, destination, 1000) { }
        }
    }

    @Test
    fun `non-https url is refused`() {
        val destination = File(tmp.root, "bootstrap.zip")
        assertThrows(IOException::class.java) {
            HttpBootstrapDownloader().downloadFile("http://example.test/x.zip", destination, 0) { }
        }
    }

    @Test
    fun `http error status fails loudly`() {
        val url = startServer().replace("/bootstrap.zip", "/missing.zip")
        assertThrows(IOException::class.java) {
            HttpBootstrapDownloader().downloadFile(url, File(tmp.root, "x.zip"), 0) { }
        }
    }
}

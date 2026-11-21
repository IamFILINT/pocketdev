package dev.pocketdev.core.runtime

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP(S) downloader with byte-range resume.
 *
 * Only the pinned `https://` URL is ever requested (enforced by [BootstrapArtifact]).
 * A server that ignores a resume `Range` request is rejected instead of silently
 * restarting the file, so a resumed archive can never be a mix of two responses.
 */
class HttpBootstrapDownloader(
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 60_000,
) : BootstrapDownloader {

    override fun download(
        artifact: BootstrapArtifact,
        destination: File,
        offset: Long,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        downloadFrom(artifact.downloadUrl, destination, offset, onProgress)
    }

    /**
     * @throws IOException on any HTTP/network failure. The partial file is left in
     * place so the next run can resume.
     */
    internal fun downloadFrom(
        url: String,
        destination: File,
        offset: Long,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        if (!url.startsWith("https://") && !url.startsWith("http://127.0.0.1")) {
            throw IOException("refusing non-HTTPS download URL")
        }
        val connection = (URL(url).openConnection() as? HttpURLConnection)
            ?: throw IOException("not an HTTP URL: $url")
        connection.instanceFollowRedirects = true
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty("User-Agent", "PocketDev-bootstrap/1")
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = connection.responseCode
            if (offset > 0 && code == HttpURLConnection.HTTP_OK) {
                throw IOException("server ignored the Range request; refusing to mix responses")
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP $code for $url")
            }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                destOutputStream(destination, offset).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = offset
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun destOutputStream(destination: File, offset: Long): java.io.OutputStream =
        try {
            java.io.FileOutputStream(destination, offset > 0)
        } catch (e: FileNotFoundException) {
            destination.parentFile?.let { it.mkdirs() }
            java.io.FileOutputStream(destination, offset > 0)
        }
}

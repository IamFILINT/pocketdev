package dev.pocketdev.runtime

import dev.pocketdev.core.agent.AgentDownloader
import dev.pocketdev.core.runtime.HttpBootstrapDownloader
import java.io.File

/** Reuses the bootstrap downloader's HTTPS-only, byte-range implementation. */
class HttpAgentDownloader : AgentDownloader {

    private val http = HttpBootstrapDownloader()

    override fun download(
        url: String,
        destination: File,
        offset: Long,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        http.downloadFile(url, destination, offset, onProgress)
    }
}

package dev.pocketdev.core.agent

import dev.pocketdev.core.runtime.BootstrapChecksums
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Lifecycle of the provisioned AI agent binary. */
sealed interface AgentState {
    data object NotInstalled : AgentState

    data class Downloading(val bytesReceived: Long, val totalBytes: Long) : AgentState {
        val fraction: Float
            get() = if (totalBytes <= 0L) 0f
            else (bytesReceived.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
    }

    data object Verifying : AgentState

    data class Ready(val version: String) : AgentState

    data class Failed(val message: String) : AgentState
}

/** Resumable HTTPS downloader for agent artifacts; implemented in `:runtime`. */
fun interface AgentDownloader {
    fun download(
        url: String,
        destination: File,
        offset: Long,
        onProgress: (bytesDownloaded: Long) -> Unit,
    )
}

/**
 * Downloads, checksum-verifies and installs a self-contained agent binary.
 *
 * Fail-closed rules mirror the bootstrap manager: a size or checksum mismatch
 * never replaces an existing binary, and the partial file is deleted so a corrupted
 * download cannot be resumed into a mixed artifact.
 */
class AgentInstaller(
    private val installDir: File,
    private val downloader: AgentDownloader,
    private val onState: (AgentState) -> Unit = {},
) {

    val binary: File = File(installDir, "opencode")
    private val partial: File = File(installDir, "opencode.part")

    fun isInstalled(artifact: AgentArtifact): Boolean =
        binary.isFile &&
            binary.length() == artifact.sizeBytes &&
            BootstrapChecksums.matches(artifact.sha256, BootstrapChecksums.sha256(binary))

    /** Blocking: call off the main thread. */
    fun install(artifact: AgentArtifact): AgentState {
        fun emit(state: AgentState): AgentState {
            onState(state)
            return state
        }
        fun fail(message: String): AgentState = emit(AgentState.Failed(message))

        if (isInstalled(artifact)) return emit(AgentState.Ready(artifact.upstreamVersion))

        emit(AgentState.Downloading(0, artifact.sizeBytes))
        try {
            Files.createDirectories(installDir.toPath())
            if (partial.length() > artifact.sizeBytes) partial.delete()
            if (partial.length() < artifact.sizeBytes) {
                downloader.download(artifact.downloadUrl, partial, partial.length()) { received ->
                    emit(AgentState.Downloading(received, artifact.sizeBytes))
                }
            }
            if (partial.length() != artifact.sizeBytes) {
                return fail("downloaded ${partial.length()} bytes, expected ${artifact.sizeBytes}")
            }

            emit(AgentState.Verifying)
            val actual = BootstrapChecksums.sha256(partial)
            if (!BootstrapChecksums.matches(artifact.sha256, actual)) {
                partial.delete()
                return fail("checksum mismatch: expected ${artifact.sha256}, got $actual (artifact deleted)")
            }

            move(partial, binary)
            if (!binary.setExecutable(true, true)) {
                return fail("could not mark ${binary.name} executable")
            }
            return emit(AgentState.Ready(artifact.upstreamVersion))
        } catch (e: Exception) {
            return fail(e.message ?: e::class.java.simpleName)
        }
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

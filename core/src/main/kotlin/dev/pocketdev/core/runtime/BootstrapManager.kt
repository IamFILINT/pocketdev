package dev.pocketdev.core.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Byte-range capable downloader for a pinned artifact. Implementations live in the
 * Android module; `:core` stays testable with fakes.
 *
 * [offset] may be non-zero after an interrupted run; the implementation must append
 * from that byte offset (HTTP `Range`) and never truncate the existing bytes.
 */
fun interface BootstrapDownloader {
    fun download(
        artifact: BootstrapArtifact,
        destination: File,
        offset: Long,
        onProgress: (bytesDownloaded: Long) -> Unit,
    )
}

/**
 * Resumable, idempotent bootstrap manager (prompt §8.4).
 *
 * Steps: device check → storage check → already-installed check → resumable
 * download → checksum verify → safe extraction into `usr.staging` → structural
 * health check → atomic-ish swap with rollback → journal → `Ready`.
 *
 * Safety properties enforced here:
 * - a healthy, completed runtime is never overwritten silently;
 * - a checksum mismatch deletes the artifact and fails closed, before `$PREFIX` is
 *   touched;
 * - extraction happens in staging, and a failed post-swap health check restores
 *   `usr.prev`;
 * - progress is journaled so a killed process resumes instead of restarting.
 */
class BootstrapManager(
    private val filesDir: File,
    private val cacheDir: File,
    private val downloader: BootstrapDownloader,
    private val freeBytes: () -> Long = { Long.MAX_VALUE },
    private val health: PrefixHealthCheck = BootstrapHealth(),
    private val onState: (RuntimeState) -> Unit = {},
) {

    val journalStore: BootstrapJournalStore = BootstrapJournalStore(File(filesDir, "state/runtime.json"))
    val prefix: File = File(filesDir, "usr")
    val staging: File = File(filesDir, "usr.staging")
    val backup: File = File(filesDir, "usr.prev")

    fun provision(manifest: BootstrapManifest, abi: Abi, apiLevel: Int): RuntimeState {
        var stage = RuntimeState.Stage.CHECK
        fun emit(state: RuntimeState): RuntimeState {
            onState(state)
            return state
        }
        fun fail(message: String): RuntimeState = emit(RuntimeState.Failed(stage, message))

        emit(RuntimeState.Checking)
        try {
            val artifact = manifest.artifactFor(abi, apiLevel)
                ?: return fail("unsupported device: ${abi.ndkAbi} at API $apiLevel")

            val journal = journalStore.read()
            if (journal?.completed == true &&
                journal.version == artifact.releaseTag &&
                health.check(prefix).ok
            ) {
                return emit(RuntimeState.Ready(artifact.releaseTag))
            }

            val requiredBytes = artifact.sizeBytes * FREE_SPACE_FACTOR
            if (freeBytes() < requiredBytes) {
                return fail("insufficient storage: need $requiredBytes bytes")
            }

            stage = RuntimeState.Stage.DOWNLOAD
            Files.createDirectories(cacheDir.toPath())
            val part = File(cacheDir, "bootstrap-${artifact.sha256}.part")
            if (part.length() > artifact.sizeBytes) BootstrapArchive.deleteRecursively(part)
            var offset = part.length()
            if (offset > 0 && offset < artifact.sizeBytes) {
                emit(RuntimeState.Downloading(offset, artifact.sizeBytes))
            }
            if (offset < artifact.sizeBytes) {
                downloader.download(artifact, part, offset) { received ->
                    journalStore.write(
                        BootstrapJournal(artifact.releaseTag, RuntimeState.Stage.DOWNLOAD, artifact.sha256, received),
                    )
                    emit(RuntimeState.Downloading(received, artifact.sizeBytes))
                }
            }
            if (part.length() != artifact.sizeBytes) {
                return fail("downloaded ${part.length()} bytes, expected ${artifact.sizeBytes}")
            }

            stage = RuntimeState.Stage.VERIFY
            emit(RuntimeState.Verifying)
            val actual = BootstrapChecksums.sha256(part)
            if (!BootstrapChecksums.matches(artifact.sha256, actual)) {
                BootstrapArchive.deleteRecursively(part)
                return fail("checksum mismatch: expected ${artifact.sha256}, got $actual (artifact deleted)")
            }
            journalStore.write(
                BootstrapJournal(artifact.releaseTag, RuntimeState.Stage.VERIFY, artifact.sha256, part.length()),
            )

            stage = RuntimeState.Stage.EXTRACT
            BootstrapArchive.deleteRecursively(staging)
            BootstrapArchive.extract(part, staging) { done, total ->
                emit(RuntimeState.Extracting(done, total))
            }

            stage = RuntimeState.Stage.HEALTH_CHECK
            val stagedReport = health.check(staging)
            if (!stagedReport.ok) {
                BootstrapArchive.deleteRecursively(staging)
                return fail("staged prefix failed health checks: ${stagedReport.failureSummary()}")
            }

            swap(staging, prefix, backup)
            val report = health.check(prefix)
            if (!report.ok) {
                BootstrapArchive.deleteRecursively(prefix)
                if (backup.exists()) move(backup, prefix)
                return fail("installed prefix failed health checks, previous runtime restored: ${report.failureSummary()}")
            }
            BootstrapArchive.deleteRecursively(backup)
            BootstrapArchive.deleteRecursively(part)
            journalStore.write(
                BootstrapJournal(artifact.releaseTag, RuntimeState.Stage.HEALTH_CHECK, artifact.sha256, artifact.sizeBytes, completed = true),
            )
            return emit(RuntimeState.Ready(artifact.releaseTag))
        } catch (e: Exception) {
            return fail(e.message ?: e::class.java.simpleName)
        }
    }

    private fun swap(staging: File, prefix: File, backup: File) {
        BootstrapArchive.deleteRecursively(backup)
        val hadPrefix = prefix.exists()
        try {
            if (hadPrefix) move(prefix, backup)
            move(staging, prefix)
        } catch (e: Exception) {
            if (prefix.exists() && hadPrefix && backup.exists()) BootstrapArchive.deleteRecursively(prefix)
            if (hadPrefix && backup.exists()) move(backup, prefix)
            throw e
        }
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath())
        }
    }

    private companion object {
        const val FREE_SPACE_FACTOR = 4L
    }
}

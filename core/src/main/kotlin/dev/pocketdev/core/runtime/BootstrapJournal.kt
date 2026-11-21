package dev.pocketdev.core.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Durable progress record for the bootstrap manager (`files/state/runtime.json`).
 *
 * Values are written as `key=value` lines through a temp file + atomic move, so a
 * process death can never leave a half-written journal behind. A corrupt or
 * unknown journal is treated as absent — the manager then re-runs the safe,
 * idempotent path instead of trusting garbage.
 */
data class BootstrapJournal(
    val version: String? = null,
    val stage: RuntimeState.Stage = RuntimeState.Stage.CHECK,
    val sha256: String? = null,
    val bytesDownloaded: Long = 0,
    val completed: Boolean = false,
)

class BootstrapJournalStore(private val file: File) {

    fun read(): BootstrapJournal? {
        if (!file.isFile) return null
        return try {
            val values = file.readLines(Charsets.UTF_8)
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
                }
                .toMap()
            val stage = values["stage"]?.let { name -> RuntimeState.Stage.entries.firstOrNull { it.name == name } }
            BootstrapJournal(
                version = values["version"]?.takeIf { it.isNotBlank() },
                stage = stage ?: return null,
                sha256 = values["sha256"]?.takeIf { it.isNotBlank() },
                bytesDownloaded = values["bytes"]?.toLongOrNull() ?: 0,
                completed = values["completed"] == "true",
            )
        } catch (e: Exception) {
            null
        }
    }

    fun write(journal: BootstrapJournal) {
        file.parentFile?.let { Files.createDirectories(it.toPath()) }
        val content = buildString {
            appendLine("format=1")
            journal.version?.let { appendLine("version=$it") }
            appendLine("stage=${journal.stage.name}")
            journal.sha256?.let { appendLine("sha256=$it") }
            appendLine("bytes=${journal.bytesDownloaded}")
            appendLine("completed=${journal.completed}")
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

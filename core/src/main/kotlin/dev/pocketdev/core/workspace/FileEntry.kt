package dev.pocketdev.core.workspace

import java.io.File

enum class FileKind {
    DIRECTORY,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    ARCHIVE,
    BINARY,
}

data class FileEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val kind: FileKind,
) {
    val isHidden: Boolean get() = name.startsWith(".")

    companion object {
        fun from(root: File, file: File): FileEntry {
            val relative = root.canonicalFile.toPath()
                .relativize(file.canonicalFile.toPath())
                .toString()
                .replace(File.separatorChar, '/')
            val directory = file.isDirectory
            return FileEntry(
                name = file.name,
                relativePath = relative,
                isDirectory = directory,
                sizeBytes = if (directory) 0L else file.length(),
                lastModifiedEpochMs = file.lastModified(),
                kind = if (directory) FileKind.DIRECTORY else kindOf(file.name),
            )
        }

        private fun kindOf(name: String): FileKind = when (name.substringAfterLast('.').lowercase()) {
            "txt", "md", "json", "kt", "kts", "java", "xml", "yml", "yaml", "csv", "html", "css", "js", "ts", "sh", "py", "go", "rs", "c", "h", "cpp", "gradle", "properties" -> FileKind.TEXT
            "png", "jpg", "jpeg", "gif", "webp", "svg" -> FileKind.IMAGE
            "mp3", "wav", "ogg", "flac", "m4a" -> FileKind.AUDIO
            "mp4", "mkv", "webm", "mov" -> FileKind.VIDEO
            "zip", "tar", "gz", "bz2", "xz", "jar", "apk" -> FileKind.ARCHIVE
            else -> FileKind.BINARY
        }
    }
}

data class DirectoryListing(
    val relativePath: String,
    val entries: List<FileEntry>,
)

sealed interface WorkspaceResult<out T> {
    data class Success<out T>(val value: T) : WorkspaceResult<T>
    data class Failure(val message: String) : WorkspaceResult<Nothing>
    data class PartialFailure(val message: String, val completed: Long, val total: Long) : WorkspaceResult<Nothing>

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
        is PartialFailure -> null
    }
}

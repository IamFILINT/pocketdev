package dev.pocketdev.core.runtime

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.zip.ZipFile

/** Raised when an archive violates the extraction safety rules. */
class BootstrapArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Hard caps applied while extracting a bootstrap archive. They exist to turn a
 * malicious or corrupt archive into a fast, explicit failure instead of filling the
 * device.
 */
data class BootstrapLimits(
    val maxEntries: Int = 200_000,
    val maxEntryBytes: Long = 512L * 1024 * 1024,
    val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxSymlinks: Int = 10_000,
)

/**
 * Safe extractor for an audited Termux bootstrap archive.
 *
 * The archive layout is the prefix root (`bin/`, `lib/`, …) plus a `SYMLINKS.txt`
 * manifest with `target←relative/path` lines, matching upstream `TermuxInstaller`.
 * Rules enforced here (prompt §8.4 step 6):
 *
 * - entry names cannot be absolute, contain `..`, backslashes or NUL bytes;
 * - the destination must be empty, and every written path is resolved and checked
 *   to stay inside it;
 * - symlinks are never taken from the zip; they are recreated from
 *   `SYMLINKS.txt` only when both link and target stay inside the prefix;
 * - entry count and size caps prevent zip bombs;
 * - `bin/`, `libexec/`, `lib/apt/apt-helper` and `lib/apt/methods/` are made
 *   executable, mirroring upstream.
 */
object BootstrapArchive {

    const val SYMLINKS_FILE = "SYMLINKS.txt"

    private const val SYMLINK_SEPARATOR = '\u2190'
    private const val MAX_SYMLINKS_FILE_BYTES = 1_000_000L

    data class Result(
        val entries: Int,
        val symlinksCreated: Int,
        val bytesWritten: Long,
    )

    fun extract(
        archive: File,
        destination: File,
        limits: BootstrapLimits = BootstrapLimits(),
        onProgress: (completedEntries: Int, totalEntries: Int) -> Unit = { _, _ -> },
    ): Result {
        require(archive.isFile) { "archive does not exist: $archive" }
        val root = destination.canonicalFile
        if (root.exists() && root.list()?.isNotEmpty() == true) {
            throw BootstrapArchiveException("destination must be empty: $root")
        }
        Files.createDirectories(root.toPath())

        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            if (entries.size > limits.maxEntries) {
                throw BootstrapArchiveException("archive has ${entries.size} entries, limit ${limits.maxEntries}")
            }
            val declaredTotal = entries.sumOf { if (it.size > 0) it.size else 0L }
            if (declaredTotal > limits.maxTotalBytes) {
                throw BootstrapArchiveException("archive expands to $declaredTotal bytes, limit ${limits.maxTotalBytes}")
            }

            val seen = HashSet<String>(entries.size * 2)
            val symlinkLines = ArrayList<String>()

            entries.forEachIndexed { index, entry ->
                val relative = safeRelativePath(entry.name)
                if (!seen.add(relative)) {
                    throw BootstrapArchiveException("duplicate archive entry: $relative")
                }
                when {
                    entry.isDirectory -> Files.createDirectories(resolveInside(root, relative))

                    relative == SYMLINKS_FILE -> {
                        if (entry.size > MAX_SYMLINKS_FILE_BYTES) {
                            throw BootstrapArchiveException("$SYMLINKS_FILE is implausibly large: ${entry.size} bytes")
                        }
                        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { lines ->
                            lines.forEach { if (it.isNotBlank()) symlinkLines += it }
                        }
                    }

                    else -> {
                        if (entry.size < 0 || entry.size > limits.maxEntryBytes) {
                            throw BootstrapArchiveException("entry $relative has unsafe size ${entry.size}")
                        }
                        val target = resolveInside(root, relative)
                        Files.createDirectories(target.parent)
                        zip.getInputStream(entry).use { input ->
                            Files.newOutputStream(target).use { output -> input.copyTo(output) }
                        }
                    }
                }
                onProgress(index + 1, entries.size)
            }

            if (symlinkLines.size > limits.maxSymlinks) {
                throw BootstrapArchiveException("${symlinkLines.size} symlinks, limit ${limits.maxSymlinks}")
            }
            symlinkLines.forEach { createSymlink(root, it) }

            applyExecutableBits(root)
            return Result(
                entries = entries.size,
                symlinksCreated = symlinkLines.size,
                bytesWritten = declaredTotal,
            )
        }
    }

    internal fun safeRelativePath(name: String): String {
        if (name.isEmpty()) throw BootstrapArchiveException("empty archive entry name")
        if (name.contains('\u0000')) throw BootstrapArchiveException("NUL in archive entry name")
        if (name.contains('\\')) throw BootstrapArchiveException("backslash in archive entry name: $name")
        if (name.startsWith("/")) throw BootstrapArchiveException("absolute archive entry: $name")

        val segments = name.removeSuffix("/").split('/')
        segments.forEach { segment ->
            if (segment.isEmpty() || segment == "." || segment == "..") {
                throw BootstrapArchiveException("unsafe path segment in archive entry: $name")
            }
        }
        return segments.joinToString("/")
    }

    private fun resolveInside(root: File, relative: String): Path {
        val resolved = root.toPath().resolve(relative).normalize()
        if (!resolved.startsWith(root.toPath())) {
            throw BootstrapArchiveException("entry escapes destination: $relative")
        }
        return resolved
    }

    private fun createSymlink(root: File, line: String) {
        val separator = line.indexOf(SYMLINK_SEPARATOR)
        if (separator <= 0) {
            throw BootstrapArchiveException("malformed symlink line: $line")
        }
        val targetText = line.substring(0, separator).trim()
        val linkText = line.substring(separator + 1).trim().removePrefix("./")
        if (targetText.isEmpty() || linkText.isEmpty()) {
            throw BootstrapArchiveException("malformed symlink line: $line")
        }

        val linkPath = resolveInside(root, safeRelativePath(linkText))
        val linkParent = linkPath.parent ?: root.toPath()
        val resolvedTarget = linkParent.resolve(targetText).normalize()
        if (!resolvedTarget.startsWith(root.toPath())) {
            throw BootstrapArchiveException("symlink target escapes destination: $line")
        }
        if (Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
            throw BootstrapArchiveException("symlink path already exists: $linkText")
        }
        Files.createDirectories(linkParent)
        try {
            Files.createSymbolicLink(linkPath, Paths.get(targetText))
        } catch (e: UnsupportedOperationException) {
            throw BootstrapArchiveException("filesystem does not support symlinks", e)
        }
    }

    private fun applyExecutableBits(root: File) {
        markTreeExecutable(File(root, "bin").toPath())
        markTreeExecutable(File(root, "libexec").toPath())
        markTreeExecutable(File(root, "lib/apt/methods").toPath())
        File(root, "lib/apt/apt-helper").takeIf { it.isFile }?.setExecutable(true, true)
    }

    private fun markTreeExecutable(directory: Path) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(
            directory,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!Files.isSymbolicLink(file)) makeExecutable(file)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun makeExecutable(file: Path) {
        try {
            val permissions = EnumSet.copyOf(Files.getPosixFilePermissions(file))
            permissions += PosixFilePermission.OWNER_READ
            permissions += PosixFilePermission.OWNER_WRITE
            permissions += PosixFilePermission.OWNER_EXECUTE
            Files.setPosixFilePermissions(file, permissions)
        } catch (e: UnsupportedOperationException) {
            file.toFile().setExecutable(true, true)
        }
    }

    internal fun deleteRecursively(file: File) {
        if (!file.exists()) return
        file.walkBottomUp().forEach { it.delete() }
    }
}

package dev.pocketdev.core.workspace

import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.NewlineStyle
import dev.pocketdev.core.editor.TextEncoding
import dev.pocketdev.core.editor.TextFileCodec
import dev.pocketdev.core.editor.TextFileSnapshot
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class WorkspaceFiles(private val root: File) {

    private val canonicalRoot: File = root.canonicalFile

    suspend fun list(
        relativeDirectory: String = "",
        showHidden: Boolean = false,
    ): WorkspaceResult<DirectoryListing> =
        wrapped {
            currentCoroutineContext().ensureActive()
            val directory = resolve(relativeDirectory)
            require(directory.isDirectory) { "Not a directory" }
            val entries = (directory.listFiles() ?: throw IOException("Could not read directory"))
                .asSequence()
                .filter { !Files.isSymbolicLink(it.toPath()) }
                .map { FileEntry.from(canonicalRoot, it) }
                .filter { showHidden || !it.isHidden }
                .toList()
                .sortedWith(ENTRY_ORDER)
            DirectoryListing(normalize(relativeDirectory), entries)
        }

    suspend fun create(
        relativeDirectory: String,
        name: String,
        directory: Boolean,
    ): WorkspaceResult<String> =
        wrapped {
            currentCoroutineContext().ensureActive()
            requireValidName(name)
            val parent = resolve(relativeDirectory)
            require(parent.isDirectory) { "Not a directory" }
            val target = File(parent, name)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            val created = if (directory) target.mkdirs() else target.createNewFile()
            require(created) { "Could not create entry" }
            relativePathOf(target)
        }

    suspend fun rename(relativePath: String, newName: String): WorkspaceResult<String> =
        wrapped {
            currentCoroutineContext().ensureActive()
            requireValidName(newName)
            val source = resolveExisting(relativePath)
            val target = File(source.parentFile, newName)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            Files.move(source.toPath(), target.toPath())
            relativePathOf(target)
        }

    suspend fun delete(
        relativePath: String,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
    ): WorkspaceResult<Unit> =
        wrapped {
            val source = resolveExisting(relativePath)
            require(source.canonicalFile != canonicalRoot) { "Cannot delete project root" }
            val plan = collectDeletePlan(source)
            val total = plan.size.toLong()
            var completed = 0L
            try {
                for (file in plan.asReversed()) {
                    currentCoroutineContext().ensureActive()
                    require(Files.deleteIfExists(file.toPath())) { "Could not delete entry" }
                    completed++
                    onProgress(completed, total)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw PartialWorkspaceException(error.message ?: "Could not delete entry", completed, total)
            }
        }

    suspend fun copy(
        relativePath: String,
        targetDirectory: String,
        newName: String? = null,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
    ): WorkspaceResult<String> =
        wrapped {
            val source = resolveExisting(relativePath)
            require(source.canonicalFile != canonicalRoot) { "Cannot copy project root" }
            val name = newName ?: source.name
            requireValidName(name)
            val destinationDirectory = resolve(targetDirectory)
            require(destinationDirectory.isDirectory) { "Not a directory" }
            val destination = File(destinationDirectory, name)
            require(!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            require(!destination.canonicalFile.path.startsWith(source.canonicalFile.path + File.separator)) {
                "Cannot copy into itself"
            }
            val plan = collectCopyPlan(source, destination)
            val total = plan.size.toLong()
            var completed = 0L
            try {
                for ((entry, target) in plan) {
                    currentCoroutineContext().ensureActive()
                    if (entry.isDirectory) {
                        require(target.isDirectory || target.mkdirs()) { "Could not create directory" }
                    } else {
                        target.parentFile?.mkdirs()
                        copyFile(entry, target)
                    }
                    completed++
                    onProgress(completed, total)
                }
            } catch (cancelled: CancellationException) {
                destination.deleteRecursively()
                throw cancelled
            } catch (error: Exception) {
                destination.deleteRecursively()
                throw PartialWorkspaceException(error.message ?: "Could not copy entry", completed, total)
            }
            relativePathOf(destination)
        }

    suspend fun move(
        relativePath: String,
        targetDirectory: String,
        newName: String? = null,
    ): WorkspaceResult<String> =
        wrapped {
            currentCoroutineContext().ensureActive()
            val source = resolveExisting(relativePath)
            require(source.canonicalFile != canonicalRoot) { "Cannot move project root" }
            val name = newName ?: source.name
            requireValidName(name)
            val destinationDirectory = resolve(targetDirectory)
            require(destinationDirectory.isDirectory) { "Not a directory" }
            val destination = File(destinationDirectory, name)
            require(!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            require(!destination.canonicalFile.path.startsWith(source.canonicalFile.path + File.separator)) {
                "Cannot move into itself"
            }
            Files.move(source.toPath(), destination.toPath())
            relativePathOf(destination)
        }

    suspend fun destination(relativeDirectory: String, name: String): WorkspaceResult<File> =
        wrapped {
            requireValidName(name)
            val directory = resolve(relativeDirectory)
            require(directory.isDirectory) { "Not a directory" }
            val target = File(directory, name)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            target
        }

    suspend fun source(relativePath: String): WorkspaceResult<File> = wrapped { resolveExisting(relativePath) }

    suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long = 0,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
    ): WorkspaceResult<Long> =
        wrapped {
            var copied = 0L
            val buffer = ByteArray(64 * 1024)
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress(copied, totalBytes.coerceAtLeast(0))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw PartialWorkspaceException(error.message ?: "Could not copy stream", copied, totalBytes.coerceAtLeast(0))
            }
            copied
        }

    suspend fun search(
        query: String,
        showHidden: Boolean = false,
        maxResults: Int = 200,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
    ): WorkspaceResult<List<FileEntry>> =
        wrapped {
            val needle = query.trim()
            if (needle.isEmpty() || maxResults <= 0) return@wrapped emptyList()
            val matches = mutableListOf<FileEntry>()
            val stack = ArrayDeque<Pair<File, Int>>()
            stack.add(canonicalRoot to 0)
            while (stack.isNotEmpty() && matches.size < maxResults) {
                currentCoroutineContext().ensureActive()
                val (directory, depth) = stack.removeLast()
                val children = directory.listFiles() ?: continue
                for (child in children) {
                    if (Files.isSymbolicLink(child.toPath())) continue
                    val entry = FileEntry.from(canonicalRoot, child)
                    if (!showHidden && entry.isHidden) continue
                    if (entry.name.contains(needle, ignoreCase = true)) {
                        matches.add(entry)
                        onProgress(matches.size.toLong(), 0)
                    }
                    if (child.isDirectory && depth < MAX_SEARCH_DEPTH) {
                        stack.add(child to depth + 1)
                    }
                    if (matches.size >= maxResults) break
                }
            }
            matches.sortedWith(ENTRY_ORDER)
        }

    suspend fun preview(relativePath: String, maxBytes: Int = 16_384): WorkspaceResult<String?> =
        wrapped {
            currentCoroutineContext().ensureActive()
            val source = resolveExisting(relativePath)
            require(source.isFile) { "Not a file" }
            val bytes = ByteArray(maxBytes.coerceAtLeast(1))
            val read = Files.newInputStream(source.toPath()).use { it.read(bytes) }
            if (read <= 0 || bytes.copyOf(read).any { it == 0.toByte() }) return@wrapped null
            String(bytes, 0, read, StandardCharsets.UTF_8)
        }

    suspend fun readTextFile(
        relativePath: String,
        maxBytes: Int = DEFAULT_MAX_TEXT_BYTES,
    ): WorkspaceResult<TextFileSnapshot> =
        wrapped {
            currentCoroutineContext().ensureActive()
            val source = resolveExisting(relativePath)
            require(source.isFile) { "Not a file" }
            require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links cannot be edited" }
            val size = source.length()
            val length = minOf(size, maxBytes.coerceAtLeast(1).toLong()).toInt()
            val bytes = ByteArray(length)
            var read = 0
            Files.newInputStream(source.toPath()).use { input ->
                while (read < length) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes, read, length - read)
                    if (count < 0) break
                    read += count
                }
            }
            val payload = if (read == length) bytes else bytes.copyOf(read)
            TextFileCodec.decode(
                bytes = payload,
                sizeBytes = size,
                lastModifiedEpochMs = source.lastModified(),
                truncated = size > length,
            ) ?: throw IOException("Binary file cannot be edited")
        }

    suspend fun writeTextFile(
        relativePath: String,
        text: String,
        encoding: TextEncoding,
        newlineStyle: NewlineStyle,
        expectedSignature: FileSignature? = null,
    ): WorkspaceResult<FileSignature> =
        wrapped {
            currentCoroutineContext().ensureActive()
            val target = resolveExisting(relativePath)
            require(target.isFile) { "Not a file" }
            require(!Files.isSymbolicLink(target.toPath())) { "Symbolic links cannot be edited" }
            if (expectedSignature != null) {
                val current = FileSignature(target.length(), target.lastModified())
                require(current == expectedSignature) { "File changed on disk" }
            }
            Files.write(target.toPath(), TextFileCodec.encode(text, encoding, newlineStyle))
            FileSignature(target.length(), target.lastModified())
        }

    private fun resolve(relativeDirectory: String): File {
        if (relativeDirectory.isBlank()) return canonicalRoot
        return PathSafety.resolveWithin(canonicalRoot, relativeDirectory)
            ?: throw IllegalArgumentException("Unsafe path")
    }

    private fun resolveExisting(relativePath: String): File {
        require(relativePath.isNotBlank()) { "Path must not be blank" }
        val file = resolve(relativePath)
        require(Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Not found" }
        return file
    }

    private fun requireValidName(name: String) {
        require(name.isNotBlank() && name !in setOf(".", "..")) { "Invalid name" }
        require(name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Invalid name" }
        require(PathSafety.isSafeRelativePath(name)) { "Invalid name" }
    }

    private fun relativePathOf(file: File): String =
        canonicalRoot.toPath().relativize(file.canonicalFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')

    private fun normalize(relativeDirectory: String): String =
        relativeDirectory.replace('\\', '/').trim('/')

    private fun collectDeletePlan(source: File): List<File> {
        val plan = mutableListOf<File>()
        fun walk(file: File) {
            plan.add(file)
            if (Files.isSymbolicLink(file.toPath())) return
            if (file.isDirectory) {
                file.listFiles()?.forEach(::walk) ?: throw IOException("Could not read directory")
            }
        }
        walk(source)
        return plan
    }

    private fun collectCopyPlan(source: File, destination: File): List<Pair<File, File>> {
        val plan = mutableListOf<Pair<File, File>>()
        fun walk(file: File, target: File) {
            if (Files.isSymbolicLink(file.toPath())) return
            plan.add(file to target)
            if (file.isDirectory) {
                file.listFiles()?.forEach { child -> walk(child, File(target, child.name)) }
                    ?: throw IOException("Could not read directory")
            }
        }
        walk(source, destination)
        return plan
    }

    private suspend fun copyFile(source: File, target: File) {
        Files.newInputStream(source.toPath()).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private suspend inline fun <T> wrapped(block: suspend () -> T): WorkspaceResult<T> =
        runCatching { block() }.fold(
            onSuccess = { WorkspaceResult.Success(it) },
            onFailure = { error ->
                when (error) {
                    is CancellationException -> throw error
                    is PartialWorkspaceException -> WorkspaceResult.PartialFailure(
                        error.message ?: "Partial operation failed",
                        error.completed,
                        error.total,
                    )
                    else -> WorkspaceResult.Failure(error.message ?: error::class.simpleName ?: "Failed")
                }
            },
        )

    private class PartialWorkspaceException(
        message: String,
        val completed: Long,
        val total: Long,
    ) : IOException(message)

    companion object {
        const val DEFAULT_MAX_TEXT_BYTES = 1_048_576
        private const val MAX_SEARCH_DEPTH = 12
        private val ENTRY_ORDER = compareByDescending<FileEntry> { it.isDirectory }
            .thenBy { it.isHidden }
            .thenBy { it.name.lowercase() }
    }
}

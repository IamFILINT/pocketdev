package dev.pocketdev.core.workspace

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque

class WorkspaceFiles(private val root: File) {

    private val canonicalRoot: File = root.canonicalFile

    fun list(relativeDirectory: String = "", showHidden: Boolean = false): WorkspaceResult<DirectoryListing> =
        wrapped {
            val directory = resolve(relativeDirectory)
            require(directory.isDirectory) { "Not a directory" }
            val entries = Files.list(directory.toPath()).use { stream ->
                stream.map { path: Path -> path.toFile() }
                    .filter { !Files.isSymbolicLink(it.toPath()) }
                    .map { file: File -> FileEntry.from(canonicalRoot, file) }
                    .filter { showHidden || !it.isHidden }
                    .toList()
                    .sortedWith(ENTRY_ORDER)
            }
            DirectoryListing(normalize(relativeDirectory), entries)
        }

    fun create(relativeDirectory: String, name: String, directory: Boolean): WorkspaceResult<String> =
        wrapped {
            requireValidName(name)
            val parent = resolve(relativeDirectory)
            require(parent.isDirectory) { "Not a directory" }
            val target = File(parent, name)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            val created = if (directory) target.mkdirs() else target.createNewFile()
            require(created) { "Could not create entry" }
            relativePathOf(target)
        }

    fun rename(relativePath: String, newName: String): WorkspaceResult<String> =
        wrapped {
            requireValidName(newName)
            val source = resolveExisting(relativePath)
            val target = File(source.parentFile, newName)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            Files.move(source.toPath(), target.toPath())
            relativePathOf(target)
        }

    fun delete(relativePath: String): WorkspaceResult<Unit> =
        wrapped {
            val source = resolveExisting(relativePath)
            require(source.canonicalFile != canonicalRoot) { "Cannot delete project root" }
            require(source.deleteRecursively()) { "Could not delete entry" }
        }

    fun copy(
        relativePath: String,
        targetDirectory: String,
        newName: String? = null,
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
            copyTree(source, destination)
            relativePathOf(destination)
        }

    fun move(
        relativePath: String,
        targetDirectory: String,
        newName: String? = null,
    ): WorkspaceResult<String> =
        wrapped {
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

    fun destination(relativeDirectory: String, name: String): WorkspaceResult<File> =
        wrapped {
            requireValidName(name)
            val directory = resolve(relativeDirectory)
            require(directory.isDirectory) { "Not a directory" }
            val target = File(directory, name)
            require(!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Already exists" }
            target
        }

    fun source(relativePath: String): WorkspaceResult<File> = wrapped { resolveExisting(relativePath) }

    fun search(
        query: String,
        showHidden: Boolean = false,
        maxResults: Int = 200,
    ): WorkspaceResult<List<FileEntry>> =
        wrapped {
            val needle = query.trim()
            if (needle.isEmpty() || maxResults <= 0) return@wrapped emptyList()
            val matches = mutableListOf<FileEntry>()
            val stack = ArrayDeque<Pair<File, Int>>()
            stack.add(canonicalRoot to 0)
            while (stack.isNotEmpty() && matches.size < maxResults) {
                val (directory, depth) = stack.removeLast()
                val children = directory.listFiles() ?: continue
                for (child in children) {
                    if (Files.isSymbolicLink(child.toPath())) continue
                    val entry = FileEntry.from(canonicalRoot, child)
                    if (!showHidden && entry.isHidden) continue
                    if (entry.name.contains(needle, ignoreCase = true)) matches.add(entry)
                    if (child.isDirectory && depth < MAX_SEARCH_DEPTH) {
                        stack.add(child to depth + 1)
                    }
                    if (matches.size >= maxResults) break
                }
            }
            matches.sortedWith(ENTRY_ORDER)
        }

    fun preview(relativePath: String, maxBytes: Int = 16_384): WorkspaceResult<String?> =
        wrapped {
            val source = resolveExisting(relativePath)
            require(source.isFile) { "Not a file" }
            val bytes = ByteArray(maxBytes.coerceAtLeast(1))
            val read = Files.newInputStream(source.toPath()).use { it.read(bytes) }
            if (read <= 0 || bytes.copyOf(read).any { it == 0.toByte() }) return@wrapped null
            String(bytes, 0, read, StandardCharsets.UTF_8)
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

    private fun copyTree(source: File, destination: File) {
        if (Files.isSymbolicLink(source.toPath())) return
        if (source.isFile) {
            destination.parentFile?.mkdirs()
            Files.copy(source.toPath(), destination.toPath())
            return
        }
        require(destination.mkdirs()) { "Could not create directory" }
        source.listFiles()?.forEach { child -> copyTree(child, File(destination, child.name)) }
            ?: throw IOException("Could not read directory")
    }

    private inline fun <T> wrapped(block: () -> T): WorkspaceResult<T> =
        runCatching(block).fold(
            onSuccess = { WorkspaceResult.Success(it) },
            onFailure = { WorkspaceResult.Failure(it.message ?: it::class.simpleName ?: "Failed") },
        )

    companion object {
        private const val MAX_SEARCH_DEPTH = 12
        private val ENTRY_ORDER = compareByDescending<FileEntry> { it.isDirectory }
            .thenBy { it.name.lowercase() }
    }
}

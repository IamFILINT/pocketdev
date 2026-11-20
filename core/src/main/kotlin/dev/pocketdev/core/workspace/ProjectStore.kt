package dev.pocketdev.core.workspace

import java.io.File

/**
 * Small, dependency-free persistence for the project registry.
 *
 * Format: one project per line, tab-separated:
 * `id \t name \t rootPath \t createdAtEpochMs`
 *
 * Values are escaped so tabs, newlines, and backslashes in names cannot corrupt
 * the file. Writes go through a temp file + rename so an interrupted write does
 * not destroy the registry.
 *
 * Note: this is intentionally not a general database. When project metadata grows
 * (per-project AI sessions, terminals, Git state), this should be replaced by a
 * proper store in the workspace layer.
 */
class ProjectStore(private val storageDir: File) {

    private val registryFile: File
        get() = File(storageDir, REGISTRY_FILE_NAME)

    @Synchronized
    fun load(): List<Project> {
        val file = registryFile
        if (!file.exists()) return emptyList()
        return file.readLines()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::parseLine)
            .distinctBy { it.id }
            .toList()
    }

    @Synchronized
    fun add(project: Project): List<Project> {
        val updated = load().filterNot { it.id == project.id } + project
        write(updated)
        return updated
    }

    @Synchronized
    fun remove(id: String): List<Project> {
        val updated = load().filterNot { it.id == id }
        write(updated)
        return updated
    }

    @Synchronized
    fun replaceAll(projects: List<Project>) {
        write(projects.distinctBy { it.id })
    }

    private fun write(projects: List<Project>) {
        storageDir.mkdirs()
        val target = registryFile
        val temp = File(storageDir, "$REGISTRY_FILE_NAME.tmp")
        temp.writeText(projects.joinToString(separator = "\n", transform = ::formatLine))
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Unable to persist project registry" }
        }
    }

    private fun formatLine(project: Project): String = listOf(
        escape(project.id),
        escape(project.name),
        escape(project.rootPath),
        project.createdAtEpochMs.toString(),
    ).joinToString(separator = "\t")

    private fun parseLine(line: String): Project? {
        val parts = line.split('\t')
        if (parts.size != 4) return null
        val createdAt = parts[3].toLongOrNull() ?: return null
        val id = unescape(parts[0])
        val name = unescape(parts[1])
        val rootPath = unescape(parts[2])
        if (id.isBlank() || name.isBlank() || rootPath.isBlank()) return null
        return Project(
            id = id,
            name = name,
            rootPath = rootPath,
            createdAtEpochMs = createdAt,
        )
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> { append('\\'); i += 2 }
                    't' -> { append('\t'); i += 2 }
                    'n' -> { append('\n'); i += 2 }
                    'r' -> { append('\r'); i += 2 }
                    else -> { append(ch); i += 1 }
                }
            } else {
                append(ch)
                i += 1
            }
        }
    }

    private companion object {
        const val REGISTRY_FILE_NAME = "projects.tsv"
    }
}
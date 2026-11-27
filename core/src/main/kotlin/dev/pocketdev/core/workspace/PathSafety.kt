package dev.pocketdev.core.workspace

import java.io.File
import java.nio.file.Files

/**
 * Guards every filesystem path that originates from user input or the AI agent.
 *
 * PocketDev exposes a project workspace to external actors (UI, editor, terminal,
 * and the AI agent). Path traversal via `..`, absolute paths, or drive letters
 * must never be able to escape a project root.
 */
object PathSafety {

    private val WINDOWS_DRIVE = Regex("^[A-Za-z]:")

    /**
     * @return true when [candidate] is a non-empty relative path with no empty,
     *   `.`, or `..` segments, no absolute prefix, no drive letter, and no NUL.
     */
    fun isSafeRelativePath(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        if (candidate.any { it == '\u0000' }) return false

        val normalized = candidate.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        if (WINDOWS_DRIVE.containsMatchIn(normalized)) return false

        val segments = normalized.split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." }
    }

    /**
     * Resolves [relative] inside [root], returning the canonical target file only
     * if it stays within the root. Returns null for unsafe or escaping paths.
     */
    fun resolveWithin(root: File, relative: String): File? {
        if (!isSafeRelativePath(relative)) return null
        val rootCanonical = root.canonicalFile
        val target = File(rootCanonical, relative.replace('\\', '/')).canonicalFile
        val rootPrefix = rootCanonical.path + File.separator
        return when {
            target.path == rootCanonical.path -> target
            target.path.startsWith(rootPrefix) -> target
            else -> null
        }
    }

    /**
     * Resolves [relative] inside [root] while rejecting symbolic links in every
     * existing path component, then verifies canonical containment.
     */
    fun resolveWithinNoFollow(root: File, relative: String): File? {
        if (!isSafeRelativePath(relative)) return null
        val rootCanonical = root.canonicalFile
        var current = rootCanonical
        for (segment in relative.replace('\\', '/').split('/')) {
            val candidate = File(current, segment)
            if (Files.isSymbolicLink(candidate.toPath())) return null
            current = candidate
        }
        val target = current.canonicalFile
        val rootPrefix = rootCanonical.path + File.separator
        return when {
            target.path == rootCanonical.path -> target
            target.path.startsWith(rootPrefix) -> target
            else -> null
        }
    }

    /**
     * Converts a project display name into a safe directory segment. Callers should
     * validate the name first; this is a defensive last step.
     */
    fun toSafeSegment(name: String): String {
        val cleaned = name
            .replace(Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trimStart('.')
        return cleaned.ifBlank { "project" }
    }
}
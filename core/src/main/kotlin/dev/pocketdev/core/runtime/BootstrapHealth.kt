package dev.pocketdev.core.runtime

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Structural health report for an extracted runtime root.
 *
 * This deliberately does not execute anything: real Bionic execution can only be
 * proven on a real Android device/emulator, which CI cannot provide for ARM64, so
 * the report records exactly what was checked (prompt §8.4 step 10, honest scope).
 */
data class BootstrapHealthReport(
    val checks: List<Check>,
) {
    data class Check(val name: String, val passed: Boolean, val detail: String)

    val ok: Boolean get() = checks.isNotEmpty() && checks.all { it.passed }

    fun failureSummary(): String =
        checks.filterNot { it.passed }.joinToString("; ") { "${it.name}: ${it.detail}" }
}

/**
 * File-level health checks for an installed prefix.
 *
 * @param requiredEntries paths (relative to the prefix) that must exist.
 * @param requiredExecutables subset of [requiredEntries] that must be executable.
 */
fun interface PrefixHealthCheck {
    fun check(prefix: File): BootstrapHealthReport
}

class BootstrapHealth(
    private val requiredEntries: List<String> = listOf(
        "bin/bash",
        "bin/pkg",
        "bin/apt",
        "etc/apt/sources.list",
    ),
    private val requiredExecutables: List<String> = listOf("bin/bash", "bin/apt", "bin/pkg"),
) : PrefixHealthCheck {

    override fun check(prefix: File): BootstrapHealthReport {
        val checks = ArrayList<BootstrapHealthReport.Check>()
        val root = prefix.toPath()

        checks += BootstrapHealthReport.Check(
            name = "prefix-exists",
            passed = Files.isDirectory(root),
            detail = prefix.absolutePath,
        )
        if (!Files.isDirectory(root)) return BootstrapHealthReport(checks)

        requiredEntries.forEach { relative ->
            val path = root.resolve(relative)
            checks += BootstrapHealthReport.Check(
                name = "entry:$relative",
                passed = Files.exists(path),
                detail = if (Files.exists(path)) "present" else "missing",
            )
        }

        requiredExecutables.forEach { relative ->
            val path = root.resolve(relative)
            checks += BootstrapHealthReport.Check(
                name = "exec:$relative",
                passed = Files.isExecutable(path),
                detail = if (Files.isExecutable(path)) "executable" else "not executable",
            )
        }

        val leftovers = Files.exists(root.resolve(BootstrapArchive.SYMLINKS_FILE))
        checks += BootstrapHealthReport.Check(
            name = "symlinks-processed",
            passed = !leftovers,
            detail = if (leftovers) "${BootstrapArchive.SYMLINKS_FILE} still present" else "consumed",
        )

        val escaping = findEscapingSymlinks(root)
        checks += BootstrapHealthReport.Check(
            name = "symlinks-contained",
            passed = escaping.isEmpty(),
            detail = if (escaping.isEmpty()) "all targets inside prefix" else escaping.joinToString(),
        )

        return BootstrapHealthReport(checks)
    }

    private fun findEscapingSymlinks(root: Path): List<String> {
        val escaping = ArrayList<String>()
        if (!Files.isDirectory(root)) return escaping
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isSymbolicLink(file)) {
                        val target = Files.readSymbolicLink(file)
                        val resolved = (file.parent ?: root).resolve(target).normalize()
                        if (!resolved.startsWith(root)) escaping += file.toString()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    escaping += file.toString()
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return escaping
    }
}

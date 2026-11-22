package dev.pocketdev.runtime

import dev.pocketdev.core.runtime.BootstrapHealth
import dev.pocketdev.core.runtime.BootstrapHealthReport
import dev.pocketdev.core.runtime.PrefixHealthCheck
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Health check that validates a prefix both structurally (via `BootstrapHealth`)
 * and by actually executing a binary through the system linker (`/system/bin/linker64`).
 *
 * This is the "real Bionic execution" step that `BootstrapHealth` alone cannot do:
 * it proves that `bash` and a coreutil (`ls`) can be loaded by the linker and run
 * with the relocated `LD_LIBRARY_PATH` / `PATH` / `LD_PRELOAD` environment.
 *
 * The check runs against the `prefix` argument passed by `BootstrapManager` — both
 * `usr.staging` (before swap) and `usr` (after swap) — so a failed post-swap
 * execution restores `usr.prev` via the manager's rollback.
 */
class RuntimeExecutionHealthCheck(
    private val dataDir: String,
    private val legacyDataDir: String,
    private val linker64: String = "/system/bin/linker64",
) : PrefixHealthCheck {

    private val structural = BootstrapHealth()

    override fun check(prefix: File): BootstrapHealthReport {
        val structuralReport = structural.check(prefix)
        val execChecks = runExecutionChecks(prefix)
        return BootstrapHealthReport(structuralReport.checks + execChecks)
    }

    private fun runExecutionChecks(prefix: File): List<BootstrapHealthReport.Check> {
        val checks = ArrayList<BootstrapHealthReport.Check>()

        val bash = File(prefix, "bin/bash")
        val ls = File(prefix, "bin/ls")
        val preload = File(prefix, "lib/libtermux-exec-ld-preload.so")
        val home = File(prefix.parentFile, "home").apply { mkdirs() }
        val env = RuntimeEnvBuilder(prefix, home, dataDir, legacyDataDir).baseEnv()
        val childEnv = if (preload.isFile) env.plus("LD_PRELOAD" to preload.absolutePath) else env

        checks += linkerExec(
            name = "linker-exec-bash",
            program = bash,
            args = arrayOf("-lc", "echo pd-runtime-ok"),
            childEnv = childEnv,
            expectSubstring = "pd-runtime-ok",
        )

        checks += linkerExec(
            name = "linker-exec-ls",
            program = ls,
            args = arrayOf("-1", File(prefix, "bin").absolutePath),
            childEnv = childEnv,
            expectNonEmptyOutput = true,
        )

        return checks
    }

    private fun linkerExec(
        name: String,
        program: File,
        args: Array<String>,
        childEnv: Map<String, String>,
        expectSubstring: String? = null,
        expectNonEmptyOutput: Boolean = false,
    ): BootstrapHealthReport.Check {
        if (!program.isFile) {
            return BootstrapHealthReport.Check(name, false, "program missing: ${program.absolutePath}")
        }
        return try {
            val argv = listOf(linker64, program.absolutePath) + args.toList()
            val process = ProcessBuilder(argv)
                .redirectErrorStream(true)
                .apply { environment().putAll(childEnv) }
                .start()
            val output = StringBuilder()
            val reader = Thread {
                process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
            }.apply { isDaemon = true; start() }
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(2_000)

            val exit = runCatching { process.exitValue() }.getOrDefault(-1)
            val ok = when {
                !finished -> false
                exit != 0 -> false
                expectSubstring != null -> output.toString().contains(expectSubstring)
                expectNonEmptyOutput -> output.toString().isNotBlank()
                else -> true
            }
            BootstrapHealthReport.Check(
                name = name,
                passed = ok,
                detail = if (ok) "exit=0 out=${output.toString().trim().take(120)}"
                else "exit=$exit out=${output.toString().trim().take(120)}",
            )
        } catch (e: Exception) {
            BootstrapHealthReport.Check(name, false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
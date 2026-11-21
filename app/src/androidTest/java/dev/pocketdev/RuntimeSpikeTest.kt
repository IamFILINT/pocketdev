package dev.pocketdev

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocketdev.core.runtime.Abi
import dev.pocketdev.core.runtime.BootstrapManager
import dev.pocketdev.core.runtime.BootstrapPins
import dev.pocketdev.core.runtime.HttpBootstrapDownloader
import dev.pocketdev.core.runtime.RuntimePaths
import dev.pocketdev.core.runtime.RuntimeState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ADR-0002 spike (prompt §8.4/§8.5, risks R11/R12).
 *
 * Provisions the pinned x86_64 bootstrap into app-private storage on the API 30
 * emulator and measures what actually executes:
 * 1. direct `execve` of a file in app data (expected to be blocked by W^X),
 * 2. `bash`/`ls` through `/system/bin/linker64` (the Termux first-hop workaround),
 * 3. shebang execution with and without `termux-exec` `LD_PRELOAD`.
 *
 * Results are reported, not assumed: only the linker64 hops are asserted. This test
 * never runs on a phone and proves nothing about ARM64 devices.
 */
class RuntimeSpikeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val report = mutableListOf<String>()

    private data class ExecResult(val finished: Boolean, val exit: Int, val output: String) {
        val ok: Boolean get() = finished && exit == 0
    }

    @Test
    fun pinnedBootstrapRunsThroughTheSystemLinker() {
        val paths = RuntimePaths(context.filesDir).apply { ensureBaseDirectories() }
        val manager = BootstrapManager(
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            downloader = HttpBootstrapDownloader(),
        )
        val state = manager.provision(BootstrapPins.manifest, Abi.X86_64, Build.VERSION.SDK_INT)
        report("provision", state.toString())
        assertTrue("provisioning failed: $state", state is RuntimeState.Ready)

        val prefix = paths.prefix
        val bash = File(prefix, "bin/bash")
        assertTrue("bash missing at $bash", bash.isFile)
        report("bash-mode", "canExecute=${bash.canExecute()} size=${bash.length()}")

        val environment = mapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to paths.home.absolutePath,
            "PATH" to "${prefix.absolutePath}/bin:/system/bin",
            "TMPDIR" to File(prefix, "tmp").apply { mkdirs() }.absolutePath,
            "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
        )
        val linker = "/system/bin/linker64"

        val direct = runCatching {
            ProcessBuilder(File(prefix, "bin/echo").absolutePath, "direct").start().waitFor()
        }
        report(
            "direct-exec-app-data",
            direct.fold({ "ALLOWED exit=$it (unexpected)" }, { "blocked: ${it.javaClass.simpleName}" }),
        )

        val bashResult = exec(environment, linker, bash.absolutePath, "-c", "echo spike-ok; echo PREFIX=\$PREFIX; echo HOME=\$HOME")
        report("bash-via-linker64", "finished=${bashResult.finished} exit=${bashResult.exit} out=${bashResult.output.trim()}")
        assertTrue("bash did not run through linker64: ${bashResult.output}", bashResult.ok)
        assertTrue("unexpected bash output: ${bashResult.output}", bashResult.output.contains("spike-ok"))

        val lsResult = exec(environment, linker, File(prefix, "bin/ls").absolutePath, "-1", File(prefix, "bin").absolutePath)
        report("ls-via-linker64", "finished=${lsResult.finished} exit=${lsResult.exit} lines=${lsResult.output.lines().size}")
        assertTrue("ls did not run through linker64: ${lsResult.output}", lsResult.ok)

        val script = File(prefix, "tmp/spike-shebang.sh").apply {
            writeText("#!${bash.absolutePath}\necho shebang-ok\n")
            setExecutable(true)
        }
        val noPreload = exec(environment, linker, bash.absolutePath, "-c", script.absolutePath)
        report("shebang-without-termux-exec", "exit=${noPreload.exit} out=${noPreload.output.trim().take(200)}")

        val preload = listOf(
            "libtermux-exec-ld-preload.so",
            "libtermux-exec-linker-ld-preload.so",
            "libtermux-exec-direct-ld-preload.so",
        ).map { File(prefix, "lib/$it") }.firstOrNull { it.isFile }
        if (preload == null) {
            report("termux-exec", "no preload library shipped in the archive")
        } else {
            val withPreload = exec(
                environment + ("LD_PRELOAD" to preload.absolutePath),
                linker,
                bash.absolutePath,
                "-c",
                script.absolutePath,
            )
            report(
                "shebang-with-termux-exec",
                "lib=${preload.name} exit=${withPreload.exit} out=${withPreload.output.trim().take(200)}",
            )
        }

        val tools = exec(environment, linker, bash.absolutePath, "-c", "command -v apt; command -v dpkg; command -v pkg")
        report("package-tools", tools.output.trim().replace('\n', ','))

        val reportFile = File(context.getExternalFilesDir(null), "spike-report.txt")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(report.joinToString("\n"))
    }

    private fun exec(environment: Map<String, String>, vararg command: String): ExecResult =
        try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
            val output = StringBuilder()
            val reader = Thread {
                process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
            }.apply { isDaemon = true; start() }
            val finished = process.waitFor(180, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            reader.join(5_000)
            ExecResult(finished, if (finished) process.exitValue() else -1, output.toString())
        } catch (e: Exception) {
            ExecResult(false, -1, "${e.javaClass.simpleName}: ${e.message}")
        }

    private fun report(key: String, value: String) {
        val line = "$key: $value"
        report += line
        Log.i("RuntimeSpike", line)
        println("RUNTIME_SPIKE $line")
    }
}

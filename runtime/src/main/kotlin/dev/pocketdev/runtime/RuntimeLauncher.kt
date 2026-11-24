package dev.pocketdev.runtime

import java.io.File

/**
 * Launches a program located in the PocketDev prefix via the system linker
 * (`/system/bin/linker64`). This is the "first-hop" workaround for Android 10+
 * W^X: direct `execve()` of files in app-private storage is blocked, but the
 * system linker can load and execute them.
 *
 * The caller is responsible for reading the process streams and calling
 * `waitFor()` or `destroyForcibly()`.
 */
class RuntimeLauncher(
    private val envBuilder: RuntimeEnvBuilder,
    private val linker64: String = "/system/bin/linker64",
    private val preload: File? = null,
) {

    /** The argv that will be passed to ProcessBuilder (linker64 + program + args). */
    fun command(program: File, vararg args: String): List<String> =
        listOf(linker64) + program.absolutePath + args.toList()

    /** The environment that will be applied to the child process. */
    fun environment(): Map<String, String> =
        if (preload != null) envBuilder.childEnv(preload) else envBuilder.baseEnv()

    fun plan(program: File, vararg args: String, workingDir: File): RuntimeLaunchPlan =
        RuntimeLaunchPlan(linker64, command(program, *args), environment(), workingDir.absolutePath)

    /** Starts `program` (absolute path inside $PREFIX) with `args` via the system linker. */
    fun exec(program: File, vararg args: String): Process = execIn(null, program, *args)

    /** Like [exec], with an explicit working directory for the child. */
    fun execIn(workingDir: File?, program: File, vararg args: String): Process {
        val argv = command(program, *args)
        val env = environment()
        return ProcessBuilder(argv)
            .redirectErrorStream(true)
            .apply {
                workingDir?.let { directory(it) }
                environment().putAll(env)
            }
            .start()
    }

    /** Starts a command given as an absolute program path + args. */
    fun exec(vararg argv: String): Process {
        require(argv.isNotEmpty()) { "argv must not be empty" }
        val env = environment()
        val allArgv = listOf(linker64) + argv.toList()
        return ProcessBuilder(allArgv)
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()
    }

    companion object {
        fun withPreload(envBuilder: RuntimeEnvBuilder, preload: File): RuntimeLauncher =
            RuntimeLauncher(envBuilder, "/system/bin/linker64", preload)

        fun withoutPreload(envBuilder: RuntimeEnvBuilder): RuntimeLauncher =
            RuntimeLauncher(envBuilder, "/system/bin/linker64", null)
    }
}
package dev.pocketdev.runtime

import java.io.File

/**
 * Starts one child process for [OpenCodeServer].
 *
 * Extracted so tests can supply a plain JVM launcher while production uses
 * [RuntimeProcessLauncher], which routes through `/system/bin/linker64` (ADR-0002).
 */
fun interface ProcessLauncher {
    fun launch(workingDir: File?, program: File, args: List<String>): Process
}

/** Launches PocketDev programs through the system linker (W^X first hop). */
class RuntimeProcessLauncher(
    private val launcher: RuntimeLauncher,
) : ProcessLauncher {

    override fun launch(workingDir: File?, program: File, args: List<String>): Process =
        launcher.execIn(workingDir, program, *args.toTypedArray())
}

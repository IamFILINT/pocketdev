package dev.pocketdev.runtime

import android.content.Context
import dev.pocketdev.core.runtime.RuntimePaths
import java.io.File

/**
 * Builds the execution environment for processes spawned inside the PocketDev
 * prefix, per docs/BOOTSTRAP_LAYOUT.md §8.5.
 *
 * Values are derived from the real app-private layout so that the stock
 * `termux-exec-ld-preload.so` from the pinned bootstrap recognizes our package
 * data dir and prefix (admitted by spike run `44878997591`) instead of its
 * compiled-in `/data/data/com.termux` defaults.
 */
class RuntimeEnvBuilder(
    private val prefix: File,
    private val home: File,
    val dataDir: String,
    val legacyDataDir: String,
    private val agentConfig: RuntimeAgentConfig = RuntimeAgentConfig.NONE,
) {

    private val tmpDir = File(prefix, "tmp")
    private val libDir = File(prefix, "lib")
    private val binDir = File(prefix, "bin")

    /** Base environment for the runtime itself (no `LD_PRELOAD`). */
    fun baseEnv(): Map<String, String> = buildMap {
        put("PREFIX", prefix.absolutePath)
        put("HOME", home.absolutePath)
        put("PATH", "${binDir.absolutePath}:${binDir.absolutePath}/applets:/system/bin")
        put("TMPDIR", tmpDir.absolutePath)
        put("LD_LIBRARY_PATH", libDir.absolutePath)
        put("TERM", "xterm-256color")
        put("LANG", "en_US.UTF-8")
        put("TERMUX_APP__DATA_DIR", dataDir)
        put("TERMUX_APP__LEGACY_DATA_DIR", legacyDataDir)
        put("TERMUX__PREFIX", prefix.absolutePath)
        putAll(agentConfig.environment)
    }

    /** Child-process environment: base environment plus the `LD_PRELOAD` hook. */
    fun childEnv(preload: File): Map<String, String> =
        baseEnv().plus("LD_PRELOAD" to preload.absolutePath)

    fun withAgentConfig(config: RuntimeAgentConfig): RuntimeEnvBuilder =
        RuntimeEnvBuilder(prefix, home, dataDir, legacyDataDir, config)

    companion object {
        /** Builds from the real Android app context and runtime layout. */
        fun fromContext(context: Context, paths: RuntimePaths): RuntimeEnvBuilder =
            RuntimeEnvBuilder(
                prefix = paths.prefix,
                home = paths.home,
                dataDir = context.applicationInfo.dataDir,
                legacyDataDir = "/data/data/${context.packageName}",
            )
    }
}

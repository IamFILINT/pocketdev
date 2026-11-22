package dev.pocketdev.runtime

import android.content.Context
import android.os.StatFs
import dev.pocketdev.core.runtime.Abi
import dev.pocketdev.core.runtime.BootstrapManifest
import dev.pocketdev.core.runtime.BootstrapManager
import dev.pocketdev.core.runtime.HttpBootstrapDownloader
import dev.pocketdev.core.runtime.RuntimePaths
import dev.pocketdev.core.runtime.RuntimeState
import java.io.File

/**
 * Android-side runtime wiring (docs/ARCHITECTURE.md): ties the Android-only pieces
 * together with the pure `:core` bootstrap manager.
 *
 * - resolves the real app-private runtime layout via [RuntimePaths];
 * - builds the execution environment for processes spawned inside the prefix;
 * - deploys/uses the stock `termux-exec` preload;
 * - launches programs through the system linker (`/system/bin/linker64`);
 * - runs the real execution health check during provisioning.
 */
class RuntimeController(context: Context) {

    private val appContext = context.applicationContext
    private val paths = RuntimePaths(appContext.filesDir).apply { ensureBaseDirectories() }

    val envBuilder: RuntimeEnvBuilder = RuntimeEnvBuilder.fromContext(appContext, paths)
    val preloadDeployer: RuntimePreloadDeployer = RuntimePreloadDeployer(paths.prefix)
    private val health: RuntimeExecutionHealthCheck = RuntimeExecutionHealthCheck(
        dataDir = envBuilder.dataDir,
        legacyDataDir = envBuilder.legacyDataDir,
    )

    val manager: BootstrapManager = BootstrapManager(
        filesDir = appContext.filesDir,
        cacheDir = appContext.cacheDir,
        downloader = HttpBootstrapDownloader(),
        health = health,
        freeBytes = { freeSpaceBytes() },
    )

    fun provision(manifest: BootstrapManifest, abi: Abi, apiLevel: Int): RuntimeState =
        manager.provision(manifest, abi, apiLevel)

    /** Starts [program] (absolute path inside `$PREFIX`) through `/system/bin/linker64`. */
    fun launch(program: File, vararg args: String): Process {
        val preload = preloadDeployer.preloadFile()
        val launcher = if (preload != null) {
            RuntimeLauncher.withPreload(envBuilder, preload)
        } else {
            RuntimeLauncher.withoutPreload(envBuilder)
        }
        return launcher.exec(program, *args)
    }

    fun childEnv(): Map<String, String> {
        val preload = preloadDeployer.preloadFile()
            ?: error("termux-exec preload is not deployed; provision the runtime first")
        return envBuilder.childEnv(preload)
    }

    private fun freeSpaceBytes(): Long {
        val statFs = StatFs(appContext.filesDir.absolutePath)
        return statFs.blockSizeLong * statFs.availableBlocksLong
    }
}

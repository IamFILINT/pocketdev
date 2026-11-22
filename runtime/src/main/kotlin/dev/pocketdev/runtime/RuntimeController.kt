package dev.pocketdev.runtime

import android.content.Context
import android.os.Build
import android.os.StatFs
import dev.pocketdev.core.runtime.Abi
import dev.pocketdev.core.runtime.BootstrapManifest
import dev.pocketdev.core.runtime.BootstrapManager
import dev.pocketdev.core.runtime.BootstrapPins
import dev.pocketdev.core.runtime.HttpBootstrapDownloader
import dev.pocketdev.core.runtime.RuntimePaths
import dev.pocketdev.core.runtime.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.NotInstalled)

    /**
     * Honest runtime state shared with the UI. Seeded from the bootstrap journal so
     * a completed installation survives process restarts.
     */
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    val manager: BootstrapManager = BootstrapManager(
        filesDir = appContext.filesDir,
        cacheDir = appContext.cacheDir,
        downloader = HttpBootstrapDownloader(),
        health = health,
        freeBytes = { freeSpaceBytes() },
        onState = { _state.value = it },
    )

    init {
        _state.value = initialState()
    }

    /** Provisions the pinned bootstrap for this device. Blocking: call off the main thread. */
    fun provision(): RuntimeState {
        val abi = abiFor(Build.SUPPORTED_ABIS.toList())
        val result = if (abi == null) {
            RuntimeState.Failed(
                RuntimeState.Stage.CHECK,
                "unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}",
            )
        } else {
            manager.provision(BootstrapPins.manifest, abi, Build.VERSION.SDK_INT)
        }
        _state.value = result
        return result
    }

    fun provision(manifest: BootstrapManifest, abi: Abi, apiLevel: Int): RuntimeState {
        val result = manager.provision(manifest, abi, apiLevel)
        _state.value = result
        return result
    }

    /** Pure predicate used by [initialState]: a completed journal + deployed preload. */
    internal companion object {
        fun initialStateFrom(journalCompleted: Boolean, version: String?, preloadDeployed: Boolean): RuntimeState =
            if (journalCompleted && version != null && preloadDeployed) {
                RuntimeState.Ready(version)
            } else {
                RuntimeState.NotInstalled
            }
    }

    private fun initialState(): RuntimeState {
        val journal = manager.journalStore.read()
        return initialStateFrom(
            journalCompleted = journal?.completed == true,
            version = journal?.version,
            preloadDeployed = preloadDeployer.isDeployed(),
        )
    }

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

    companion object {
        /** First ABI in [abis] that PocketDev has a pinned bootstrap for, if any. */
        fun abiFor(abis: List<String>): Abi? = when {
            abis.any { it == Abi.AARCH64.ndkAbi } -> Abi.AARCH64
            abis.any { it == Abi.X86_64.ndkAbi } -> Abi.X86_64
            else -> null
        }
    }
}

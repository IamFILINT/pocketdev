package dev.pocketdev.runtime

import android.content.Context
import android.os.Build
import dev.pocketdev.core.agent.AgentInstaller
import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.core.agent.OpenCodePins
import dev.pocketdev.core.runtime.RuntimePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Provisions and controls OpenCode Bionic (docs/adr/0003-opencode-provisioning.md).
 *
 * The agent is a self-contained aarch64 binary downloaded from a pinned release,
 * verified against its published SHA-256, and installed outside `$PREFIX` so a
 * runtime reinstall never removes it. ARM64 only: the upstream Bionic project does
 * not build x86_64, so this controller refuses to provision on the CI emulator.
 */
class OpenCodeController(context: Context) {

    private val appContext = context.applicationContext
    private val paths = RuntimePaths(appContext.filesDir).apply { ensureBaseDirectories() }

    /** Agent payloads live beside the runtime, never inside it. */
    val installDir: File = File(appContext.filesDir, "agent")

    private val _state = MutableStateFlow<AgentState>(AgentState.NotInstalled)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val installer = AgentInstaller(installDir, HttpAgentDownloader()) { _state.value = it }
    private val launcher = RuntimeLauncher.withoutPreload(
        RuntimeEnvBuilder.fromContext(appContext, paths),
    )

    init {
        _state.value = initialState()
    }

    private var process: Process? = null

    /** Downloads/verifies/installs the pinned agent. Blocking: call off the main thread. */
    fun provision(apiLevel: Int = Build.VERSION.SDK_INT): AgentState {
        val artifact = OpenCodePins.aarch64
        val result = if (apiLevel < artifact.minApiLevel) {
            AgentState.Failed("OpenCode Bionic requires API ${artifact.minApiLevel}+ (device is $apiLevel)")
        } else if (RuntimeController.abiFor(Build.SUPPORTED_ABIS.toList()) != artifact.abi) {
            AgentState.Failed("OpenCode Bionic is only built for ${artifact.abi.ndkAbi}")
        } else {
            installer.install(artifact)
        }
        _state.value = result
        return result
    }

    /**
     * Starts the agent inside [projectDir]. Returns the process; call [stop] to end it.
     */
    fun start(projectDir: File, vararg args: String): Process {
        val binary = installer.binary
        check(binary.isFile) { "OpenCode is not installed; provision it first" }
        check(projectDir.isDirectory) { "project directory does not exist: $projectDir" }
        stop()
        val started = launcher.execIn(projectDir, binary, *args).also { process = it }
        return started
    }

    fun stop() {
        process?.let { if (it.isAlive) it.destroy() }
        process = null
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun initialState(): AgentState =
        if (installer.isInstalled(OpenCodePins.aarch64)) {
            AgentState.Ready(OpenCodePins.UPSTREAM_VERSION)
        } else {
            AgentState.NotInstalled
        }
}

package dev.pocketdev.runtime

import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.core.agent.OpenCodeSessionApi
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Agent operations the Agent workspace needs, as an interface so the ViewModel can
 * be exercised with a fake runtime on the emulator. [OpenCodeController] is the
 * production implementation.
 */
interface AgentRuntime {

    val agentState: StateFlow<AgentState>

    val serverState: StateFlow<OpenCodeServerState>

    /** Downloads, verifies and installs the pinned agent. Blocking. */
    fun install(): AgentState

    /** Starts `opencode serve` in [projectDir]; readiness arrives on [serverState]. */
    fun startServer(projectDir: File): OpenCodeServerState

    fun stopServer()

    /** Client bound to the listening server, or null when it is not listening. */
    fun newApiClient(): OpenCodeSessionApi?
}

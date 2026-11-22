package dev.pocketdev.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.core.runtime.RuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes real runtime status to the dashboard.
 *
 * The state comes from [dev.pocketdev.runtime.RuntimeController] and reflects the
 * bootstrap journal; this class never fabricates progress.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container
    private val runtime = container.runtime
    private val openCode = container.openCode
    private val _provisioning = MutableStateFlow(false)
    private val _agentProvisioning = MutableStateFlow(false)

    val runtimeState: StateFlow<RuntimeState> = runtime.state
    val provisioning: StateFlow<Boolean> = _provisioning.asStateFlow()
    val agentState: StateFlow<AgentState> = openCode.state
    val agentProvisioning: StateFlow<Boolean> = _agentProvisioning.asStateFlow()

    /** Downloads, verifies and installs the pinned runtime. Safe to call twice. */
    fun installRuntime() {
        if (_provisioning.value || runtimeState.value is RuntimeState.Ready) return
        _provisioning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runtime.provision()
            } finally {
                _provisioning.value = false
            }
        }
    }

    /** Downloads and installs the pinned OpenCode Bionic binary. Safe to retry. */
    fun installAgent() {
        if (_agentProvisioning.value || agentState.value is AgentState.Ready) return
        _agentProvisioning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                openCode.provision()
            } finally {
                _agentProvisioning.value = false
            }
        }
    }
}

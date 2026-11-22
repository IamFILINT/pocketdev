package dev.pocketdev.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.PocketDevApplication
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

    private val runtime = (application as PocketDevApplication).container.runtime
    private val _provisioning = MutableStateFlow(false)

    val runtimeState: StateFlow<RuntimeState> = runtime.state
    val provisioning: StateFlow<Boolean> = _provisioning.asStateFlow()

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
}

package dev.pocketdev.ui.dashboard

import androidx.lifecycle.ViewModel
import dev.pocketdev.core.runtime.RuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exposes real runtime status to the dashboard.
 *
 * Until the runtime controller exists (Milestone 3), the honest state is
 * "NotInstalled". This class must never fabricate progress.
 */
class DashboardViewModel : ViewModel() {

    private val _runtimeState = MutableStateFlow<RuntimeState>(RuntimeState.NotInstalled)
    val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()
}
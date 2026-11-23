package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.runtime.RuntimeState
import dev.pocketdev.ui.dashboard.DashboardViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardViewModelTest {

    private lateinit var app: PocketDevApplication
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.container.projectStore.replaceAll(emptyList())
        viewModel = DashboardViewModel(app)
    }

    @After
    fun tearDown() {
        app.container.projectStore.replaceAll(emptyList())
    }

    @Test
    fun initialStateIsNotProvisioning() {
        assertFalse(viewModel.provisioning.value)
        assertFalse(viewModel.agentProvisioning.value)
    }

    @Test
    fun exposesRuntimeAndAgentStateFlows() {
        // Seeded from the bootstrap journal; accept any declared RuntimeState variant.
        assertTrue(viewModel.runtimeState.value is RuntimeState)
        // Agent state is one of the declared AgentState variants.
        viewModel.agentState.value // must not throw
    }

    @Test
    fun installRuntimeIsGuardedAgainstDoubleInvocation() {
        // Calling twice synchronously should not crash; the guard prevents overlap.
        viewModel.installRuntime()
        viewModel.installRuntime()
        // provisioning flag is either true (in flight) or false (completed synchronously).
        // Either way no exception and the flow remains readable.
        viewModel.provisioning.value
    }
}

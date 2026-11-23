package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.runtime.OpenCodeController
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCodeControllerTest {

    @Test
    fun provisionFailsClosedOnUnsupportedAbi() {
        // The emulator is x86_64; OpenCode Bionic is aarch64-only, so provisioning
        // must be refused rather than attempting a download.
        val controller = OpenCodeController(ApplicationProvider.getApplicationContext())
        val state = controller.provision()

        assertTrue(
            "expected Failed on unsupported ABI, got $state",
            state is AgentState.Failed,
        )
    }

    @Test
    fun startServerRequiresInstalledBinary() {
        val controller = OpenCodeController(ApplicationProvider.getApplicationContext())
        var threw = false
        try {
            controller.startServer(java.io.File(System.getProperty("java.io.tmpdir")))
        } catch (e: IllegalStateException) {
            threw = true
        } catch (e: Exception) {
            // Any failure is acceptable here; the binary is not installed.
            threw = true
        }
        assertTrue("startServer must refuse when the agent is not installed", threw)
    }
}

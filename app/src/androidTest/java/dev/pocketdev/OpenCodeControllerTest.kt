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
    fun provisionFailsClosedBelowMinimumApi() {
        // API 30 google_apis x86_64 images advertise arm64-v8a in
        // ro.product.cpu.abilist (ARM translation), so an ABI-only negative test
        // would attempt a real 211 MB download. Gate on API level instead: it is
        // deterministic and never touches the network. ABI selection itself is
        // covered by RuntimeControllerAbiTest.
        val controller = OpenCodeController(ApplicationProvider.getApplicationContext())
        val state = controller.provision(apiLevel = 28)

        assertTrue(
            "expected Failed below min API, got $state",
            state is AgentState.Failed,
        )
    }

    @Test
    fun startServerRequiresInstalledBinary() {
        val controller = OpenCodeController(ApplicationProvider.getApplicationContext())
        var threw = false
        val projectDir = java.io.File(System.getProperty("java.io.tmpdir") ?: ".")
        try {
            controller.startServer(projectDir)
        } catch (e: IllegalStateException) {
            threw = true
        } catch (e: Exception) {
            // Any failure is acceptable here; the binary is not installed.
            threw = true
        }
        assertTrue("startServer must refuse when the agent is not installed", threw)
    }
}

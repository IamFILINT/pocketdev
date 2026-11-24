package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocketdev.core.runtime.RuntimeState
import dev.pocketdev.ui.projects.CreateProjectResult
import dev.pocketdev.ui.projects.ProjectsViewModel
import dev.pocketdev.ui.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalViewModelTest {

    @Test
    fun runsRealBionicPtyInSelectedProjectAndSupportsSessions() {
        Assume.assumeTrue(
            "Bionic PTY runs only with bionicPty=true (see runtime-spike.yml)",
            InstrumentationRegistry.getArguments().getString("bionicPty") == "true",
        )
        val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
        val state = app.container.runtime.provision()
        assertTrue("runtime provisioning failed: $state", state is RuntimeState.Ready)
        val prefix = app.container.paths.prefix.absolutePath

        val projectsRoot = app.container.projectsRoot
        val projectsViewModel = ProjectsViewModel(app)
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()

        assertTrue(projectsViewModel.createProject("pty-bionic") is CreateProjectResult.Created)
        val project = projectsViewModel.uiState.value.projects.first { it.name == "pty-bionic" }
        app.container.selectProject(project.id)

        val viewModel = TerminalViewModel(app)
        try {
            assertEquals(project.rootPath, viewModel.uiState.value.rootPath)
            assertEquals(1, viewModel.uiState.value.sessions.size)
            assertEquals(null, viewModel.uiState.value.error)

            viewModel.send("echo pocketdev-bionic-ok\r")
            waitUntil { output(viewModel).contains("pocketdev-bionic-ok") }
            viewModel.send("echo BIONIC_PREFIX=\$PREFIX\r")
            waitUntil { output(viewModel).contains("BIONIC_PREFIX=$prefix") }
            viewModel.send("command -v bash\r")
            waitUntil { output(viewModel).contains("$prefix/bin/bash") }

            viewModel.createSession()
            assertEquals(2, viewModel.uiState.value.sessions.size)

            viewModel.resize(60, 20)
            assertEquals(60, viewModel.uiState.value.snapshot.cols)
            assertEquals(20, viewModel.uiState.value.snapshot.rows)

            viewModel.closeSession(viewModel.uiState.value.activeId!!)
            assertEquals(1, viewModel.uiState.value.sessions.size)
        } finally {
            viewModel.shutdown()
            projectsViewModel.clearRegistry()
            projectsRoot.deleteRecursively()
        }
    }

    private fun output(viewModel: TerminalViewModel): String =
        viewModel.uiState.value.snapshot.lines.joinToString("\n") { it.text() }

    private fun waitUntil(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", false)
    }
}

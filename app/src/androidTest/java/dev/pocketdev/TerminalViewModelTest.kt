package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.ui.projects.CreateProjectResult
import dev.pocketdev.ui.projects.ProjectsViewModel
import dev.pocketdev.ui.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalViewModelTest {

    @Test
    fun runsRealPtyInSelectedProjectAndSupportsSessions() {
        val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
        val projectsRoot = app.container.projectsRoot
        val projectsViewModel = ProjectsViewModel(app)
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()

        assertTrue(projectsViewModel.createProject("pty-basic") is CreateProjectResult.Created)
        val project = projectsViewModel.uiState.value.projects.first { it.name == "pty-basic" }
        app.container.selectProject(project.id)

        val viewModel = TerminalViewModel(app)
        try {
            assertEquals(project.rootPath, viewModel.uiState.value.rootPath)
            assertEquals(1, viewModel.uiState.value.sessions.size)

            viewModel.send("echo pocketdev-pty-ok\r")
            waitUntil {
                viewModel.uiState.value.snapshot.lines.joinToString("\n") { it.text() }
                    .contains("pocketdev-pty-ok")
            }

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

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", false)
    }
}

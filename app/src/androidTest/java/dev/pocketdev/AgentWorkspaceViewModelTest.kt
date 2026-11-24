package dev.pocketdev

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.core.agent.OpenCodeServerConnection
import dev.pocketdev.core.agent.OpenCodeSession
import dev.pocketdev.core.agent.OpenCodeSessionApi
import dev.pocketdev.core.agent.PermissionDecision
import dev.pocketdev.core.agent.ServerSentEvent
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.runtime.AgentRuntime
import dev.pocketdev.runtime.OpenCodeServerState
import dev.pocketdev.ui.agent.AgentWorkspaceViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AgentWorkspaceViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val projectDir = File(application.cacheDir, "agent-workspace-test").apply { mkdirs() }

    private fun project(id: String, name: String, root: File): Project =
        Project(id, name, root.absolutePath, 0L)

    private fun selectedFlow(project: Project): MutableStateFlow<Project?> =
        MutableStateFlow(project)

    private class FakeApi(
        initial: List<OpenCodeSession> = listOf(OpenCodeSession("ses_1", "First session", null)),
        private val events: List<ServerSentEvent> = listOf(
            ServerSentEvent("session.created", "{}", null),
            ServerSentEvent("message.part.updated", """{"type":"reasoning"}""", null),
            ServerSentEvent("session.idle", "{}", null),
        ),
    ) : OpenCodeSessionApi {
        val sessions = initial.toMutableList()
        var streamCalls = 0
        var created = 0
        val prompts = mutableListOf<Pair<String, String>>()
        val replies = mutableListOf<Triple<String, String, PermissionDecision>>()
        var promptBlock: (() -> Unit)? = null

        override fun listSessions(limit: Int): List<OpenCodeSession> = sessions.toList()

        override fun createSession(title: String?): OpenCodeSession {
            created++
            val session = OpenCodeSession("ses_new_$created", title, null)
            sessions.add(session)
            return session
        }

        override fun prompt(sessionId: String, text: String): String {
            promptBlock?.invoke()
            prompts.add(sessionId to text)
            return "{}"
        }

        override fun replyPermission(sessionId: String, requestId: String, decision: PermissionDecision) {
            replies.add(Triple(sessionId, requestId, decision))
        }

        override fun streamEvents(sessionId: String?, onEvent: (ServerSentEvent) -> Boolean): Int {
            streamCalls++
            var delivered = 0
            for (event in events) {
                delivered++
                if (!onEvent(event)) break
            }
            return delivered
        }
    }

    private class FakeRuntime(val api: FakeApi = FakeApi()) : AgentRuntime {
        override val agentState = MutableStateFlow<AgentState>(AgentState.NotInstalled)
        override val serverState = MutableStateFlow<OpenCodeServerState>(OpenCodeServerState.Stopped)
        var installed = false
        var startedDir: File? = null

        override fun install(): AgentState {
            installed = true
            val ready = AgentState.Ready("2.3.2")
            agentState.value = ready
            return ready
        }

        override fun startServer(projectDir: File): OpenCodeServerState {
            startedDir = projectDir
            val listening = OpenCodeServerState.Listening(
                OpenCodeServerConnection(
                    url = "http://127.0.0.1:4096",
                    port = 4096,
                    password = "pw",
                ),
            )
            serverState.value = listening
            return listening
        }

        override fun stopServer() {
            serverState.value = OpenCodeServerState.Stopped
        }

        override fun newApiClient(): OpenCodeSessionApi? = api
    }

    private fun await(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun startServerLoadsSessionsAndStreamsRealEvents() {
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))

        viewModel.startServer()

        await {
            viewModel.sessions.value.size == 1 &&
                viewModel.taskState.value == TaskState.COMPLETED &&
                !viewModel.streaming.value
        }
        assertEquals(projectDir.canonicalPath, runtime.startedDir?.canonicalPath)
        assertTrue(runtime.api.streamCalls >= 1)
        assertEquals("First session", viewModel.sessions.value.single().title)
        assertNull(viewModel.actionError.value)
    }

    @Test
    fun stopServerDetachesTheStreamAndClearsSessions() {
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))
        viewModel.startServer()
        await { viewModel.sessions.value.isNotEmpty() }

        viewModel.stopServer()

        await { viewModel.sessions.value.isEmpty() && !viewModel.streaming.value }
        assertEquals(OpenCodeServerState.Stopped, runtime.serverState.value)
    }

    @Test
    fun installDelegatesToProvisioning() {
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))

        viewModel.installAgent()

        await { viewModel.agentState.value is AgentState.Ready }
        assertTrue(runtime.installed)
        assertFalse(viewModel.busy.value)
    }

    @Test
    fun sendPromptCreatesSessionWhenNoneIsSelected() {
        val runtime = FakeRuntime(FakeApi(emptyList()))
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))
        viewModel.startServer()
        await { runtime.api.streamCalls > 0 && !viewModel.busy.value && viewModel.activeSessionId.value == null }

        viewModel.sendPrompt("hello")

        await { runtime.api.created == 1 && runtime.api.prompts.size == 1 }
        assertEquals("ses_new_1" to "hello", runtime.api.prompts.single())
        assertEquals("ses_new_1", viewModel.activeSessionId.value)
        assertFalse(viewModel.sending.value)
    }

    @Test
    fun createSessionSelectsTheNewSession() {
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))
        viewModel.startServer()
        await { viewModel.sessions.value.isNotEmpty() }

        viewModel.createSession()

        await { viewModel.activeSessionId.value == "ses_new_1" }
        assertEquals(2, viewModel.sessions.value.size)
    }

    @Test
    fun permissionRequestFromStreamCanBeReplied() {
        val api = FakeApi(
            events = listOf(
                ServerSentEvent("session.created", "{}", null),
                ServerSentEvent("message.part.updated", """{"type":"text"}""", null),
                ServerSentEvent(
                    event = "permission.asked",
                    data = """{"type":"permission.asked","data":{"id":"per_1","sessionID":"ses_1","action":"bash","resources":["pwd"]}}""",
                    id = null,
                ),
            ),
        )
        val runtime = FakeRuntime(api)
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))

        viewModel.startServer()

        await { viewModel.permissionRequest.value?.id == "per_1" }
        assertEquals(TaskState.WAITING_FOR_PERMISSION, viewModel.taskState.value)
        viewModel.respondToPermission(PermissionDecision.REJECT)
        await { api.replies.size == 1 }
        assertEquals(Triple("ses_1", "per_1", PermissionDecision.REJECT), api.replies.single())
    }

    @Test
    fun blankPromptIsRejectedWithoutCallingTheServer() {
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, projectDir, selectedFlow(project("test", "Test", projectDir)))
        viewModel.startServer()
        await { viewModel.sessions.value.isNotEmpty() }

        viewModel.sendPrompt("   ")

        assertTrue(viewModel.actionError.value != null)
        assertTrue(runtime.api.prompts.isEmpty())
        assertEquals(0, runtime.api.created)
    }

    @Test
    fun switchingProjectsWhileAgentIsIdleDoesNotRetargetServerUntilRestart() {
        val base = File(application.cacheDir, "agent-switch-base").apply { mkdirs() }
        val first = File(base, "first").apply { mkdirs() }
        val second = File(base, "second").apply { mkdirs() }
        val selected = selectedFlow(project("first", "First", first))
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, base, selected)

        viewModel.startServer()
        await { viewModel.serverContext.value?.rootPath == first.canonicalPath }
        selected.value = project("second", "Second", second)
        await { viewModel.selectedProjectContext.value?.projectId == "second" }

        assertEquals(first.canonicalPath, viewModel.serverContext.value?.rootPath)
        assertEquals(first.canonicalPath, runtime.startedDir?.canonicalPath)

        viewModel.stopServer()
        await { viewModel.serverContext.value == null }
        viewModel.startServer()
        await {
            runtime.startedDir?.canonicalPath == second.canonicalPath &&
                viewModel.serverContext.value?.rootPath == second.canonicalPath
        }
    }

    @Test
    fun switchingProjectsWhileAgentIsBusyKeepsServerBoundToOriginalRoot() {
        val base = File(application.cacheDir, "agent-busy-base").apply { mkdirs() }
        val first = File(base, "first").apply { mkdirs() }
        val second = File(base, "second").apply { mkdirs() }
        val selected = selectedFlow(project("first", "First", first))
        val runtime = FakeRuntime()
        val viewModel = AgentWorkspaceViewModel(application, runtime, base, selected)
        val promptStarted = CountDownLatch(1)
        val promptRelease = CountDownLatch(1)
        runtime.api.promptBlock = {
            promptStarted.countDown()
            promptRelease.await()
        }

        viewModel.startServer()
        await { viewModel.serverContext.value != null && runtime.api.streamCalls > 0 && !viewModel.busy.value }
        viewModel.sendPrompt("hello")
        assertTrue(promptStarted.await(3, TimeUnit.SECONDS))
        assertTrue(viewModel.sending.value)
        selected.value = project("second", "Second", second)
        await { viewModel.selectedProjectContext.value?.projectId == "second" }

        assertEquals(first.canonicalPath, viewModel.serverContext.value?.rootPath)
        assertEquals(first.canonicalPath, runtime.startedDir?.canonicalPath)
        promptRelease.countDown()
        await { !viewModel.sending.value && runtime.api.prompts.size == 1 }
        assertEquals(first.canonicalPath, viewModel.serverContext.value?.rootPath)
    }
}

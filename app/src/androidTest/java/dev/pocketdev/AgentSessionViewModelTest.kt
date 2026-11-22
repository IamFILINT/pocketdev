package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.agent.ServerSentEvent
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.ui.agent.AgentSessionViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentSessionViewModelTest {

    private lateinit var viewModel: AgentSessionViewModel

    @Before
    fun setUp() {
        viewModel = AgentSessionViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        viewModel.reset()
    }

    @Test
    fun startsIdle() {
        assertEquals(TaskState.IDLE, viewModel.taskState.value)
    }

    @Test
    fun appliesAllowedTransition() {
        viewModel.applyEvent(ServerSentEvent("session.created", "{}", null))
        assertEquals(TaskState.INITIALIZING, viewModel.taskState.value)
    }

    @Test
    fun ignoresOutOfOrderEvent() {
        viewModel.applyEvent(ServerSentEvent("session.created", "{}", null))
        viewModel.applyEvent(ServerSentEvent("message.part.updated", """{"type":"reasoning"}""", null))
        assertEquals(TaskState.THINKING, viewModel.taskState.value)

        // COMPLETED -> THINKING is illegal; event should be ignored.
        viewModel.applyEvent(ServerSentEvent("session.idle", "{}", null))
        assertEquals(TaskState.COMPLETED, viewModel.taskState.value)
        viewModel.applyEvent(ServerSentEvent("message.part.updated", """{"type":"reasoning"}""", null))
        assertEquals(TaskState.COMPLETED, viewModel.taskState.value)
    }

    @Test
    fun resetReturnsToIdle() {
        viewModel.applyEvent(ServerSentEvent("session.created", "{}", null))
        viewModel.reset()
        assertEquals(TaskState.IDLE, viewModel.taskState.value)
    }

    @Test
    fun unknownEventDoesNotChangeState() {
        viewModel.applyEvent(ServerSentEvent("totally.unknown", "{}", null))
        assertEquals(TaskState.IDLE, viewModel.taskState.value)
    }
}

package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.agent.OpenCodeSession
import dev.pocketdev.core.agent.OpenCodeSessionApi
import dev.pocketdev.core.agent.ServerSentEvent
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.ui.agent.AgentSessionViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private class FakeApi(
        private val events: List<ServerSentEvent> = emptyList(),
        private val failure: Exception? = null,
    ) : OpenCodeSessionApi {
        override fun listSessions(limit: Int): List<OpenCodeSession> = emptyList()
        override fun createSession(title: String?): OpenCodeSession = OpenCodeSession("ses_test", title, null)
        override fun prompt(sessionId: String, text: String): String = "{}"
        override fun streamEvents(sessionId: String?, onEvent: (ServerSentEvent) -> Boolean): Int {
            failure?.let { throw it }
            var delivered = 0
            for (event in events) {
                delivered++
                if (!onEvent(event)) break
            }
            return delivered
        }
    }

    private class BlockingApi : OpenCodeSessionApi {
        val started = CountDownLatch(1)
        override fun listSessions(limit: Int): List<OpenCodeSession> = emptyList()
        override fun createSession(title: String?): OpenCodeSession = OpenCodeSession("ses_test", title, null)
        override fun prompt(sessionId: String, text: String): String = "{}"
        override fun streamEvents(sessionId: String?, onEvent: (ServerSentEvent) -> Boolean): Int {
            started.countDown()
            Thread.sleep(60_000)
            return 0
        }
    }

    private fun await(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun streamAppliesEventsUntilTerminal() {
        val api = FakeApi(
            listOf(
                ServerSentEvent("session.created", "{}", null),
                ServerSentEvent("message.part.updated", """{"type":"reasoning"}""", null),
                ServerSentEvent("session.idle", "{}", null),
            ),
        )

        viewModel.start(api)

        await { viewModel.taskState.value == TaskState.COMPLETED && !viewModel.streaming.value }
        assertNull(viewModel.error.value)
    }

    @Test
    fun streamFailureSurfacesErrorWithoutInventingState() {
        viewModel.start(FakeApi(failure = IOException("stream broke")))

        await { !viewModel.streaming.value }
        assertEquals("stream broke", viewModel.error.value)
        assertEquals(TaskState.IDLE, viewModel.taskState.value)
    }

    @Test
    fun stopEndsStreamingWithoutError() {
        val api = BlockingApi()
        viewModel.start(api)
        assertTrue(api.started.await(2, TimeUnit.SECONDS))
        assertTrue(viewModel.streaming.value)

        viewModel.stop()

        assertFalse(viewModel.streaming.value)
        Thread.sleep(100)
        assertNull(viewModel.error.value)
    }
}

package dev.pocketdev.core.ai

import dev.pocketdev.core.agent.ServerSentEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskStateMapperTest {

    @Test
    fun sessionCreatedMovesIdleToInitializing() {
        val ev = ServerSentEvent("session.created", "{}", null)
        assertEquals(TaskState.INITIALIZING, TaskStateMapper.fromEvent(ev, TaskState.IDLE))
    }

    @Test
    fun sessionIdleMovesRunningToCompleted() {
        val ev = ServerSentEvent("session.idle", "{}", null)
        assertEquals(TaskState.COMPLETED, TaskStateMapper.fromEvent(ev, TaskState.RUNNING))
    }

    @Test
    fun sessionErrorMovesToFailed() {
        val ev = ServerSentEvent("session.error", "{}", null)
        assertEquals(TaskState.FAILED, TaskStateMapper.fromEvent(ev, TaskState.RUNNING))
    }

    @Test
    fun permissionAskedMovesToWaiting() {
        val ev = ServerSentEvent("permission.asked", "{}", null)
        assertEquals(TaskState.WAITING_FOR_PERMISSION, TaskStateMapper.fromEvent(ev, TaskState.RUNNING))
    }

    @Test
    fun messagePartReasoningMovesToThinking() {
        val ev = ServerSentEvent("message.part.updated", """{"type":"reasoning"}""", null)
        assertEquals(TaskState.THINKING, TaskStateMapper.fromEvent(ev, TaskState.RUNNING))
    }

    @Test
    fun messagePartToolMovesToToolRunning() {
        val ev = ServerSentEvent("message.part.updated", """{"type":"tool"}""", null)
        assertEquals(TaskState.TOOL_RUNNING, TaskStateMapper.fromEvent(ev, TaskState.RUNNING))
    }

    @Test
    fun sessionUpdatedBusyMovesToRunning() {
        val ev = ServerSentEvent("session.updated", """{"status":"busy"}""", null)
        assertEquals(TaskState.RUNNING, TaskStateMapper.fromEvent(ev, TaskState.THINKING))
    }

    @Test
    fun outOfOrderTransitionIsIgnored() {
        // COMPLETED -> THINKING is not allowed.
        val ev = ServerSentEvent("message.part.updated", """{"type":"reasoning"}""", null)
        assertNull(TaskStateMapper.fromEvent(ev, TaskState.COMPLETED))
    }

    @Test
    fun unknownEventReturnsNull() {
        val ev = ServerSentEvent("totally.unknown", "{}", null)
        assertNull(TaskStateMapper.fromEvent(ev, TaskState.RUNNING))
    }

    @Test
    fun nestedPropertiesAreRead() {
        val ev = ServerSentEvent("session.updated", """{"properties":{"status":"busy"}}""", null)
        assertEquals(TaskState.RUNNING, TaskStateMapper.fromEvent(ev, TaskState.THINKING))
    }
}

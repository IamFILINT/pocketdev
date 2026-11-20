package dev.pocketdev.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateMachineTest {

    @Test
    fun `normal conversation flow is allowed`() {
        val chain = listOf(
            TaskState.IDLE,
            TaskState.INITIALIZING,
            TaskState.THINKING,
            TaskState.TOOL_RUNNING,
            TaskState.THINKING,
            TaskState.COMPLETED,
        )
        chain.zipWithNext().forEach { (from, to) ->
            assertTrue("expected $from -> $to", TaskStateMachine.canTransition(from, to))
        }
    }

    @Test
    fun `terminal states can only return to idle`() {
        listOf(TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED).forEach { terminal ->
            assertTrue(TaskStateMachine.canTransition(terminal, TaskState.IDLE))
            assertFalse(TaskStateMachine.canTransition(terminal, TaskState.THINKING))
        }
    }

    @Test
    fun `cannot complete from idle`() {
        assertFalse(TaskStateMachine.canTransition(TaskState.IDLE, TaskState.COMPLETED))
    }

    @Test
    fun `waiting for permission can resume or cancel`() {
        assertTrue(TaskStateMachine.canTransition(TaskState.WAITING_FOR_PERMISSION, TaskState.TOOL_RUNNING))
        assertTrue(TaskStateMachine.canTransition(TaskState.WAITING_FOR_PERMISSION, TaskState.CANCELLED))
    }

    @Test
    fun `isTerminal reflects terminal states`() {
        assertTrue(TaskState.COMPLETED.isTerminal)
        assertTrue(TaskState.FAILED.isTerminal)
        assertTrue(TaskState.CANCELLED.isTerminal)
        assertFalse(TaskState.RUNNING.isTerminal)
    }
}
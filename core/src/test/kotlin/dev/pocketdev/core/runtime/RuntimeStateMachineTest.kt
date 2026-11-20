package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStateMachineTest {

    @Test
    fun `happy path transitions are allowed`() {
        val chain = listOf(
            RuntimeState.NotInstalled,
            RuntimeState.Checking,
            RuntimeState.Downloading(10, 100),
            RuntimeState.Verifying,
            RuntimeState.Extracting(1, 3),
            RuntimeState.Ready("0.1.0"),
        )
        chain.zipWithNext().forEach { (from, to) ->
            assertTrue("expected $from -> $to to be allowed", RuntimeStateMachine.canTransition(from, to))
        }
    }

    @Test
    fun `skipping verification is rejected`() {
        assertFalse(
            RuntimeStateMachine.canTransition(
                RuntimeState.Downloading(10, 100),
                RuntimeState.Extracting(0, 1),
            ),
        )
    }

    @Test
    fun `returning to ready without update is rejected`() {
        assertFalse(RuntimeStateMachine.canTransition(RuntimeState.Checking, RuntimeState.Updating))
    }

    @Test
    fun `failed can be retried`() {
        val failed = RuntimeState.Failed(RuntimeState.Stage.DOWNLOAD, "network error")
        assertTrue(RuntimeStateMachine.canTransition(failed, RuntimeState.Checking))
        assertTrue(RuntimeStateMachine.canTransition(failed, RuntimeState.Downloading(0, 0)))
    }

    @Test
    fun `requireTransition throws for illegal transition`() {
        try {
            RuntimeStateMachine.requireTransition(RuntimeState.NotInstalled, RuntimeState.Ready("x"))
            throw AssertionError("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Illegal runtime transition"))
        }
    }

    @Test
    fun `download fraction is clamped and safe`() {
        assertEquals(0f, RuntimeState.Downloading(0, 0).fraction, 0.0f)
        assertEquals(0.5f, RuntimeState.Downloading(50, 100).fraction, 0.0001f)
        assertEquals(1f, RuntimeState.Downloading(150, 100).fraction, 0.0f)
    }
}
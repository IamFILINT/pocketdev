package dev.pocketdev.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class OpenCodeServerTest {

    /** Launches a shell script instead of the agent binary; records the requested args. */
    private class ScriptLauncher(private val script: String) : ProcessLauncher {
        val launches = CopyOnWriteArrayList<List<String>>()

        override fun launch(workingDir: File?, program: File, args: List<String>): Process {
            launches.add(listOf(program.absolutePath) + args)
            return ProcessBuilder("/bin/sh", "-c", script)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
        }
    }

    private fun awaitState(
        server: OpenCodeServer,
        timeoutMs: Long = 10_000,
        predicate: (OpenCodeServerState) -> Boolean,
    ): OpenCodeServerState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = server.state.value
            if (predicate(current)) return current
            Thread.sleep(25)
        }
        return server.state.value
    }

    @Test
    fun reachesListeningAndPassesServeArgs() {
        val launcher = ScriptLauncher(
            "echo 'server listening on http://127.0.0.1:4096'; echo 'server password pw-123'; sleep 30",
        )
        val server = OpenCodeServer(launcher)
        try {
            server.start(File("/tmp"), File("/tmp"))

            val state = awaitState(server) { it is OpenCodeServerState.Listening }
            assertTrue("state was $state", state is OpenCodeServerState.Listening)
            val connection = (state as OpenCodeServerState.Listening).connection
            assertEquals(4096, connection.port)
            assertEquals("pw-123", connection.password)

            val launched = launcher.launches.single()
            assertTrue(launched.contains("serve"))
            assertTrue(launched.contains("--hostname"))
            assertTrue(launched.contains("127.0.0.1"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun reportsExitedAfterListeningProcessEnds() {
        val launcher = ScriptLauncher(
            "echo 'server listening on http://127.0.0.1:4097'; echo 'server password pw'; exit 0",
        )
        val server = OpenCodeServer(launcher)
        server.start(File("/tmp"), File("/tmp"))
        awaitState(server) { it is OpenCodeServerState.Listening }

        val state = awaitState(server) { it is OpenCodeServerState.Exited || it is OpenCodeServerState.Failed }
        assertTrue("state was $state", state is OpenCodeServerState.Exited)
    }

    @Test
    fun failsWhenProcessExitsBeforeListening() {
        val launcher = ScriptLauncher("echo 'opencode v2.3.2 starting'; exit 3")
        val server = OpenCodeServer(launcher)
        server.start(File("/tmp"), File("/tmp"))

        val state = awaitState(server) { it is OpenCodeServerState.Failed }
        assertTrue("state was $state", state is OpenCodeServerState.Failed)
        assertNotNull((state as OpenCodeServerState.Failed).message)
    }

    @Test
    fun refusesNonLoopbackUrlBeforeListening() {
        val launcher = ScriptLauncher(
            "echo 'server listening on http://192.168.0.5:4096'; echo 'server password pw'; sleep 0.3; exit 1",
        )
        val server = OpenCodeServer(launcher)
        server.start(File("/tmp"), File("/tmp"))

        val state = awaitState(server) { it is OpenCodeServerState.Failed }
        assertTrue("state was $state", state is OpenCodeServerState.Failed)
    }

    @Test
    fun stopReturnsToStoppedAndKillsChild() {
        val launcher = ScriptLauncher(
            "echo 'server listening on http://127.0.0.1:4098'; echo 'server password pw'; sleep 30",
        )
        val server = OpenCodeServer(launcher)
        server.start(File("/tmp"), File("/tmp"))
        awaitState(server) { it is OpenCodeServerState.Listening }
        assertTrue(server.isRunning())

        server.stop()

        assertFalse(server.isRunning())
        assertEquals(OpenCodeServerState.Stopped, server.state.value)
    }
}

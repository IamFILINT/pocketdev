package dev.pocketdev.terminal

import dev.pocketdev.ProjectContext
import dev.pocketdev.core.terminal.TerminalSnapshot
import dev.pocketdev.runtime.RuntimeLaunchPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.ArrayDeque

class TerminalSessionTest {

    @Test
    fun decodesNativeExitInformation() {
        assertEquals(PtyExitInfo(), PtyExitInfo.fromNative(0))
        assertEquals(
            PtyExitInfo(PtyExitKind.EXITED, 7),
            PtyExitInfo.fromNative(encoded(PtyExitKind.EXITED, 7)),
        )
        assertEquals(
            PtyExitInfo(PtyExitKind.SIGNALED, 9),
            PtyExitInfo.fromNative(encoded(PtyExitKind.SIGNALED, 9)),
        )
    }

    @Test
    fun exposesExitInfoAndNormalizesInput() {
        val driver = FakePtyDriver()
        driver.exitValue = encoded(PtyExitKind.EXITED, 3)
        val session = session(driver)

        val info = session.waitForExit(1000)
        assertEquals(PtyExitKind.EXITED, info.kind)
        assertEquals(3, info.code)
        assertFalse(session.isRunning())

        session.send("hello\n")
        waitUntil { String(driver.lastWrite) == "hello\r" }

        session.close()
        assertEquals(1, driver.closeCount)
        session.close()
        assertEquals(1, driver.closeCount)
    }

    @Test
    fun carriesBoundProjectContext() {
        val context = ProjectContext("project-1", "Project One", "/tmp/project")
        val session = session(FakePtyDriver(), context)

        assertEquals(context, session.context)
        assertEquals(context.rootPath, session.context.rootPath)
    }

    @Test
    fun supportsConcurrentListenerChangesAndIdempotentClose() {
        val driver = FakePtyDriver()
        val session = session(driver)
        val listener: (TerminalSnapshot) -> Unit = { }
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        val threads = (1..4).map {
            Thread {
                repeat(50) {
                    runCatching {
                        session.addSnapshotListener(listener)
                        session.removeSnapshotListener(listener)
                    }.onFailure { failures.add(it) }
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        driver.feed("done".toByteArray(Charsets.UTF_8))
        waitUntil { session.plainText().contains("done") }

        session.close()
        session.close()
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(1, driver.closeCount)
        assertEquals(1, driver.stopCount)
    }

    private fun session(
        driver: FakePtyDriver,
        context: ProjectContext = ProjectContext(null, "Test project", "/tmp"),
    ): TerminalSession = TerminalSession(
        name = "test",
        context = context,
        plan = RuntimeLaunchPlan(
            executable = "/bin/bash",
            argv = listOf("/bin/bash"),
            environment = emptyMap(),
            workingDirectory = "/tmp",
        ),
        driver = driver,
    )

    private fun encoded(kind: PtyExitKind, code: Int): Long =
        ((code.toLong() and 0xFF) shl 8) or (kind.ordinal.toLong() and 0xFF)

    private fun waitUntil(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", false)
    }

    private class FakePtyDriver : PtyDriver {
        private val lock = Any()
        private val pending = ArrayDeque<ByteArray>()
        var closeCount = 0
        var stopCount = 0
        var exitValue = 0L
        var lastWrite = ByteArray(0)
        private var readClosed = false

        fun feed(bytes: ByteArray) = synchronized(lock) { pending.addLast(bytes) }

        override fun open(cols: Int, rows: Int, cwd: String, executable: String, argv: Array<String>, environment: Array<String>): Long = 42L

        override fun read(pointer: Long, buffer: ByteArray): Int = synchronized(lock) {
            if (readClosed) return@synchronized PtyDriver.READ_CLOSED
            val bytes = pending.pollFirst()
            if (bytes == null) {
                Thread.sleep(1)
                -1
            } else {
                val count = minOf(bytes.size, buffer.size)
                System.arraycopy(bytes, 0, buffer, 0, count)
                count
            }
        }

        override fun write(pointer: Long, buffer: ByteArray): Int {
            lastWrite = buffer.copyOf()
            return buffer.size
        }

        override fun resize(pointer: Long, cols: Int, rows: Int): Boolean = true
        override fun isRunning(pointer: Long): Boolean = exitValue == 0L
        override fun exitInfo(pointer: Long): Long = exitValue
        override fun waitForExit(pointer: Long, timeoutMillis: Int): Long = exitValue
        override fun hangup(pointer: Long) = Unit
        override fun terminate(pointer: Long) = Unit
        override fun requestStop(pointer: Long) {
            stopCount++
            synchronized(lock) { readClosed = true }
        }
        override fun close(pointer: Long) {
            closeCount++
        }
    }
}

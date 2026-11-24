package dev.pocketdev.terminal

import dev.pocketdev.core.terminal.AnsiBuffer
import dev.pocketdev.core.terminal.TerminalSnapshot
import dev.pocketdev.runtime.RuntimeLaunchPlan
import java.nio.charset.StandardCharsets
import java.util.UUID

class TerminalSession(
    val name: String,
    plan: RuntimeLaunchPlan,
    cols: Int = 80,
    rows: Int = 24,
    private val driver: PtyDriver = PtyNativeDriver,
) {

    val id: String = UUID.randomUUID().toString()
    private val buffer = AnsiBuffer(cols, rows)
    private val pointer: Long = driver.open(
        cols,
        rows,
        plan.workingDirectory,
        plan.executable,
        plan.argv.toTypedArray(),
        plan.envArray,
    ).also { check(it != 0L) { "Could not start PTY" } }
    private val readers = ByteArray(8192)
    private val stateLock = Any()
    private val operationLock = Any()
    private val onSnapshotListeners = mutableListOf<(TerminalSnapshot) -> Unit>()
    @Volatile private var closed = false
    @Volatile private var currentExitInfo = PtyExitInfo()

    private val readerThread = Thread({
        while (!closed) {
            val count = driver.read(pointer, readers)
            if (count == PtyDriver.READ_CLOSED) break
            if (count < 0) continue
            if (count == 0) break
            buffer.feed(readers.copyOf(count))
            emitSnapshot()
        }
        refreshExitInfo()
    }, "pty-reader-$id").apply { isDaemon = true }

    init {
        readerThread.start()
    }

    fun addSnapshotListener(listener: (TerminalSnapshot) -> Unit) {
        synchronized(stateLock) { onSnapshotListeners.add(listener) }
        listener(buffer.snapshot())
    }

    fun removeSnapshotListener(listener: (TerminalSnapshot) -> Unit) {
        synchronized(stateLock) { onSnapshotListeners.remove(listener) }
    }

    fun snapshot(): TerminalSnapshot = buffer.snapshot()

    fun plainText(): String = buffer.plainText()

    fun exitInfo(): PtyExitInfo {
        if (!closed) refreshExitInfo()
        return currentExitInfo
    }

    fun waitForExit(timeoutMs: Long): PtyExitInfo {
        val info = synchronized(operationLock) {
            if (closed) currentExitInfo
            else PtyExitInfo.fromNative(driver.waitForExit(pointer, timeoutMs.toInt()))
        }
        publishExitInfo(info)
        return info
    }

    fun send(text: String) {
        if (text.isEmpty()) return
        synchronized(operationLock) {
            if (closed) return
            driver.write(pointer, text.replace("\n", "\r").toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun resize(cols: Int, rows: Int) {
        synchronized(operationLock) {
            if (closed) return
            buffer.resize(cols, rows)
            driver.resize(pointer, cols, rows)
        }
        emitSnapshot()
    }

    fun isRunning(): Boolean = !closed && refreshExitInfo().kind == PtyExitKind.RUNNING

    fun terminate() {
        synchronized(operationLock) {
            if (!closed) driver.terminate(pointer)
        }
    }

    fun close() {
        synchronized(operationLock) {
            if (closed) return
            closed = true
            driver.requestStop(pointer)
        }
        readerThread.join(1000)
        if (readerThread.isAlive) terminate()
        readerThread.join(2000)
        val finalInfo = synchronized(operationLock) {
            val info = PtyExitInfo.fromNative(driver.waitForExit(pointer, 1000))
            driver.close(pointer)
            info
        }
        publishExitInfo(finalInfo)
    }

    private fun refreshExitInfo(): PtyExitInfo {
        val info = synchronized(operationLock) {
            if (closed) currentExitInfo
            else PtyExitInfo.fromNative(driver.exitInfo(pointer))
        }
        publishExitInfo(info)
        return info
    }

    private fun publishExitInfo(info: PtyExitInfo) {
        currentExitInfo = info
        emitSnapshot()
    }

    private fun emitSnapshot() {
        val snapshot = buffer.snapshot()
        val listeners = synchronized(stateLock) { onSnapshotListeners.toList() }
        listeners.forEach { it(snapshot) }
    }
}

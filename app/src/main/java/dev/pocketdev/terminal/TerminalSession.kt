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
) {

    val id: String = UUID.randomUUID().toString()
    private val buffer = AnsiBuffer(cols, rows)
    private val pointer = PtyNative.open(
        cols,
        rows,
        plan.workingDirectory,
        plan.executable,
        plan.argv.toTypedArray(),
        plan.envArray,
    )
    private val readers = ByteArray(8192)
    private var closed = false
    private val onSnapshotListeners = mutableListOf<(TerminalSnapshot) -> Unit>()

    private val readerThread = Thread({
        while (!closed) {
            val count = PtyNative.read(pointer, readers)
            if (count <= 0) break
            buffer.feed(readers.copyOf(count))
            emitSnapshot()
        }
    }, "pty-reader-$id").apply { isDaemon = true }

    init {
        check(pointer != 0L) { "Could not start PTY" }
        readerThread.start()
    }

    fun addSnapshotListener(listener: (TerminalSnapshot) -> Unit) {
        onSnapshotListeners.add(listener)
        listener(buffer.snapshot())
    }

    fun removeSnapshotListener(listener: (TerminalSnapshot) -> Unit) {
        onSnapshotListeners.remove(listener)
    }

    fun snapshot(): TerminalSnapshot = buffer.snapshot()

    fun plainText(): String = buffer.plainText()

    fun send(text: String) {
        if (closed || text.isEmpty()) return
        PtyNative.write(pointer, text.replace("\n", "\r").toByteArray(StandardCharsets.UTF_8))
    }

    fun resize(cols: Int, rows: Int) {
        if (closed) return
        buffer.resize(cols, rows)
        PtyNative.resize(pointer, cols, rows)
        emitSnapshot()
    }

    fun isRunning(): Boolean = !closed && PtyNative.isRunning(pointer)

    fun close() {
        if (closed) return
        closed = true
        PtyNative.hangup(pointer)
        readerThread.join(500)
        if (readerThread.isAlive) {
            PtyNative.terminate(pointer)
            readerThread.join(500)
        }
        if (readerThread.isAlive) return
        PtyNative.close(pointer)
        emitSnapshot()
    }

    private fun emitSnapshot() {
        val snapshot = buffer.snapshot()
        onSnapshotListeners.toList().forEach { it(snapshot) }
    }
}

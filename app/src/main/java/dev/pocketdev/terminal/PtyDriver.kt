package dev.pocketdev.terminal

enum class PtyExitKind {
    RUNNING,
    EXITED,
    SIGNALED,
    UNKNOWN,
}

data class PtyExitInfo(
    val kind: PtyExitKind = PtyExitKind.RUNNING,
    val code: Int = 0,
) {
    companion object {
        fun fromNative(value: Long): PtyExitInfo {
            if (value == 0L) return PtyExitInfo()
            val kind = (value and 0xFFL).toInt()
            val code = ((value shr 8) and 0xFFL).toInt()
            return PtyExitInfo(
                kind = when (kind) {
                    1 -> PtyExitKind.EXITED
                    2 -> PtyExitKind.SIGNALED
                    3 -> PtyExitKind.UNKNOWN
                    else -> PtyExitKind.RUNNING
                },
                code = code,
            )
        }
    }
}

interface PtyDriver {
    fun open(cols: Int, rows: Int, cwd: String, executable: String, argv: Array<String>, environment: Array<String>): Long
    fun read(pointer: Long, buffer: ByteArray): Int
    fun write(pointer: Long, buffer: ByteArray): Int
    fun resize(pointer: Long, cols: Int, rows: Int): Boolean
    fun isRunning(pointer: Long): Boolean
    fun exitInfo(pointer: Long): Long
    fun waitForExit(pointer: Long, timeoutMillis: Int): Long
    fun hangup(pointer: Long)
    fun terminate(pointer: Long)
    fun requestStop(pointer: Long)
    fun close(pointer: Long)

    companion object {
        const val READ_CLOSED = -2
    }
}

object PtyNativeDriver : PtyDriver {
    override fun open(cols: Int, rows: Int, cwd: String, executable: String, argv: Array<String>, environment: Array<String>): Long =
        PtyNative.open(cols, rows, cwd, executable, argv, environment)

    override fun read(pointer: Long, buffer: ByteArray): Int = PtyNative.read(pointer, buffer)
    override fun write(pointer: Long, buffer: ByteArray): Int = PtyNative.write(pointer, buffer)
    override fun resize(pointer: Long, cols: Int, rows: Int): Boolean = PtyNative.resize(pointer, cols, rows)
    override fun isRunning(pointer: Long): Boolean = PtyNative.isRunning(pointer)
    override fun exitInfo(pointer: Long): Long = PtyNative.exitInfo(pointer)
    override fun waitForExit(pointer: Long, timeoutMillis: Int): Long = PtyNative.waitForExit(pointer, timeoutMillis)
    override fun hangup(pointer: Long) = PtyNative.hangup(pointer)
    override fun terminate(pointer: Long) = PtyNative.terminate(pointer)
    override fun requestStop(pointer: Long) = PtyNative.requestStop(pointer)
    override fun close(pointer: Long) = PtyNative.close(pointer)
}

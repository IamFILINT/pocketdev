package dev.pocketdev.terminal

object PtyNative {

    init {
        System.loadLibrary("pocketdev_pty")
    }

    external fun open(
        cols: Int,
        rows: Int,
        cwd: String,
        executable: String,
        argv: Array<String>,
        environment: Array<String>,
    ): Long

    external fun read(pointer: Long, buffer: ByteArray): Int

    external fun write(pointer: Long, buffer: ByteArray): Int

    external fun resize(pointer: Long, cols: Int, rows: Int): Boolean

    external fun isRunning(pointer: Long): Boolean

    external fun hangup(pointer: Long)

    external fun terminate(pointer: Long)

    external fun close(pointer: Long)
}

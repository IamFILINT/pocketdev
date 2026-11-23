package dev.pocketdev.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiBufferTest {

    @Test
    fun writesTextAndHandlesCarriageReturnAndNewline() {
        val buffer = AnsiBuffer(20, 3)
        buffer.feed("hello\r\nworld".toByteArray())

        val snapshot = buffer.snapshot()
        assertEquals("hello", snapshot.lines[0].text())
        assertEquals("world", snapshot.lines[1].text())
        assertEquals(1 to 5, cursorOf(snapshot))
    }

    @Test
    fun csiCursorMovementOverwritesAndErases() {
        val buffer = AnsiBuffer(10, 3)
        buffer.feed("abcdef".toByteArray())
        buffer.feed("\u001B[3DXYZ".toByteArray())
        assertEquals("abcXYZ", buffer.snapshot().lines[0].text())

        buffer.feed("\u001B[2K".toByteArray())
        assertEquals("", buffer.snapshot().lines[0].text())
    }

    @Test
    fun scrollingMovesLinesIntoScrollback() {
        val buffer = AnsiBuffer(10, 2)
        buffer.feed("a\nb\nc".toByteArray())

        val snapshot = buffer.snapshot()
        assertEquals(listOf("a", "b", "c"), snapshot.lines.map { it.text() })
        assertEquals(2 to 1, cursorOf(snapshot))
    }

    @Test
    fun resizeKeepsVisibleHistoryAndTruncatesColumns() {
        val buffer = AnsiBuffer(4, 2)
        buffer.feed("abcdefgh".toByteArray())
        buffer.resize(4, 1)
        assertEquals(listOf("abcd", "efgh"), buffer.snapshot().lines.map { it.text() })

        buffer.resize(2, 1)
        assertEquals(listOf("abcd", "ef"), buffer.snapshot().lines.map { it.text() })
    }

    @Test
    fun sgrColorsCellsAndOscSequencesAreIgnored() {
        val buffer = AnsiBuffer(40, 2)
        buffer.feed("\u001B]0;window title\u0007".toByteArray())
        buffer.feed("\u001B[31mred\u001B[0m plain".toByteArray())

        val line = buffer.snapshot().lines[0]
        assertEquals("red plain", line.text())
        assertNotNull(line.cells[0].foreground)
    }

    @Test
    fun plainTextContainsScrollbackAndScreen() {
        val buffer = AnsiBuffer(10, 2)
        buffer.feed("one\ntwo\nthree".toByteArray())

        assertTrue(buffer.plainText().contains("one"))
        assertTrue(buffer.plainText().contains("three"))
    }

    private fun cursorOf(snapshot: TerminalSnapshot): Pair<Int, Int> {
        snapshot.lines.forEachIndexed { row, line ->
            line.cells.forEachIndexed { column, cell ->
                if (cell.cursor) return row to column
            }
        }
        error("No cursor cell in snapshot")
    }
}

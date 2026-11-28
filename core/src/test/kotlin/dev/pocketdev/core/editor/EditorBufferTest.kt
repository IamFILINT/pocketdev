package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBufferTest {
    @Test
    fun `replace updates text line map and dirty state`() {
        val buffer = EditorBuffer("one\ntwo")

        assertTrue(buffer.replace(4, 7, "2"))
        assertEquals("one\n2", buffer.text)
        assertEquals(listOf(0, 4), buffer.lineStarts)
        assertTrue(buffer.dirty)
    }

    @Test
    fun `undo and redo preserve saved-state dirty state`() {
        val buffer = EditorBuffer("abc")

        buffer.replace(3, 3, "!")
        buffer.replace(0, 0, ">")
        assertTrue(buffer.canUndo)

        buffer.undo()
        assertTrue(buffer.canRedo)
        assertEquals("abc!", buffer.text)

        buffer.redo()
        assertEquals(">abc!", buffer.text)
        assertTrue(buffer.dirty)

        buffer.undo()
        buffer.undo()
        assertFalse(buffer.dirty)
        assertTrue(buffer.canRedo)
    }

    @Test
    fun `older save completion does not clear a newer revision`() {
        val buffer = EditorBuffer("one")

        buffer.replace(3, 3, "X")
        val savedRevision = buffer.revision
        val savedText = buffer.text
        buffer.replace(4, 4, "Y")

        buffer.markSaved(savedRevision, savedText)

        assertTrue(buffer.dirty)
        assertEquals("oneXY", buffer.text)
    }

    @Test
    fun `replaceRange and line helpers operate on editor text`() {
        val buffer = EditorBuffer("first\nsecond")

        assertTrue(buffer.replaceRange(0..4, "line"))
        assertEquals("line\nsecond", buffer.text)
        assertEquals("second", buffer.lineText(1))
        assertEquals(2 to 1, buffer.lineAndColumn(5))
    }

    @Test
    fun `line and column are one based at the start of a file`() {
        val buffer = EditorBuffer("val greeting = \"hi\"\n")

        assertEquals(1 to 1, buffer.lineAndColumn(0))
        assertEquals(1 to 20, buffer.lineAndColumn(19))
        assertEquals(2 to 1, buffer.lineAndColumn(20))
    }
}
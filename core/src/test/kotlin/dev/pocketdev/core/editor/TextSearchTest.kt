package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {
    @Test
    fun `finds matches with case and whole-word options`() {
        val text = "Foo foo foobar Foo"
        val caseSensitive = TextSearch.findAll(text, "Foo", ignoreCase = false)
        val insensitive = TextSearch.findAll(text, "foo", ignoreCase = true)
        val wholeWord = TextSearch.findAll(text, "foo", ignoreCase = true, wholeWord = true)

        assertEquals(listOf(TextMatch(0, 3), TextMatch(15, 18)), caseSensitive)
        assertEquals(listOf(TextMatch(0, 3), TextMatch(4, 7), TextMatch(8, 11), TextMatch(15, 18)), insensitive)
        assertEquals(listOf(TextMatch(0, 3), TextMatch(4, 7), TextMatch(15, 18)), wholeWord)
    }

    @Test
    fun `findNext wraps after the current offset`() {
        assertEquals(
            TextMatch(8, 11),
            TextSearch.findNext("one two one", "one", fromOffset = 4),
        )
        assertEquals(
            TextMatch(0, 3),
            TextSearch.findNext("one two one", "one", fromOffset = 9),
        )
    }

    @Test
    fun `replaceAll rewrites matches and reports count`() {
        val (text, count) = TextSearch.replaceAll(
            text = "a A a",
            query = "a",
            replacement = "b",
            ignoreCase = true,
        )

        assertEquals("b b b", text)
        assertEquals(3, count)
    }
}
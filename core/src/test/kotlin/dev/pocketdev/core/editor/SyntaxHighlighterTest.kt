package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {
    @Test
    fun `kotlin tokens classify keywords strings and comments`() {
        val text = "val name = \"x\" // note\n"
        val tokens = SyntaxHighlighter.tokenize(text, EditorLanguage.KOTLIN)

        assertTrue(tokens.any { text.substring(it.start, it.end) == "val" && it.type == SyntaxTokenType.KEYWORD })
        assertTrue(tokens.any { text.substring(it.start, it.end) == "\"x\"" && it.type == SyntaxTokenType.STRING })
        assertTrue(tokens.any { text.substring(it.start, it.end) == "// note" && it.type == SyntaxTokenType.COMMENT })
        assertCovers(text, tokens)
    }

    @Test
    fun `json tokens classify literals and numbers`() {
        val text = "{\"enabled\": true, \"count\": 2}"
        val tokens = SyntaxHighlighter.tokenize(text, EditorLanguage.JSON)

        assertTrue(tokens.any { text.substring(it.start, it.end) == "true" && it.type == SyntaxTokenType.KEYWORD })
        assertTrue(tokens.any { text.substring(it.start, it.end) == "2" && it.type == SyntaxTokenType.NUMBER })
        assertCovers(text, tokens)
    }

    @Test
    fun `python line comments and strings are highlighted`() {
        val text = "# note\nprint('x')"
        val tokens = SyntaxHighlighter.tokenize(text, EditorLanguage.PYTHON)

        assertTrue(tokens.any { text.substring(it.start, it.end) == "# note" && it.type == SyntaxTokenType.COMMENT })
        assertTrue(tokens.any { text.substring(it.start, it.end) == "'x'" && it.type == SyntaxTokenType.STRING })
        assertCovers(text, tokens)
    }

    private fun assertCovers(text: String, tokens: List<SyntaxToken>) {
        var cursor = 0
        for (token in tokens) {
            assertEquals(cursor, token.start)
            cursor = token.end
        }
        assertEquals(text.length, cursor)
    }
}
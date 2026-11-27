package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFormatterTest {
    @Test
    fun `formats indentation and trailing whitespace without adding a newline`() {
        val formatted = EditorFormatter.format(
            text = "fun main() {   \n\tprintln(\"hi\")   \n}",
            language = EditorLanguage.KOTLIN,
        )

        assertEquals("fun main() {\n    println(\"hi\")\n}", formatted)
        assertEquals(formatted, EditorFormatter.format(formatted, EditorLanguage.KOTLIN))
    }

    @Test
    fun `unsupported languages are left byte for byte unchanged`() {
        val text = "a\tb\r\nc  \n"

        assertEquals(text, EditorFormatter.format(text, EditorLanguage.PLAIN_TEXT))
        assertEquals(text, EditorFormatter.format(text, EditorLanguage.MARKDOWN))
        assertEquals(text, EditorFormatter.format(text, EditorLanguage.YAML))
        assertFalse(EditorFormatter.supportsFormatting(EditorLanguage.MARKDOWN))
        assertTrue(EditorFormatter.supportsFormatting(EditorLanguage.KOTLIN))
    }

    @Test
    fun `markdown hard line breaks are preserved`() {
        val text = "first line  \nsecond line\\\nthird line"

        assertEquals(text, EditorFormatter.format(text, EditorLanguage.MARKDOWN))
    }

    @Test
    fun `string literals multiline strings and comments are preserved`() {
        val text = "val raw = \"\"\"\n\tkeep  \n\"\"\"\nval s = \"a\\tb\"   \n// keep  \n"

        assertEquals(
            "val raw = \"\"\"\n\tkeep  \n\"\"\"\nval s = \"a\\tb\"\n// keep  \n",
            EditorFormatter.format(text, EditorLanguage.KOTLIN),
        )
    }
}

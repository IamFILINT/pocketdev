package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorFormatterTest {
    @Test
    fun `formats trailing whitespace and tabs for code languages`() {
        val formatted = EditorFormatter.format(
            text = "fun main() {   \n\tprintln(\"hi\")   \n}",
            language = EditorLanguage.KOTLIN,
        )

        assertEquals("fun main() {\n    println(\"hi\")\n}\n", formatted)
    }

    @Test
    fun `keeps plain text tabs and normalizes newlines`() {
        val formatted = EditorFormatter.format(
            text = "a\tb\r\nc",
            language = EditorLanguage.PLAIN_TEXT,
        )

        assertEquals("a\tb\nc\n", formatted)
    }
}
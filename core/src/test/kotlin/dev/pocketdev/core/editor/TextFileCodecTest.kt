package dev.pocketdev.core.editor

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileCodecTest {
    @Test
    fun `decodes utf8 bom crlf and normalizes line endings`() {
        val bytes = byteArrayOf(
            0xEF.toByte(),
            0xBB.toByte(),
            0xBF.toByte(),
        ) + "one\r\ntwo".toByteArray(StandardCharsets.UTF_8)

        val snapshot = TextFileCodec.decode(bytes, bytes.size.toLong(), 7L, false)!!

        assertEquals("one\ntwo", snapshot.text)
        assertEquals(TextEncoding.UTF_8_BOM, snapshot.encoding)
        assertEquals(NewlineStyle.CRLF, snapshot.newlineStyle)
        assertEquals(listOf(NewlineStyle.CRLF), snapshot.newlineStyles)
        assertEquals(FileSignature.fromBytes(bytes, 7L), snapshot.signature)
        assertFalse(snapshot.truncated)
    }

    @Test
    fun `encode preserves configured encoding and newline style`() {
        val bytes = TextFileCodec.encode(
            text = "a\nb",
            encoding = TextEncoding.UTF_8_BOM,
            newlineStyle = NewlineStyle.CRLF,
        )

        val expected = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()).toList() +
            "a\r\nb".toByteArray(StandardCharsets.UTF_8).toList()

        assertEquals(expected, bytes.toList())
    }

    @Test
    fun `malformed utf8 is rejected at start middle and end`() {
        assertNull(
            TextFileCodec.decode(byteArrayOf(0xFF.toByte()) + "abc".toByteArray(), 4L, 1L, false),
        )
        assertNull(
            TextFileCodec.decode(
                "abc".toByteArray() + byteArrayOf(0xC3.toByte()) + "def".toByteArray(),
                7L,
                1L,
                false,
            ),
        )
        assertNull(
            TextFileCodec.decode("abc".toByteArray() + byteArrayOf(0x80.toByte()), 4L, 1L, false),
        )
    }

    @Test
    fun `mixed newline styles are preserved byte for byte`() {
        val text = "a\r\nb\rc\nd"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val snapshot = TextFileCodec.decode(bytes, bytes.size.toLong(), 1L, false)!!

        assertEquals("a\nb\nc\nd", snapshot.text)
        assertEquals(
            listOf(NewlineStyle.CRLF, NewlineStyle.CR, NewlineStyle.LF),
            snapshot.newlineStyles,
        )
        assertEquals(
            text,
            String(
                TextFileCodec.encode(
                    text = snapshot.text,
                    encoding = snapshot.encoding,
                    newlineStyle = snapshot.newlineStyle,
                    newlineStyles = snapshot.newlineStyles,
                ),
                StandardCharsets.UTF_8,
            ),
        )
    }

    @Test
    fun `new newline boundaries use the selected newline style`() {
        val original = "a\r\nb".toByteArray(StandardCharsets.UTF_8)
        val snapshot = TextFileCodec.decode(original, original.size.toLong(), 1L, false)!!
        val edited = "a\nb\nc"

        assertEquals(
            "a\r\nb\nc",
            String(
                TextFileCodec.encode(
                    text = edited,
                    encoding = snapshot.encoding,
                    newlineStyle = NewlineStyle.LF,
                    newlineStyles = snapshot.newlineStyles,
                ),
                StandardCharsets.UTF_8,
            ),
        )
    }

    @Test
    fun `binary content is rejected and truncation is reported`() {
        val binary = byteArrayOf(0x70, 0x00, 0x6B)

        assertNull(TextFileCodec.decode(binary, 3L, 1L, false))
        assertEquals(
            true,
            TextFileCodec.decode("text".toByteArray(), 99L, 1L, true)?.truncated,
        )
    }
}
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
        assertEquals(FileSignature(bytes.size.toLong(), 7L), snapshot.signature)
        assertFalse(snapshot.truncated)
    }

    @Test
    fun `encode preserves configured encoding and newline style`() {
        val bytes = TextFileCodec.encode(
            text = "a\nb",
            encoding = TextEncoding.UTF_8_BOM,
            newlineStyle = NewlineStyle.CRLF,
        )

        assertEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "a\r\nb".toByteArray(StandardCharsets.UTF_8).toList(),
            bytes.toList(),
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
package dev.pocketdev.core.editor

import java.nio.charset.StandardCharsets

object TextFileCodec {
    private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun decode(
        bytes: ByteArray,
        sizeBytes: Long,
        lastModifiedEpochMs: Long,
        truncated: Boolean,
    ): TextFileSnapshot? {
        if (bytes.any { it == 0.toByte() }) return null
        val hasBom = bytes.size >= UTF_8_BOM.size && bytes.copyOfRange(0, UTF_8_BOM.size)
            .contentEquals(UTF_8_BOM)
        val payload = if (hasBom) bytes.copyOfRange(UTF_8_BOM.size, bytes.size) else bytes
        val raw = String(payload, StandardCharsets.UTF_8)
        return TextFileSnapshot(
            text = normalizeNewlines(raw),
            encoding = if (hasBom) TextEncoding.UTF_8_BOM else TextEncoding.UTF_8,
            newlineStyle = detectNewlineStyle(raw),
            signature = FileSignature(sizeBytes, lastModifiedEpochMs),
            truncated = truncated,
            sizeBytes = sizeBytes,
        )
    }

    fun encode(
        text: String,
        encoding: TextEncoding,
        newlineStyle: NewlineStyle,
    ): ByteArray {
        val normalized = normalizeNewlines(text)
        val content = if (newlineStyle == NewlineStyle.LF) {
            normalized
        } else {
            normalized.replace("\n", newlineStyle.value)
        }
        val utf8 = content.toByteArray(StandardCharsets.UTF_8)
        return if (encoding == TextEncoding.UTF_8_BOM) UTF_8_BOM + utf8 else utf8
    }

    private fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    private fun detectNewlineStyle(text: String): NewlineStyle {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\r') {
                return if (index + 1 < text.length && text[index + 1] == '\n') {
                    NewlineStyle.CRLF
                } else {
                    NewlineStyle.CR
                }
            }
            if (char == '\n') return NewlineStyle.LF
            index++
        }
        return NewlineStyle.LF
    }
}
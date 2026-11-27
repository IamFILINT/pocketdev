package dev.pocketdev.core.editor

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
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
        val raw = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        val newlineStyles = detectNewlineStyles(raw)
        return TextFileSnapshot(
            text = normalizeNewlines(raw),
            encoding = if (hasBom) TextEncoding.UTF_8_BOM else TextEncoding.UTF_8,
            newlineStyle = newlineStyles.firstOrNull() ?: NewlineStyle.LF,
            newlineStyles = newlineStyles,
            signature = FileSignature.fromBytes(bytes, lastModifiedEpochMs, sizeBytes),
            truncated = truncated,
            sizeBytes = sizeBytes,
        )
    }

    fun encode(
        text: String,
        encoding: TextEncoding,
        newlineStyle: NewlineStyle,
        newlineStyles: List<NewlineStyle> = emptyList(),
    ): ByteArray {
        val content = buildString(text.length) {
            var boundary = 0
            text.forEach { char ->
                if (char == '\n') {
                    append((newlineStyles.getOrNull(boundary) ?: newlineStyle).value)
                    boundary++
                } else {
                    append(char)
                }
            }
        }
        val utf8 = content.toByteArray(StandardCharsets.UTF_8)
        return if (encoding == TextEncoding.UTF_8_BOM) UTF_8_BOM + utf8 else utf8
    }

    private fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    private fun detectNewlineStyles(text: String): List<NewlineStyle> {
        val styles = mutableListOf<NewlineStyle>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        styles.add(NewlineStyle.CRLF)
                        index += 2
                    } else {
                        styles.add(NewlineStyle.CR)
                        index++
                    }
                }
                '\n' -> {
                    styles.add(NewlineStyle.LF)
                    index++
                }
                else -> index++
            }
        }
        return styles
    }
}
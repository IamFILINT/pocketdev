package dev.pocketdev.core.editor

enum class TextEncoding(val label: String) {
    UTF_8("UTF-8"),
    UTF_8_BOM("UTF-8 with BOM"),
}

enum class NewlineStyle(val value: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

data class FileSignature(
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)

data class TextFileSnapshot(
    val text: String,
    val encoding: TextEncoding,
    val newlineStyle: NewlineStyle,
    val signature: FileSignature,
    val truncated: Boolean,
    val sizeBytes: Long,
)
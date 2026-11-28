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
    val contentSha256: String,
) {
    companion object {
        fun fromBytes(
            bytes: ByteArray,
            lastModifiedEpochMs: Long,
            sizeBytes: Long = bytes.size.toLong(),
        ): FileSignature {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            val hex = "0123456789abcdef"
            val hash = buildString(digest.size * 2) {
                digest.forEach { byte ->
                    val value = byte.toInt() and 0xff
                    append(hex[value ushr 4])
                    append(hex[value and 0x0f])
                }
            }
            return FileSignature(sizeBytes, lastModifiedEpochMs, hash)
        }
    }
}

data class TextFileSnapshot(
    val text: String,
    val encoding: TextEncoding,
    val newlineStyle: NewlineStyle,
    val newlineStyles: List<NewlineStyle>,
    val signature: FileSignature,
    val truncated: Boolean,
    val sizeBytes: Long,
)
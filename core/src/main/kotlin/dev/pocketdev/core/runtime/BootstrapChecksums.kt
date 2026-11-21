package dev.pocketdev.core.runtime

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 helpers for bootstrap artifact verification.
 *
 * Verification happens before extraction and is compared in constant time so a
 * mismatched archive is deleted rather than partially trusted.
 */
object BootstrapChecksums {

    private const val BUFFER_SIZE = 64 * 1024

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun matches(expectedHex: String, actualHex: String): Boolean =
        MessageDigest.isEqual(
            expectedHex.lowercase().toByteArray(Charsets.US_ASCII),
            actualHex.lowercase().toByteArray(Charsets.US_ASCII),
        )

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
}

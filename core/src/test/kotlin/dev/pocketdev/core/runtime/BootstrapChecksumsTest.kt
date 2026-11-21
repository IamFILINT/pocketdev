package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootstrapChecksumsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `sha256 matches the well-known vector for abc`() {
        val file = tmp.newFile("abc.txt").apply { writeText("abc") }
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BootstrapChecksums.sha256(file),
        )
    }

    @Test
    fun `sha256 of stream equals sha256 of file`() {
        val file = tmp.newFile("data.bin").apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }
        assertEquals(BootstrapChecksums.sha256(file), file.inputStream().use(BootstrapChecksums::sha256))
    }

    @Test
    fun `matching compares case-insensitively`() {
        val digest = "AA".repeat(32)
        assertTrue(BootstrapChecksums.matches(digest, digest.lowercase()))
    }

    @Test
    fun `different digests never match`() {
        assertFalse(BootstrapChecksums.matches("aa".repeat(32), "ab".repeat(32)))
    }

    @Test(expected = java.io.FileNotFoundException::class)
    fun `missing file fails loudly`() {
        BootstrapChecksums.sha256(File(tmp.root, "absent.zip"))
    }
}

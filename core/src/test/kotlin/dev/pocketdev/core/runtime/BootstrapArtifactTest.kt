package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapArtifactTest {

    private fun artifact(
        releaseTag: String = "bootstrap-2026.11.15-r1",
        fileName: String = "bootstrap-aarch64.zip",
        downloadUrl: String = "https://example.invalid/bootstrap-aarch64.zip",
        sha256: String = "a".repeat(64),
        sizeBytes: Long = 30_000_000,
        abi: Abi = Abi.AARCH64,
        minApiLevel: Int = 24,
        maxApiLevel: Int? = null,
    ) = BootstrapArtifact(
        releaseTag = releaseTag,
        fileName = fileName,
        downloadUrl = downloadUrl,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        abi = abi,
        minApiLevel = minApiLevel,
        maxApiLevel = maxApiLevel,
    )

    @Test
    fun `valid artifact is accepted`() {
        val a = artifact()
        assertEquals("bootstrap-2026.11.15-r1", a.releaseTag)
        assertEquals(Abi.AARCH64, a.abi)
    }

    @Test
    fun `rejects non https download url`() {
        val failure = runCatching { artifact(downloadUrl = "http://example.invalid/x.zip") }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `rejects malformed sha256`() {
        listOf(
            "A".repeat(64), // uppercase
            "a".repeat(63), // too short
            "g".repeat(64), // non-hex
            "",
        ).forEach { bad ->
            val failure = runCatching { artifact(sha256 = bad) }
            assertTrue("expected rejection for '$bad'", failure.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `rejects non positive size`() {
        val failure = runCatching { artifact(sizeBytes = 0) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `rejects file names that are paths`() {
        val failure = runCatching { artifact(fileName = "../bootstrap.zip") }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `rejects incoherent api range`() {
        val failure = runCatching { artifact(minApiLevel = 34, maxApiLevel = 30) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `supports is true only for matching abi and api range`() {
        val a = artifact(minApiLevel = 29, maxApiLevel = 35)
        assertTrue(a.supports(Abi.AARCH64, 29))
        assertTrue(a.supports(Abi.AARCH64, 35))
        assertFalse(a.supports(Abi.AARCH64, 36))
        assertFalse(a.supports(Abi.AARCH64, 28))
        assertFalse(a.supports(Abi.X86_64, 32))
    }

    @Test
    fun `manifest resolves the artifact for the device`() {
        val manifest = BootstrapManifest(
            releaseTag = "bootstrap-2026.11.15-r1",
            artifacts = listOf(
                artifact(fileName = "bootstrap-aarch64.zip", abi = Abi.AARCH64),
                artifact(fileName = "bootstrap-x86_64.zip", abi = Abi.X86_64),
            ),
        )
        assertEquals(Abi.AARCH64, manifest.artifactFor(Abi.AARCH64, 30)?.abi)
        assertEquals(Abi.X86_64, manifest.artifactFor(Abi.X86_64, 30)?.abi)
    }

    @Test
    fun `manifest returns null when no artifact covers the device`() {
        val manifest = BootstrapManifest(
            releaseTag = "r1",
            artifacts = listOf(
                artifact(releaseTag = "r1", minApiLevel = 34, maxApiLevel = 36),
            ),
        )
        assertNull(manifest.artifactFor(Abi.AARCH64, 30))
    }

    @Test
    fun `manifest rejects duplicate artifacts for one abi`() {
        val failure = runCatching {
            BootstrapManifest(
                releaseTag = "r1",
                artifacts = listOf(artifact(), artifact(fileName = "other.zip")),
            )
        }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `manifest rejects empty artifact list`() {
        val failure = runCatching { BootstrapManifest("r1", emptyList()) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `manifest rejects artifacts from a different release`() {
        val failure = runCatching {
            BootstrapManifest(
                releaseTag = "r1",
                artifacts = listOf(artifact(releaseTag = "r2")),
            )
        }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }
}

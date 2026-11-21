package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapPinsTest {

    @Test
    fun `pinned release exposes one audited artifact per supported ABI`() {
        assertEquals(2, BootstrapPins.manifest.artifacts.size)
        assertEquals(BootstrapPins.aarch64, BootstrapPins.manifest.artifactFor(Abi.AARCH64, 30))
        assertEquals(BootstrapPins.x86_64, BootstrapPins.manifest.artifactFor(Abi.X86_64, 30))
        assertEquals(BootstrapPins.RELEASE_TAG, BootstrapPins.aarch64.releaseTag)
    }

    @Test
    fun `artifact supports the product minimum and current API levels`() {
        assertTrue(BootstrapPins.aarch64.supports(Abi.AARCH64, 29))
        assertTrue(BootstrapPins.aarch64.supports(Abi.AARCH64, 37))
    }

    @Test
    fun `x86_64 pin exists only for the emulator spike and is a release asset too`() {
        val url = BootstrapPins.x86_64.downloadUrl
        assertTrue(url.startsWith("https://github.com/termux/termux-packages/releases/download/"))
        assertTrue(url.endsWith("/bootstrap-x86_64.zip"))
        assertEquals(
            "fb95765f2ffb4cf5648891199dedb820ed19a63334352fb0bc69b7b7c917e002",
            BootstrapPins.X86_64_SHA256,
        )
        assertEquals(33_058_188L, BootstrapPins.X86_64_SIZE_BYTES)
    }

    @Test
    fun `download url is the audited release asset`() {
        val url = BootstrapPins.aarch64.downloadUrl
        assertTrue(url.startsWith("https://github.com/termux/termux-packages/releases/download/"))
        assertTrue(url.endsWith("/bootstrap-aarch64.zip"))
        assertTrue(url.contains("bootstrap-2026.11.15-r1%2Bapt.android-7"))
    }

    @Test
    fun `pinned hash and size are the ones measured from the release asset`() {
        assertEquals(
            "388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7",
            BootstrapPins.AARCH64_SHA256,
        )
        assertEquals(33_148_806L, BootstrapPins.AARCH64_SIZE_BYTES)
        assertNotNull(BootstrapPins.aarch64)
    }
}

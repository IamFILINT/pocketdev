package dev.pocketdev.core.agent

import dev.pocketdev.core.runtime.Abi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodePinsTest {

    @Test
    fun pinIsSelfConsistent() {
        val pin = OpenCodePins.aarch64
        assertEquals(OpenCodePins.RELEASE_TAG, pin.releaseTag)
        assertEquals(OpenCodePins.SHA256, pin.sha256)
        assertEquals(OpenCodePins.SIZE_BYTES, pin.sizeBytes)
        assertEquals(Abi.AARCH64, pin.abi)
        assertTrue(pin.downloadUrl.contains(OpenCodePins.RELEASE_TAG))
        assertTrue(pin.downloadUrl.endsWith("/${pin.fileName}"))
    }

    @Test
    fun supportsArm64FromApi29() {
        val pin = OpenCodePins.aarch64
        assertTrue(pin.supports(Abi.AARCH64, 29))
        assertTrue(pin.supports(Abi.AARCH64, 36))
        assertFalse(pin.supports(Abi.AARCH64, 28))
        assertFalse(pin.supports(Abi.X86_64, 36))
    }
}

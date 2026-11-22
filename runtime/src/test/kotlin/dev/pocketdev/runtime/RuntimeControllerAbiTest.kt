package dev.pocketdev.runtime

import dev.pocketdev.core.runtime.Abi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeControllerAbiTest {

    @Test
    fun prefersAarch64WhenSupported() {
        assertEquals(Abi.AARCH64, RuntimeController.abiFor(listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test
    fun mapsX8664() {
        assertEquals(Abi.X86_64, RuntimeController.abiFor(listOf("x86_64")))
    }

    @Test
    fun rejectsAbisWithoutPinnedBootstrap() {
        assertNull(RuntimeController.abiFor(listOf("armeabi-v7a", "x86")))
    }
}

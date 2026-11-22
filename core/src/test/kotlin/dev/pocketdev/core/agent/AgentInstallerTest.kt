package dev.pocketdev.core.agent

import dev.pocketdev.core.runtime.Abi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgentInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val payload = "bionic-bun-agent-binary".toByteArray()
    private val sha = dev.pocketdev.core.runtime.BootstrapChecksums.sha256(payload.inputStream())

    private fun artifact(sha256: String = sha): AgentArtifact = AgentArtifact(
        releaseTag = "v-test",
        fileName = "opencode",
        downloadUrl = "https://example.invalid/opencode",
        sha256 = sha256,
        sizeBytes = payload.size.toLong(),
        abi = Abi.AARCH64,
        minApiLevel = 29,
        upstreamVersion = "test",
        license = "MIT",
    )

    @Test
    fun installsVerifiedBinary() {
        val states = mutableListOf<AgentState>()
        val installer = AgentInstaller(tmp.newFolder("agent"), { _, destination, _, onProgress ->
            destination.writeBytes(payload)
            onProgress(payload.size.toLong())
        }, states::add)

        val result = installer.install(artifact())

        assertTrue(result is AgentState.Ready)
        assertEquals("test", (result as AgentState.Ready).version)
        assertTrue(installer.binary.isFile)
        assertTrue(installer.binary.canExecute())
        assertFalse(File(installer.binary.parentFile, "opencode.part").exists())
        assertTrue(states.any { it is AgentState.Verifying })
        assertTrue(installer.isInstalled(artifact()))
    }

    @Test
    fun checksumMismatchFailsClosed() {
        val installer = AgentInstaller(tmp.newFolder("agent"), { _, destination, _, _ ->
            destination.writeBytes(payload)
        })

        val result = installer.install(artifact(sha256 = "0".repeat(64)))

        assertTrue(result is AgentState.Failed)
        assertFalse(installer.binary.exists())
        assertFalse(File(installer.binary.parentFile, "opencode.part").exists())
    }

    @Test
    fun resumesFromPartialFile() {
        val dir = tmp.newFolder("agent")
        val head = payload.copyOfRange(0, 5)
        File(dir, "opencode.part").writeBytes(head)
        var requestedOffset = -1L
        val installer = AgentInstaller(dir, { _, destination, offset, _ ->
            requestedOffset = offset
            destination.appendBytes(payload.copyOfRange(offset.toInt(), payload.size))
        })

        val result = installer.install(artifact())

        assertTrue(result is AgentState.Ready)
        assertEquals(head.size.toLong(), requestedOffset)
        assertTrue(installer.binary.readBytes().contentEquals(payload))
    }
}

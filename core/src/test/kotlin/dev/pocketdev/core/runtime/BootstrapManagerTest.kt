package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BootstrapManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        cacheDir = tmp.newFolder("cache")
    }

    private fun payload(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            fun entry(name: String, content: String? = null) {
                out.putNextEntry(ZipEntry(name))
                content?.let { out.write(it.toByteArray(Charsets.UTF_8)) }
                out.closeEntry()
            }
            entry("bin/")
            entry("bin/bash", "#!/system/bin/sh\n")
            entry("bin/pkg", "pkg\n")
            entry("bin/apt", "apt\n")
            entry("etc/apt/", null)
            entry("etc/apt/sources.list", "deb https://example.test stable main\n")
            entry("SYMLINKS.txt", "bash←./bin/sh\n")
        }
        return bytes.toByteArray()
    }

    private fun sha(payload: ByteArray): String = BootstrapChecksums.sha256(payload.inputStream())

    private fun manifest(payload: ByteArray, sha256: String = sha(payload)): BootstrapManifest {
        val artifact = BootstrapArtifact(
            releaseTag = "test-2026.01.01",
            fileName = "bootstrap-test.zip",
            downloadUrl = "https://example.test/bootstrap-test.zip",
            sha256 = sha256,
            sizeBytes = payload.size.toLong(),
            abi = Abi.AARCH64,
            minApiLevel = 24,
        )
        return BootstrapManifest("test-2026.01.01", listOf(artifact))
    }

    private class FakeDownloader(
        private val payload: ByteArray,
        private val failAt: Int? = null,
    ) : BootstrapDownloader {
        var calls = 0
        var lastOffset = -1L

        override fun download(
            artifact: BootstrapArtifact,
            destination: File,
            offset: Long,
            onProgress: (Long) -> Unit,
        ) {
            calls++
            lastOffset = offset
            val end = failAt ?: payload.size
            var position = offset.toInt()
            FileOutputStream(destination, true).use { out ->
                while (position < end) {
                    val chunk = minOf(4096, end - position)
                    out.write(payload, position, chunk)
                    position += chunk
                    onProgress(position.toLong())
                }
            }
            if (failAt != null) throw IOException("simulated interruption")
        }
    }

    @Test
    fun `happy path downloads, verifies, extracts and reports ready`() {
        val payload = payload()
        val downloader = FakeDownloader(payload)
        val manager = BootstrapManager(filesDir, cacheDir, downloader)

        val state = manager.provision(manifest(payload), Abi.AARCH64, 30)

        assertTrue("unexpected state: $state", state is RuntimeState.Ready)
        assertEquals(1, downloader.calls)
        assertEquals(0L, downloader.lastOffset)
        assertTrue(File(filesDir, "usr/bin/bash").isFile)
        assertTrue(File(filesDir, "usr/bin/sh").exists())
        assertFalse(File(filesDir, "usr/SYMLINKS.txt").exists())
        assertTrue(manager.journalStore.read()!!.completed)
        assertFalse(manager.staging.exists())
        assertFalse(manager.backup.exists())
        assertFalse(cacheDir.listFiles()!!.any { it.name.endsWith(".part") })
    }

    @Test
    fun `second run is idempotent and does not redownload`() {
        val payload = payload()
        val downloader = FakeDownloader(payload)
        val manager = BootstrapManager(filesDir, cacheDir, downloader)

        assertTrue(manager.provision(manifest(payload), Abi.AARCH64, 30) is RuntimeState.Ready)
        assertTrue(manager.provision(manifest(payload), Abi.AARCH64, 30) is RuntimeState.Ready)
        assertEquals(1, downloader.calls)
    }

    @Test
    fun `interrupted download resumes from the partial file`() {
        val payload = payload()
        val half = payload.size / 2
        val first = BootstrapManager(filesDir, cacheDir, FakeDownloader(payload, failAt = half))
        val failed = first.provision(manifest(payload), Abi.AARCH64, 30)
        assertTrue(failed is RuntimeState.Failed)
        assertEquals(RuntimeState.Stage.DOWNLOAD, (failed as RuntimeState.Failed).stage)
        val part = cacheDir.listFiles()!!.single()
        assertEquals(half.toLong(), part.length())

        val resumed = FakeDownloader(payload)
        val second = BootstrapManager(filesDir, cacheDir, resumed)
        val state = second.provision(manifest(payload), Abi.AARCH64, 30)

        assertTrue("unexpected state: $state", state is RuntimeState.Ready)
        assertEquals(half.toLong(), resumed.lastOffset)
        assertTrue(File(filesDir, "usr/bin/bash").isFile)
    }

    @Test
    fun `checksum mismatch fails closed, deletes the artifact and leaves the prefix untouched`() {
        val payload = payload()
        val manager = BootstrapManager(filesDir, cacheDir, FakeDownloader(payload))

        val state = manager.provision(manifest(payload, sha256 = "0".repeat(64)), Abi.AARCH64, 30)

        assertTrue(state is RuntimeState.Failed)
        assertEquals(RuntimeState.Stage.VERIFY, (state as RuntimeState.Failed).stage)
        assertFalse(File(filesDir, "usr").exists())
        assertFalse(cacheDir.listFiles()!!.any { it.name.endsWith(".part") })
        assertFalse(manager.journalStore.read()!!.completed)
    }

    @Test
    fun `insufficient storage aborts before any download`() {
        val payload = payload()
        val downloader = FakeDownloader(payload)
        val manager = BootstrapManager(filesDir, cacheDir, downloader, freeBytes = { 1L })

        val state = manager.provision(manifest(payload), Abi.AARCH64, 30)

        assertTrue(state is RuntimeState.Failed)
        assertEquals(RuntimeState.Stage.CHECK, (state as RuntimeState.Failed).stage)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun `unsupported abi fails at the device check`() {
        val payload = payload()
        val manager = BootstrapManager(filesDir, cacheDir, FakeDownloader(payload))

        val state = manager.provision(manifest(payload), Abi.X86_64, 30)

        assertTrue(state is RuntimeState.Failed)
        assertEquals(RuntimeState.Stage.CHECK, (state as RuntimeState.Failed).stage)
    }

    @Test
    fun `failed post-swap health check restores the previous runtime`() {
        val payload = payload()
        val oldPrefix = File(filesDir, "usr").apply { mkdirs() }
        File(oldPrefix, "marker").writeText("old runtime")

        var calls = 0
        val health = PrefixHealthCheck {
            val passed = calls++ != 1
            BootstrapHealthReport(listOf(BootstrapHealthReport.Check("fake", passed, "call ${calls - 1}")))
        }
        val manager = BootstrapManager(filesDir, cacheDir, FakeDownloader(payload), health = health)

        val state = manager.provision(manifest(payload), Abi.AARCH64, 30)

        assertTrue(state is RuntimeState.Failed)
        assertEquals(RuntimeState.Stage.HEALTH_CHECK, (state as RuntimeState.Failed).stage)
        assertEquals("old runtime", File(oldPrefix, "marker").readText())
        assertFalse(manager.backup.exists())
    }

    @Test
    fun `healthy existing runtime is reported ready without touching the network`() {
        val payload = payload()
        val prefix = File(filesDir, "usr").apply { mkdirs() }
        listOf("bin/bash", "bin/pkg", "bin/apt", "etc/apt/sources.list").forEach {
            File(prefix, it).apply { parentFile.mkdirs(); writeText("x") }
        }
        File(prefix, "bin/bash").setExecutable(true)
        File(prefix, "bin/pkg").setExecutable(true)
        File(prefix, "bin/apt").setExecutable(true)
        BootstrapJournalStore(File(filesDir, "state/runtime.json")).write(
            BootstrapJournal(version = "test-2026.01.01", completed = true),
        )

        val downloader = FakeDownloader(payload)
        val manager = BootstrapManager(filesDir, cacheDir, downloader)
        val state = manager.provision(manifest(payload), Abi.AARCH64, 30)

        assertTrue("unexpected state: $state", state is RuntimeState.Ready)
        assertEquals(0, downloader.calls)
    }
}

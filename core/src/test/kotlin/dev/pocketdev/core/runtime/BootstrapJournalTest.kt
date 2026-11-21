package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootstrapJournalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): BootstrapJournalStore =
        BootstrapJournalStore(File(tmp.root, "state/runtime.json"))

    @Test
    fun `missing journal reads as null`() {
        assertNull(store().read())
    }

    @Test
    fun `journal round-trips every field`() {
        val journal = BootstrapJournal(
            version = "bootstrap-test",
            stage = RuntimeState.Stage.DOWNLOAD,
            sha256 = "ab".repeat(32),
            bytesDownloaded = 1234,
            completed = false,
        )
        val store = store()
        store.write(journal)
        assertEquals(journal, store.read())
    }

    @Test
    fun `completed journal survives a rewrite`() {
        val store = store()
        store.write(BootstrapJournal(version = "v1", stage = RuntimeState.Stage.HEALTH_CHECK, completed = true))
        val read = store.read()
        assertTrue(read!!.completed)
        assertEquals("v1", read.version)
    }

    @Test
    fun `corrupt journal is treated as absent`() {
        val file = File(tmp.root, "state/runtime.json")
        file.parentFile!!.mkdirs()
        file.writeText("this is not a journal\n")
        assertNull(BootstrapJournalStore(file).read())
    }

    @Test
    fun `unknown stage is treated as absent`() {
        val file = File(tmp.root, "state/runtime.json")
        file.parentFile!!.mkdirs()
        file.writeText("stage=TELEPORT\ncompleted=true\n")
        assertNull(BootstrapJournalStore(file).read())
    }

    @Test
    fun `no temp file is left behind`() {
        val store = store()
        store.write(BootstrapJournal(version = "v1"))
        val leftovers = File(tmp.root, "state").listFiles()!!.map { it.name }
        assertEquals(listOf("runtime.json"), leftovers)
    }
}

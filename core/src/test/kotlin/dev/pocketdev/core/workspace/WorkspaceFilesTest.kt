package dev.pocketdev.core.workspace

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFilesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var files: WorkspaceFiles

    private fun setUpWorkspace() {
        root = temporaryFolder.newFolder()
        files = WorkspaceFiles(root)
    }

    @Test
    fun listSortsDirectoriesFirstAndHonorsHiddenToggle() = runBlocking {
        setUpWorkspace()
        File(root, "b.txt").writeText("b")
        File(root, "a").mkdirs()
        File(root, ".hidden").writeText("hidden")

        val visible = files.list().getOrNull()!!.entries.map { it.name }
        val all = files.list(showHidden = true).getOrNull()!!.entries.map { it.name }

        assertEquals(listOf("a", "b.txt"), visible)
        assertEquals(listOf("a", "b.txt", ".hidden"), all)
    }

    @Test
    fun createRenameAndDeleteOperateInsideRoot() = runBlocking {
        setUpWorkspace()

        val created = files.create("", "notes.txt", directory = false).getOrNull()!!
        assertEquals("notes.txt", created)
        assertTrue(File(root, "notes.txt").exists())

        files.rename("notes.txt", "renamed.txt").getOrNull()!!
        assertFalse(File(root, "notes.txt").exists())
        assertEquals("hello", File(root, "renamed.txt").apply { writeText("hello") }.readText())

        files.delete("renamed.txt").getOrNull()!!
        assertFalse(File(root, "renamed.txt").exists())
    }

    @Test
    fun unsafeNamesAndRootDeletionAreRejected() = runBlocking {
        setUpWorkspace()

        assertTrue(files.create("", "../escape.txt", directory = false) is WorkspaceResult.Failure)
        assertTrue(files.create("", "nested/file.txt", directory = false) is WorkspaceResult.Failure)
        assertTrue(files.delete("") is WorkspaceResult.Failure)
        assertTrue(root.exists())
    }

    @Test
    fun symlinksThatEscapeTheRootAreIgnored() = runBlocking {
        setUpWorkspace()
        val outside = temporaryFolder.newFile("outside.txt").apply { writeText("secret") }
        Files.createSymbolicLink(File(root, "outside-link").toPath(), outside.toPath())

        val names = files.list().getOrNull()!!.entries.map { it.name }
        assertFalse(names.contains("outside-link"))
        assertTrue(files.preview("outside-link") is WorkspaceResult.Failure)
    }

    @Test
    fun copyAndMovePreserveFileContent() = runBlocking {
        setUpWorkspace()
        files.create("", "src", directory = true).getOrNull()!!
        File(root, "src/data.txt").writeText("payload")

        assertEquals("copies/data.txt", files.copy("src/data.txt", "copies").getOrNull()!!)
        assertEquals("payload", File(root, "copies/data.txt").readText())

        assertEquals("moved/data.txt", files.move("src/data.txt", "moved").getOrNull()!!)
        assertFalse(File(root, "src/data.txt").exists())
        assertEquals("payload", File(root, "moved/data.txt").readText())
    }

    @Test
    fun recursiveDeleteReportsProgressForEveryEntry() = runBlocking {
        setUpWorkspace()
        files.create("", "src", directory = true).getOrNull()!!
        File(root, "src/nested").mkdirs()
        repeat(3) { File(root, "src/nested/file$it.txt").writeText("x") }

        val progress = mutableListOf<Pair<Long, Long>>()
        val result = files.delete("src") { completed, total -> progress.add(completed to total) }

        assertTrue(result is WorkspaceResult.Success)
        assertFalse(File(root, "src").exists())
        assertEquals(5, progress.size)
        assertEquals(5L, progress.last().first)
        assertEquals(5L, progress.last().second)
    }

    @Test
    fun recursiveCopyCanBeCancelledAndCleansUpDestination() = runBlocking {
        setUpWorkspace()
        files.create("", "src", directory = true).getOrNull()!!
        repeat(20) { File(root, "src/file$it.txt").writeText("payload") }

        val progress = mutableListOf<Long>()
        val job = launch(Dispatchers.Default) {
            files.copy("src", "copies", onProgress = { completed, _ ->
                progress.add(completed)
                if (completed == 1L) cancel()
            })
        }
        job.join()

        assertTrue(progress.isNotEmpty())
        assertFalse(File(root, "copies").exists())
    }

    @Test
    fun copyStreamReportsPartialFailureExplicitly() = runBlocking {
        setUpWorkspace()
        var written = 0
        val failingOutput = object : OutputStream() {
            override fun write(byte: Int) {
                if (written >= 3) throw IOException("disk full")
                written++
            }
        }

        val result = files.copyStream(
            input = ByteArrayInputStream(ByteArray(16) { 1 }),
            output = failingOutput,
            totalBytes = 16,
        )

        assertTrue(result is WorkspaceResult.PartialFailure)
        val partial = result as WorkspaceResult.PartialFailure
        assertEquals("disk full", partial.message)
        assertEquals(3L, partial.completed)
        assertEquals(16L, partial.total)
    }

    @Test
    fun searchFindsMatchesAndSkipsHiddenEntries() = runBlocking {
        setUpWorkspace()
        files.create("", "src", directory = true).getOrNull()!!
        File(root, "src/Note.txt").writeText("note")
        File(root, ".secret-note").writeText("hidden")

        val visible = files.search("note").getOrNull()!!
        val all = files.search("note", showHidden = true).getOrNull()!!

        assertEquals(listOf("Note.txt"), visible.map { it.name })
        assertEquals(listOf("Note.txt", ".secret-note"), all.map { it.name })
    }

    @Test
    fun previewReadsTextAndRejectsBinaryContent() = runBlocking {
        setUpWorkspace()
        File(root, "text.txt").writeText("readable", StandardCharsets.UTF_8)
        File(root, "binary.bin").writeBytes(byteArrayOf(1, 2, 0, 4))

        assertEquals("readable", files.preview("text.txt").getOrNull()!!)
        assertNull(files.preview("binary.bin").getOrNull())
        assertNotNull(files.preview("missing.txt") as? WorkspaceResult.Failure)
    }
}

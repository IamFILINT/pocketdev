package dev.pocketdev.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

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
    fun listSortsDirectoriesFirstAndHonorsHiddenToggle() {
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
    fun createRenameAndDeleteOperateInsideRoot() {
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
    fun unsafeNamesAndRootDeletionAreRejected() {
        setUpWorkspace()

        assertTrue(files.create("", "../escape.txt", directory = false) is WorkspaceResult.Failure)
        assertTrue(files.create("", "nested/file.txt", directory = false) is WorkspaceResult.Failure)
        assertTrue(files.delete("") is WorkspaceResult.Failure)
        assertTrue(root.exists())
    }

    @Test
    fun symlinksThatEscapeTheRootAreIgnored() {
        setUpWorkspace()
        val outside = temporaryFolder.newFile("outside.txt").apply { writeText("secret") }
        Files.createSymbolicLink(File(root, "outside-link").toPath(), outside.toPath())

        val names = files.list().getOrNull()!!.entries.map { it.name }
        assertFalse(names.contains("outside-link"))
        assertTrue(files.preview("outside-link") is WorkspaceResult.Failure)
    }

    @Test
    fun copyAndMovePreserveFileContent() {
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
    fun searchFindsMatchesAndSkipsHiddenEntries() {
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
    fun previewReadsTextAndRejectsBinaryContent() {
        setUpWorkspace()
        File(root, "text.txt").writeText("readable", StandardCharsets.UTF_8)
        File(root, "binary.bin").writeBytes(byteArrayOf(1, 2, 0, 4))

        assertEquals("readable", files.preview("text.txt").getOrNull()!!)
        assertNull(files.preview("binary.bin").getOrNull())
        assertNotNull(files.preview("missing.txt") as? WorkspaceResult.Failure)
    }
}

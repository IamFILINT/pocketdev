package dev.pocketdev.core.workspace

import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.NewlineStyle
import dev.pocketdev.core.editor.TextEncoding
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFilesEditorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `text files round-trip with encoding and newline style`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root)
        val original = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "a\r\nb".toByteArray(StandardCharsets.UTF_8)
        File(root, "main.kt").writeBytes(original)

        val loaded = files.readTextFile("main.kt").getOrNull()!!
        assertEquals("a\nb", loaded.text)
        assertEquals(TextEncoding.UTF_8_BOM, loaded.encoding)
        assertEquals(NewlineStyle.CRLF, loaded.newlineStyle)
        assertFalse(loaded.truncated)

        val written = files.writeTextFile(
            relativePath = "main.kt",
            text = "a\nb\nc",
            encoding = loaded.encoding,
            newlineStyle = loaded.newlineStyle,
            expectedSignature = loaded.signature,
        ).getOrNull()!!

        assertEquals(
            FileSignature(File(root, "main.kt").length(), File(root, "main.kt").lastModified()),
            written,
        )
        assertEquals(
            (byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "a\r\nb\r\nc".toByteArray(StandardCharsets.UTF_8)).toList(),
            File(root, "main.kt").readBytes().toList(),
        )
    }

    @Test
    fun `stale signature prevents overwriting externally changed files`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root)
        val path = File(root, "notes.txt")
        path.writeText("first")

        val loaded = files.readTextFile("notes.txt").getOrNull()!!
        path.writeText("changed by agent")
        path.setLastModified(loaded.signature.lastModifiedEpochMs + 60_000L)

        val result = files.writeTextFile(
            relativePath = "notes.txt",
            text = "local edit",
            encoding = loaded.encoding,
            newlineStyle = loaded.newlineStyle,
            expectedSignature = loaded.signature,
        )

        assertTrue(result is WorkspaceResult.Failure)
        assertEquals("changed by agent", path.readText())
    }

    @Test
    fun `binary files and large files are handled safely`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root)
        File(root, "binary.dat").writeBytes(byteArrayOf(0x61, 0x00, 0x62))
        File(root, "large.txt").writeText("0123456789")

        assertTrue(files.readTextFile("binary.dat") is WorkspaceResult.Failure)
        val large = files.readTextFile("large.txt", maxBytes = 4).getOrNull()!!
        assertEquals("0123", large.text)
        assertTrue(large.truncated)
        assertEquals(10L, large.sizeBytes)
    }
}
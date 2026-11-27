package dev.pocketdev.core.workspace

import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.NewlineStyle
import dev.pocketdev.core.editor.TextEncoding
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.async
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
            FileSignature.fromBytes(
                File(root, "main.kt").readBytes(),
                File(root, "main.kt").lastModified(),
            ),
            written,
        )
        assertEquals(
            (byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "a\r\nb\r\nc".toByteArray(StandardCharsets.UTF_8)).toList(),
            File(root, "main.kt").readBytes().toList(),
        )
    }

    @Test
    fun `mixed newline file round trips byte for byte`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root)
        val original = "a\r\nb\rc\nd".toByteArray(StandardCharsets.UTF_8)
        val file = File(root, "mixed.txt")
        file.writeBytes(original)
        val snapshot = files.readTextFile("mixed.txt").getOrNull()!!

        val result = files.writeTextFile(
            relativePath = "mixed.txt",
            text = snapshot.text,
            encoding = snapshot.encoding,
            newlineStyle = snapshot.newlineStyle,
            newlineStyles = snapshot.newlineStyles,
            expectedSignature = snapshot.signature,
        )

        assertTrue(result is WorkspaceResult.Success)
        assertTrue(original.contentEquals(file.readBytes()))
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
    fun `same-size external replacement with restored timestamp is detected`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root)
        val path = File(root, "notes.txt")
        path.writeText("aaaa")
        val loaded = files.readTextFile("notes.txt").getOrNull()!!
        val originalModified = path.lastModified()

        path.writeText("bbbb")
        path.setLastModified(originalModified)

        val result = files.writeTextFile(
            relativePath = "notes.txt",
            text = "local edit",
            encoding = loaded.encoding,
            newlineStyle = loaded.newlineStyle,
            expectedSignature = loaded.signature,
        )

        assertTrue(result is WorkspaceResult.Failure)
        assertEquals("bbbb", path.readText())
    }

    @Test
    fun `staging failure preserves the original file and cleans temporary files`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root, beforeStagingWrite = { throw IOException("staging failed") })
        val path = File(root, "notes.txt")
        path.writeText("original")

        val result = files.writeTextFile(
            relativePath = "notes.txt",
            text = "replacement",
            encoding = TextEncoding.UTF_8,
            newlineStyle = NewlineStyle.LF,
        )

        assertTrue(result is WorkspaceResult.Failure)
        assertEquals("original", path.readText())
        assertTrue(root.listFiles()!!.none { it.name.contains(".pocketdev-") })
    }

    @Test
    fun `commit failure preserves the original file and cleans temporary files`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root, beforeCommit = { throw IOException("commit failed") })
        val path = File(root, "notes.txt")
        path.writeText("original")

        val result = files.writeTextFile(
            relativePath = "notes.txt",
            text = "replacement",
            encoding = TextEncoding.UTF_8,
            newlineStyle = NewlineStyle.LF,
        )

        assertTrue(result is WorkspaceResult.Failure)
        assertEquals("original", path.readText())
        assertTrue(root.listFiles()!!.none { it.name.contains(".pocketdev-") })
    }

    @Test
    fun `concurrent writes are serialized and leave complete content`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val files = WorkspaceFiles(root)
        val path = File(root, "race.txt")
        path.writeText("initial")

        val first = async {
            files.writeTextFile(
                relativePath = "race.txt",
                text = "first-complete-value",
                encoding = TextEncoding.UTF_8,
                newlineStyle = NewlineStyle.LF,
            )
        }
        val second = async {
            files.writeTextFile(
                relativePath = "race.txt",
                text = "second-complete-value",
                encoding = TextEncoding.UTF_8,
                newlineStyle = NewlineStyle.LF,
            )
        }
        assertTrue(first.await() is WorkspaceResult.Success)
        assertTrue(second.await() is WorkspaceResult.Success)

        val finalText = path.readText()
        assertTrue(finalText == "first-complete-value" || finalText == "second-complete-value")
        assertTrue(root.listFiles()!!.none { it.name.contains(".pocketdev-") })
    }

    @Test
    fun `symlinked editor paths are rejected`() = runBlocking {
        val root = temporaryFolder.newFolder()
        val outside = temporaryFolder.newFolder("outside")
        val files = WorkspaceFiles(root)
        val target = File(outside, "target.txt")
        target.writeText("outside-original")
        val link = File(root, "link.txt")
        Files.createSymbolicLink(link.toPath(), target.toPath())

        assertTrue(files.readTextFile("link.txt") is WorkspaceResult.Failure)
        val result = files.writeTextFile(
            relativePath = "link.txt",
            text = "replacement",
            encoding = TextEncoding.UTF_8,
            newlineStyle = NewlineStyle.LF,
        )
        assertTrue(result is WorkspaceResult.Failure)
        assertEquals("outside-original", target.readText())
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
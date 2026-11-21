package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BootstrapArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray?>): File {
        val file = tmp.newFile("bootstrap-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream().buffered()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                if (content != null) out.write(content)
                out.closeEntry()
            }
        }
        return file
    }

    private fun text(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

    private fun emptyDir(name: String): File = tmp.newFolder(name)

    @Test
    fun `extracts entries, consumes SYMLINKS and marks bin executable`() {
        val archive = zip(
            "bin/" to null,
            "bin/bash" to text("#!/system/bin/sh\n"),
            "etc/apt/sources.list" to text("deb https://example.test stable main\n"),
            "SYMLINKS.txt" to text("bash←./bin/sh\n"),
        )
        val destination = emptyDir("prefix")

        val result = BootstrapArchive.extract(archive, destination)

        assertEquals(4, result.entries)
        assertEquals(1, result.symlinksCreated)
        assertTrue(File(destination, "bin/bash").isFile)
        assertTrue(Files.isExecutable(File(destination, "bin/bash").toPath()))
        assertTrue(Files.isSymbolicLink(File(destination, "bin/sh").toPath()))
        assertEquals("bash", Files.readSymbolicLink(File(destination, "bin/sh").toPath()).toString())
        assertFalse(File(destination, BootstrapArchive.SYMLINKS_FILE).exists())
    }

    @Test
    fun `rejects traversal entries and writes nothing`() {
        val archive = zip("../evil.txt" to text("x"))
        val destination = emptyDir("prefix")

        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, destination)
        }
        assertFalse(File(destination.parentFile, "evil.txt").exists())
    }

    @Test
    fun `rejects absolute entries`() {
        val archive = zip("/etc/passwd" to text("x"))
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"))
        }
    }

    @Test
    fun `rejects backslash traversal`() {
        val archive = zip("bin\\..\\evil.sh" to text("x"))
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"))
        }
    }

    @Test
    fun `rejects non-empty destination`() {
        val archive = zip("bin/bash" to text("x"))
        val destination = emptyDir("prefix")
        File(destination, "existing.txt").writeText("keep me")

        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, destination)
        }
        assertEquals("keep me", File(destination, "existing.txt").readText())
    }

    @Test
    fun `rejects symlink whose target escapes the prefix`() {
        val archive = zip(
            "bin/bash" to text("x"),
            "SYMLINKS.txt" to text("../../../../etc/passwd←./bin/sh\n"),
        )
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"))
        }
    }

    @Test
    fun `rejects symlink with absolute target`() {
        val archive = zip(
            "bin/bash" to text("x"),
            "SYMLINKS.txt" to text("/system/bin/sh←./bin/sh\n"),
        )
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"))
        }
    }

    @Test
    fun `absolute Termux-prefix targets become relative links inside the destination`() {
        val archive = zip(
            "share/termux-keyring/key.gpg" to text("k"),
            "SYMLINKS.txt" to text(
                "${BootstrapArchive.TERMUX_DEFAULT_PREFIX}/share/termux-keyring/key.gpg←./share/pacman/keyrings/key.gpg\n",
            ),
        )
        val destination = emptyDir("usr.staging")

        BootstrapArchive.extract(archive, destination)

        val link = File(destination, "share/pacman/keyrings/key.gpg")
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertFalse(Files.readSymbolicLink(link.toPath()).isAbsolute)
        assertTrue(link.canonicalFile.isFile)
        assertTrue(link.canonicalPath.startsWith(destination.canonicalPath + File.separator))
    }

    @Test
    fun `rejects malformed symlink lines`() {
        val archive = zip("SYMLINKS.txt" to text("no-separator-here\n"))
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"))
        }
    }

    @Test
    fun `enforces the entry-count limit`() {
        val archive = zip(
            "a.txt" to text("a"),
            "b.txt" to text("b"),
        )
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"), BootstrapLimits(maxEntries = 1))
        }
    }

    @Test
    fun `enforces the per-entry size limit`() {
        val archive = zip("big.bin" to ByteArray(4096))
        assertThrows(BootstrapArchiveException::class.java) {
            BootstrapArchive.extract(archive, emptyDir("prefix"), BootstrapLimits(maxEntryBytes = 1024))
        }
    }

    @Test
    fun `progress reports every entry once`() {
        val archive = zip("a.txt" to text("a"), "b.txt" to text("b"))
        val seen = ArrayList<Int>()
        BootstrapArchive.extract(archive, emptyDir("prefix")) { done, _ -> seen += done }
        assertEquals(listOf(1, 2), seen)
    }
}

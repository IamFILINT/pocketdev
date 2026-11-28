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
import java.nio.file.Files

class PathSafetyTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `normal relative paths are safe`() {
        assertTrue(PathSafety.isSafeRelativePath("src"))
        assertTrue(PathSafety.isSafeRelativePath("src/main/App.kt"))
        assertTrue(PathSafety.isSafeRelativePath("a/b/c.txt"))
    }

    @Test
    fun `absolute and drive paths are unsafe`() {
        assertFalse(PathSafety.isSafeRelativePath("/etc/passwd"))
        assertFalse(PathSafety.isSafeRelativePath("C:/Windows"))
        assertFalse(PathSafety.isSafeRelativePath("\\server\\share"))
    }

    @Test
    fun `traversal and empty segments are unsafe`() {
        assertFalse(PathSafety.isSafeRelativePath(".."))
        assertFalse(PathSafety.isSafeRelativePath("../secret"))
        assertFalse(PathSafety.isSafeRelativePath("a/../../b"))
        assertFalse(PathSafety.isSafeRelativePath("a//b"))
        assertFalse(PathSafety.isSafeRelativePath("./a"))
        assertFalse(PathSafety.isSafeRelativePath(""))
    }

    @Test
    fun `null bytes are unsafe`() {
        assertFalse(PathSafety.isSafeRelativePath("a\u0000b"))
    }

    @Test
    fun `resolveWithin resolves legitimate children`() {
        val root = temporaryFolder.newFolder("root")
        val resolved = PathSafety.resolveWithin(root, "src/main/App.kt")
        assertNotNull(resolved)
        assertEquals(File(root.canonicalFile, "src/main/App.kt").path, resolved!!.path)
    }

    @Test
    fun `resolveWithin rejects escaping paths`() {
        val root = temporaryFolder.newFolder("root")
        assertNull(PathSafety.resolveWithin(root, "../outside.txt"))
        assertNull(PathSafety.resolveWithin(root, "/etc/passwd"))
    }

    @Test
    fun `resolveWithin stays inside root even via traversal with separators`() {
        val root = temporaryFolder.newFolder("root")
        assertNull(PathSafety.resolveWithin(root, "a/../../../b"))
    }

    @Test
    fun `resolveWithinNoFollow rejects symlinked files`() {
        val root = temporaryFolder.newFolder("root")
        val outside = temporaryFolder.newFolder("outside")
        val insideTarget = File(root, "inside.txt")
        insideTarget.writeText("safe")
        val insideLink = File(root, "inside-link.txt")
        Files.createSymbolicLink(insideLink.toPath(), insideTarget.toPath())
        val outsideTarget = File(outside, "secret.txt")
        outsideTarget.writeText("secret")
        val outsideLink = File(root, "outside-link.txt")
        Files.createSymbolicLink(outsideLink.toPath(), outsideTarget.toPath())

        assertNull(PathSafety.resolveWithinNoFollow(root, "inside-link.txt"))
        assertNull(PathSafety.resolveWithinNoFollow(root, "outside-link.txt"))
    }

    @Test
    fun `resolveWithinNoFollow rejects symlinked directory components`() {
        val root = temporaryFolder.newFolder("root")
        val outside = temporaryFolder.newFolder("outside")
        val insideDirectory = File(root, "real")
        assertTrue(insideDirectory.mkdirs())
        File(insideDirectory, "inside.txt").writeText("safe")
        val insideLink = File(root, "inside-dir-link")
        Files.createSymbolicLink(insideLink.toPath(), insideDirectory.toPath())
        val outsideLink = File(root, "outside-dir-link")
        Files.createSymbolicLink(outsideLink.toPath(), outside.toPath())

        assertNull(PathSafety.resolveWithinNoFollow(root, "inside-dir-link/inside.txt"))
        assertNull(PathSafety.resolveWithinNoFollow(root, "outside-dir-link/secret.txt"))
    }

    @Test
    fun `resolveWithinNoFollow accepts regular files and directories`() {
        val root = temporaryFolder.newFolder("root")
        val file = File(root, "regular.txt")
        file.writeText("safe")
        val directory = File(root, "sub")
        assertTrue(directory.mkdirs())

        assertEquals(file.canonicalFile.path, PathSafety.resolveWithinNoFollow(root, "regular.txt")!!.path)
        assertEquals(
            directory.canonicalFile.path,
            PathSafety.resolveWithinNoFollow(root, "sub")!!.path,
        )
    }

    @Test
    fun `toSafeSegment sanitizes unsafe characters`() {
        assertEquals("my_app", PathSafety.toSafeSegment("my/app"))
        assertEquals("app", PathSafety.toSafeSegment(".app"))
        assertEquals("project", PathSafety.toSafeSegment("..."))
        assertEquals("project", PathSafety.toSafeSegment("   "))
    }
}
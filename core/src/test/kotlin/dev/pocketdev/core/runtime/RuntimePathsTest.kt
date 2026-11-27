package dev.pocketdev.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimePathsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun paths(): RuntimePaths = RuntimePaths(tmp.root)

    @Test
    fun `prefix is exactly usr under files`() {
        assertEquals(File(tmp.root, "usr"), paths().prefix)
    }

    @Test
    fun `project data never lives inside the prefix`() {
        val p = paths()
        assertFalse(p.projects.canonicalPath.startsWith(p.prefix.canonicalPath + File.separator))
        assertFalse(p.state.canonicalPath.startsWith(p.prefix.canonicalPath + File.separator))
        assertFalse(p.drafts.canonicalPath.startsWith(p.prefix.canonicalPath + File.separator))
        assertFalse(p.drafts.canonicalPath.startsWith(p.projects.canonicalPath + File.separator))
    }

    @Test
    fun `home projects path matches the bootstrap layout doc`() {
        val p = paths()
        assertEquals(File(File(tmp.root, "home"), "projects"), p.projects)
        assertEquals(File(File(tmp.root, "state"), "runtime.json"), p.journal)
        assertEquals(File(File(tmp.root, "state"), "drafts"), p.drafts)
    }

    @Test
    fun `ensure creates only project and state directories`() {
        val p = paths()
        p.ensureBaseDirectories()
        assertTrue(p.projects.isDirectory)
        assertTrue(p.state.isDirectory)
        assertTrue(p.drafts.isDirectory)
        assertFalse(p.prefix.exists())
    }
}

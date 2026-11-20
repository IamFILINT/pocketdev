package dev.pocketdev.core.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectStoreTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private fun store(): ProjectStore = ProjectStore(temporaryFolder.newFolder("workspace"))

    private fun project(id: String, name: String = "App $id") = Project(
        id = id,
        name = name,
        rootPath = "/data/projects/$id",
        createdAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun `empty store returns no projects`() {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun `added project round-trips`() {
        val store = store()
        val project = project("one")
        store.add(project)
        assertEquals(listOf(project), store.load())
    }

    @Test
    fun `adding the same id replaces the entry`() {
        val store = store()
        store.add(project("one", name = "First"))
        store.add(project("one", name = "Second"))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("Second", loaded.single().name)
    }

    @Test
    fun `remove deletes only the matching project`() {
        val store = store()
        store.add(project("one"))
        store.add(project("two"))
        store.remove("one")
        assertEquals(listOf("two"), store.load().map { it.id })
    }

    @Test
    fun `values with tabs and newlines survive round-trip`() {
        val store = store()
        val tricky = Project(
            id = "id\t1",
            name = "line\nbreak\\slash",
            rootPath = "/data/tab\there",
            createdAtEpochMs = 42L,
        )
        store.add(tricky)
        assertEquals(listOf(tricky), store.load())
    }

    @Test
    fun `duplicate ids in the file are collapsed on load`() {
        val dir = temporaryFolder.newFolder("dupes")
        val file = java.io.File(dir, "projects.tsv")
        file.writeText(
            "a\tApp A\t/p/a\t1\n" +
                "a\tApp A duplicate\t/p/a2\t2\n" +
                "b\tApp B\t/p/b\t3\n",
        )
        val loaded = ProjectStore(dir).load()
        assertEquals(listOf("a", "b"), loaded.map { it.id })
        assertEquals("App A", loaded.first().name)
    }

    @Test
    fun `malformed lines are skipped`() {
        val dir = temporaryFolder.newFolder("malformed")
        java.io.File(dir, "projects.tsv").writeText(
            "not-enough-columns\n" +
                "b\tApp B\t/p/b\t3\n",
        )
        val loaded = ProjectStore(dir).load()
        assertEquals(listOf("b"), loaded.map { it.id })
    }

    @Test
    fun `replaceAll persists the full list`() {
        val store = store()
        store.replaceAll(listOf(project("a"), project("b")))
        assertEquals(listOf("a", "b"), store.load().map { it.id })
    }
}
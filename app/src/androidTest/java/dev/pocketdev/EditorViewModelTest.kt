package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.ui.editor.EditorViewModel
import dev.pocketdev.ui.projects.CreateProjectResult
import dev.pocketdev.ui.projects.ProjectsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {

    private lateinit var app: PocketDevApplication
    private lateinit var projectsViewModel: ProjectsViewModel
    private lateinit var editorViewModel: EditorViewModel
    private lateinit var projectsRoot: File
    private lateinit var projectRoot: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        projectsViewModel = ProjectsViewModel(app)
        projectsRoot = app.container.projectsRoot
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()
        val created = projectsViewModel.createProject("editor-test")
        assertTrue(created is CreateProjectResult.Created)
        val project = projectsViewModel.uiState.value.projects.first { it.name == "editor-test" }
        app.container.selectProject(project.id)
        projectRoot = File(project.rootPath)
        editorViewModel = EditorViewModel(app)
    }

    @After
    fun tearDown() {
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
    }

    private fun await(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(
            "Condition was not met within ${timeoutMs}ms: ${editorViewModel.uiState.value}",
            condition(),
        )
    }

    private fun awaitIdle() {
        await { editorViewModel.uiState.value.busyLabel == null }
    }

    @Test
    fun opensEditsAndSavesTextFileThroughWorkspace() {
        val file = File(projectRoot, "main.kt")
        file.writeText("val a = 1\n")

        editorViewModel.open("main.kt")
        await {
            editorViewModel.uiState.value.activePath == "main.kt" &&
                editorViewModel.uiState.value.text == "val a = 1\n"
        }

        val updated = "val a = 1\nval b = 2\n"
        editorViewModel.onTextChanged(updated, 0..updated.length)
        assertTrue(editorViewModel.uiState.value.dirty)
        assertEquals("Kotlin", editorViewModel.uiState.value.tabs.first().language.label)

        editorViewModel.save()
        awaitIdle()
        assertFalse(editorViewModel.uiState.value.dirty)
        assertEquals(updated, file.readText())
    }

    @Test
    fun dirtyExternalChangeIsPresentedAsConflictUntilResolved() {
        val file = File(projectRoot, "notes.txt")
        file.writeText("local source")

        editorViewModel.open("notes.txt")
        await { editorViewModel.uiState.value.activePath == "notes.txt" }
        editorViewModel.onTextChanged("local edit", 0.."local edit".length)
        assertTrue(editorViewModel.uiState.value.dirty)

        file.writeText("agent changed this")
        file.setLastModified(file.lastModified() + 60_000L)
        editorViewModel.checkExternalChanges()
        await { editorViewModel.uiState.value.externalChange != null }

        val conflict = editorViewModel.uiState.value.externalChange
        assertNotNull(conflict)
        assertTrue(conflict!!.dirty)
        assertEquals("notes.txt", conflict.path)

        editorViewModel.keepLocalChanges()
        awaitIdle()
        assertNull(editorViewModel.uiState.value.externalChange)
        assertEquals("local edit", file.readText())
    }

    @Test
    fun cleanExternalChangeReloadsAutomatically() {
        val file = File(projectRoot, "shared.txt")
        file.writeText("first version")

        editorViewModel.open("shared.txt")
        await { editorViewModel.uiState.value.activePath == "shared.txt" }

        file.writeText("second version")
        file.setLastModified(file.lastModified() + 60_000L)
        editorViewModel.checkExternalChanges()
        await { editorViewModel.uiState.value.text == "second version" }

        assertNull(editorViewModel.uiState.value.externalChange)
        assertFalse(editorViewModel.uiState.value.dirty)
    }

    @Test
    fun tabsSupportActivationAndDirtyCloseGuard() {
        File(projectRoot, "one.txt").writeText("one")
        File(projectRoot, "two.txt").writeText("two")

        editorViewModel.open("one.txt")
        await { editorViewModel.uiState.value.activePath == "one.txt" }
        editorViewModel.open("two.txt")
        await { editorViewModel.uiState.value.activePath == "two.txt" && editorViewModel.uiState.value.tabs.size == 2 }

        editorViewModel.activateTab("one.txt")
        await { editorViewModel.uiState.value.activePath == "one.txt" }

        editorViewModel.onTextChanged("changed", 0.."changed".length)
        editorViewModel.closeTab("one.txt")
        assertEquals("one.txt", editorViewModel.uiState.value.activePath)
        assertNotNull(editorViewModel.uiState.value.error)

        editorViewModel.discardChanges()
        awaitIdle()
        editorViewModel.closeTab("one.txt")
        await { editorViewModel.uiState.value.tabs.none { tab -> tab.path == "one.txt" } }
        assertEquals("two.txt", editorViewModel.uiState.value.activePath)
    }

    @Test
    fun searchReplaceAndFormatAreEditableStateOperations() {
        val file = File(projectRoot, "search.txt")
        file.writeText("foo foo\n")

        editorViewModel.open("search.txt")
        await { editorViewModel.uiState.value.activePath == "search.txt" }

        editorViewModel.setSearchVisible(true)
        editorViewModel.setSearchQuery("foo")
        await { editorViewModel.uiState.value.matches.size == 2 }

        editorViewModel.setReplaceText("bar")
        editorViewModel.replaceAll()
        awaitIdle()
        assertEquals("bar bar\n", editorViewModel.uiState.value.text)

        editorViewModel.formatActive()
        awaitIdle()
        assertEquals("bar bar\n", editorViewModel.uiState.value.text)

        editorViewModel.save()
        awaitIdle()
        assertEquals("bar bar\n", file.readText())
    }

    @Test
    fun binaryAndMissingFilesReportExplicitFailures() {
        File(projectRoot, "binary.dat").writeBytes(byteArrayOf(0x00, 0x01))

        editorViewModel.open("binary.dat")
        await { editorViewModel.uiState.value.error != null }
        assertTrue(editorViewModel.uiState.value.error!!.contains("Binary"))

        editorViewModel.clearFeedback()
        editorViewModel.open("missing.txt")
        await { editorViewModel.uiState.value.error != null }
        assertTrue(editorViewModel.uiState.value.error!!.contains("Not found"))
    }
}
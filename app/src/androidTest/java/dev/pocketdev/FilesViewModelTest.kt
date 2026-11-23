package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.ui.files.FilesViewModel
import dev.pocketdev.ui.projects.ProjectsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FilesViewModelTest {

    private lateinit var app: PocketDevApplication
    private lateinit var projectsViewModel: ProjectsViewModel
    private lateinit var filesViewModel: FilesViewModel
    private lateinit var projectsRoot: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        projectsViewModel = ProjectsViewModel(app)
        projectsRoot = app.container.projectsRoot
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()
        filesViewModel = FilesViewModel(app)
    }

    @After
    fun tearDown() {
        projectsViewModel.clearRegistry()
        projectsRoot.deleteRecursively()
    }

    private fun createProject(name: String): Project {
        assertTrue(projectsViewModel.createProject(name) is dev.pocketdev.ui.projects.CreateProjectResult.Created)
        return projectsViewModel.uiState.value.projects.first { it.name == name }
    }

    @Test
    fun createsAndListsEntriesInSelectedProject() {
        val project = createProject("files-basic")
        filesViewModel.selectProject(project.id)

        filesViewModel.create("", "src", directory = true)
        filesViewModel.create("", "main.kt", directory = false)

        assertEquals(
            listOf("src", "main.kt"),
            filesViewModel.uiState.value.entries.map { it.name },
        )
        assertTrue(File(project.rootPath, "src").isDirectory)
        assertTrue(File(project.rootPath, "main.kt").isFile)
    }

    @Test
    fun renameCopyMoveAndDeleteOperateOnSelectedProject() {
        val project = createProject("files-actions")
        filesViewModel.selectProject(project.id)
        filesViewModel.create("", "data.txt", directory = false)
        File(project.rootPath, "data.txt").writeText("payload")
        filesViewModel.refresh()
        val original = filesViewModel.uiState.value.entries.first { it.name == "data.txt" }

        filesViewModel.copy(original, "data-copy.txt")
        assertTrue(File(project.rootPath, "data-copy.txt").exists())

        filesViewModel.move(original, "moved.txt")
        assertFalse(File(project.rootPath, "data.txt").exists())
        assertTrue(File(project.rootPath, "moved.txt").exists())

        val moved = filesViewModel.uiState.value.entries.first { it.name == "moved.txt" }
        filesViewModel.delete(moved)
        assertFalse(File(project.rootPath, "moved.txt").exists())
        assertEquals("Deleted moved.txt", filesViewModel.uiState.value.message)
    }

    @Test
    fun searchHonorsHiddenToggle() {
        val project = createProject("files-search")
        filesViewModel.selectProject(project.id)
        filesViewModel.create("", "visible.txt", directory = false)
        filesViewModel.create("", ".secret.txt", directory = false)

        filesViewModel.setQuery("txt")
        assertEquals(listOf("visible.txt"), filesViewModel.uiState.value.entries.map { it.name })

        filesViewModel.toggleHidden()
        assertEquals(
            setOf("visible.txt", ".secret.txt"),
            filesViewModel.uiState.value.entries.map { it.name }.toSet(),
        )
        assertTrue(File(project.rootPath, ".secret.txt").exists())
    }

    @Test
    fun openReadsTextPreviewFromSelectedProject() {
        val project = createProject("files-preview")
        filesViewModel.selectProject(project.id)
        filesViewModel.create("", "notes.txt", directory = false)
        File(project.rootPath, "notes.txt").writeText("readable preview")
        filesViewModel.refresh()
        val entry = filesViewModel.uiState.value.entries.first { it.name == "notes.txt" }

        filesViewModel.open(entry)

        assertEquals("notes.txt", filesViewModel.uiState.value.previewPath)
        assertEquals("readable preview", filesViewModel.uiState.value.preview)
    }
}

package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.ui.projects.CreateProjectResult
import dev.pocketdev.ui.projects.ProjectsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectsViewModelTest {

    private lateinit var app: PocketDevApplication
    private lateinit var viewModel: ProjectsViewModel
    private lateinit var projectsRoot: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = ProjectsViewModel(app)
        projectsRoot = app.container.projectsRoot
        viewModel.clearRegistry()
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()
        viewModel.refresh()
    }

    @After
    fun tearDown() {
        viewModel.clearRegistry()
        projectsRoot.deleteRecursively()
    }

    @Test
    fun createProjectWithValidNameCreatesDirectoryAndRegistryEntry() {
        val result = viewModel.createProject("hello-world")

        assertEquals(CreateProjectResult.Created, result)
        assertEquals(1, viewModel.uiState.value.projects.size)
        val project = viewModel.uiState.value.projects.first()
        assertEquals("hello-world", project.name)
        assertTrue(File(project.rootPath).isDirectory)
        assertTrue(File(project.rootPath).canonicalPath.startsWith(projectsRoot.canonicalPath))
    }

    @Test
    fun createProjectWithInvalidNameReturnsInvalid() {
        val result = viewModel.createProject("")

        assertTrue(result is CreateProjectResult.Invalid)
        assertEquals(0, viewModel.uiState.value.projects.size)
    }

    @Test
    fun createProjectWithDuplicateNameAllocatesSuffix() {
        viewModel.createProject("dup")
        val second = viewModel.createProject("dup")

        assertEquals(CreateProjectResult.Created, second)
        assertEquals(2, viewModel.uiState.value.projects.size)
        val names = viewModel.uiState.value.projects.map { File(it.rootPath).name }
        assertTrue(names.contains("dup"))
        assertTrue(names.contains("dup-2"))
    }

    @Test
    fun deleteProjectRemovesDirectoryAndRegistryEntry() {
        viewModel.createProject("to-delete")
        val project = viewModel.uiState.value.projects.first()

        viewModel.deleteProject(project)

        assertEquals(0, viewModel.uiState.value.projects.size)
        assertTrue(!File(project.rootPath).exists())
    }

    @Test
    fun deleteProjectDoesNotDeleteDirectoryOutsideRoot() {
        val outside = File(System.getProperty("java.io.tmpdir"), "pocketdev-rogue-${System.nanoTime()}")
        outside.mkdirs()
        val rogue = Project(
            id = "rogue",
            name = "rogue",
            rootPath = outside.absolutePath,
            createdAtEpochMs = 0L,
        )
        app.container.projectStore.add(rogue)
        viewModel.refresh()

        viewModel.deleteProject(rogue)

        assertEquals(0, viewModel.uiState.value.projects.size)
        assertTrue(outside.exists()) // directory outside root must survive
        outside.deleteRecursively()
    }
}

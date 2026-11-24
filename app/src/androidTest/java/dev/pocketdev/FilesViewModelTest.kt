package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.ui.files.FilesOperationStatus
import dev.pocketdev.ui.files.FilesViewModel
import dev.pocketdev.ui.projects.CreateProjectResult
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
        assertTrue(projectsViewModel.createProject(name) is CreateProjectResult.Created)
        return projectsViewModel.uiState.value.projects.first { it.name == name }
    }

    private fun await(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }

    private fun awaitSuccess(label: String) {
        await {
            val operation = filesViewModel.uiState.value.operation
            operation?.label == label && operation.status == FilesOperationStatus.SUCCESS
        }
    }

    @Test
    fun createsAndListsEntriesInSelectedProject() {
        val project = createProject("files-basic")
        filesViewModel.selectProject(project.id)
        awaitSuccess("Switch project")

        filesViewModel.create("src", directory = true)
        awaitSuccess("Create src")
        filesViewModel.create("main.kt", directory = false)
        awaitSuccess("Create main.kt")

        assertEquals(
            listOf("src", "main.kt"),
            filesViewModel.uiState.value.entries.map { it.name },
        )
        assertTrue(File(project.rootPath, "src").isDirectory)
        assertTrue(File(project.rootPath, "main.kt").isFile)
    }

    @Test
    fun externalProjectSwitchDoesNotRetargetDisplayedFileOperations() {
        val first = createProject("files-first")
        val second = createProject("files-second")
        filesViewModel.selectProject(first.id)
        awaitSuccess("Switch project")

        app.container.selectProject(second.id)
        filesViewModel.create("stays-in-first.txt", directory = false)
        awaitSuccess("Create stays-in-first.txt")

        assertTrue(File(first.rootPath, "stays-in-first.txt").isFile)
        assertFalse(File(second.rootPath, "stays-in-first.txt").exists())
        assertEquals(File(second.rootPath).canonicalPath, filesViewModel.uiState.value.rootPath)
    }

    @Test
    fun renameCopyMoveAndDeleteOperateOnSelectedProject() {
        val project = createProject("files-actions")
        filesViewModel.selectProject(project.id)
        awaitSuccess("Switch project")
        filesViewModel.create("data.txt", directory = false)
        awaitSuccess("Create data.txt")
        File(project.rootPath, "data.txt").writeText("payload")
        filesViewModel.refresh()
        awaitSuccess("Refresh")
        val original = filesViewModel.uiState.value.entries.first { it.name == "data.txt" }

        filesViewModel.copy(original, "data-copy.txt")
        awaitSuccess("Copy data.txt")
        assertTrue(File(project.rootPath, "data-copy.txt").exists())

        filesViewModel.move(original, "moved.txt")
        awaitSuccess("Move data.txt")
        assertFalse(File(project.rootPath, "data.txt").exists())
        assertTrue(File(project.rootPath, "moved.txt").exists())

        val moved = filesViewModel.uiState.value.entries.first { it.name == "moved.txt" }
        filesViewModel.delete(moved)
        awaitSuccess("Delete moved.txt")
        assertFalse(File(project.rootPath, "moved.txt").exists())
        assertEquals("Deleted moved.txt", filesViewModel.uiState.value.message)
    }

    @Test
    fun searchHonorsHiddenToggle() {
        val project = createProject("files-search")
        filesViewModel.selectProject(project.id)
        awaitSuccess("Switch project")
        filesViewModel.create("visible.txt", directory = false)
        awaitSuccess("Create visible.txt")
        filesViewModel.create(".secret.txt", directory = false)
        awaitSuccess("Create .secret.txt")

        filesViewModel.setQuery("txt")
        await {
            filesViewModel.uiState.value.entries.map { it.name } == listOf("visible.txt") &&
                filesViewModel.uiState.value.operation?.status == FilesOperationStatus.SUCCESS
        }
        assertEquals(listOf("visible.txt"), filesViewModel.uiState.value.entries.map { it.name })

        filesViewModel.toggleHidden()
        await {
            filesViewModel.uiState.value.entries.map { it.name }.toSet() ==
                setOf("visible.txt", ".secret.txt") &&
                filesViewModel.uiState.value.operation?.status == FilesOperationStatus.SUCCESS
        }
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
        awaitSuccess("Switch project")
        filesViewModel.create("notes.txt", directory = false)
        awaitSuccess("Create notes.txt")
        File(project.rootPath, "notes.txt").writeText("readable preview")
        filesViewModel.refresh()
        awaitSuccess("Refresh")
        val entry = filesViewModel.uiState.value.entries.first { it.name == "notes.txt" }

        filesViewModel.open(entry)
        awaitSuccess("Open notes.txt")

        assertEquals("notes.txt", filesViewModel.uiState.value.previewPath)
        assertEquals("readable preview", filesViewModel.uiState.value.preview)
    }

    @Test
    fun failedCreateReportsExplicitFailureState() {
        val project = createProject("files-failure")
        filesViewModel.selectProject(project.id)
        awaitSuccess("Switch project")
        filesViewModel.create("blocked", directory = true)
        awaitSuccess("Create blocked")

        filesViewModel.create("blocked", directory = true)

        await {
            val state = filesViewModel.uiState.value
            state.operation?.label == "Create blocked" &&
                state.operation.status == FilesOperationStatus.FAILURE &&
                state.error != null
        }
    }

    @Test
    fun cancellingOperationReportsCancelledState() {
        val project = createProject("files-cancel")
        filesViewModel.selectProject(project.id)
        awaitSuccess("Switch project")

        filesViewModel.refresh()
        filesViewModel.cancelOperation()

        await {
            val state = filesViewModel.uiState.value
            state.operation?.status == FilesOperationStatus.CANCELLED || state.operation == null
        }
    }
}

package dev.pocketdev.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.validation.ProjectNameValidator
import dev.pocketdev.core.validation.ValidationResult
import dev.pocketdev.core.workspace.PathSafety
import dev.pocketdev.core.workspace.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val projectsRoot: String = "",
)

sealed interface CreateProjectResult {
    data object Created : CreateProjectResult
    data class Invalid(val reason: ValidationResult.Reason) : CreateProjectResult
    data class Failed(val message: String) : CreateProjectResult
}

/**
 * Manages the project registry and the on-disk project directories.
 *
 * All file operations are deliberately simple and small for Milestone 1. They will
 * move to a background dispatcher when projects grow to include large trees.
 */
class ProjectsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container

    private val _uiState = MutableStateFlow(
        ProjectsUiState(projectsRoot = container.projectsRoot.absolutePath),
    )
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        container.syncSelectedProject()
        val projects = container.projectStore.load().sortedByDescending { it.createdAtEpochMs }
        _uiState.update {
            it.copy(
                projects = projects,
                projectsRoot = container.projectsRoot.absolutePath,
            )
        }
    }

    fun createProject(rawName: String, nowMs: Long = System.currentTimeMillis()): CreateProjectResult {
        val validation = ProjectNameValidator.validate(rawName)
        if (validation is ValidationResult.Invalid) {
            return CreateProjectResult.Invalid(validation.reason)
        }

        val segment = PathSafety.toSafeSegment(rawName)
        val directory = allocateDirectory(segment)
        if (!directory.exists() && !directory.mkdirs()) {
            return CreateProjectResult.Failed("Could not create project directory")
        }

        val project = Project(
            id = UUID.randomUUID().toString(),
            name = rawName,
            rootPath = directory.absolutePath,
            createdAtEpochMs = nowMs,
        )
        container.projectStore.add(project)
        container.selectProject(project.id)
        refresh()
        return CreateProjectResult.Created
    }

    /**
     * Deletes a project and its on-disk directory, but only when the directory is
     * provably inside the workspace root.
     */
    fun deleteProject(project: Project) {
        val root = container.projectsRoot.canonicalFile
        val target = File(project.rootPath).canonicalFile
        val insideRoot = target.path.startsWith(root.path + File.separator)
        if (insideRoot) {
            target.deleteRecursively()
        }
        container.projectStore.remove(project.id)
        refresh()
    }

    fun clearRegistry() {
        container.projectStore.replaceAll(emptyList())
        refresh()
    }

    private fun allocateDirectory(baseSegment: String): File {
        var candidate = File(container.projectsRoot, baseSegment)
        var index = 2
        while (candidate.exists()) {
            candidate = File(container.projectsRoot, "$baseSegment-$index")
            index += 1
        }
        return candidate
    }
}
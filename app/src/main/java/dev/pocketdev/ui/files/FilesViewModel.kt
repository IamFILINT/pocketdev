package dev.pocketdev.ui.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.workspace.FileEntry
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.core.workspace.WorkspaceFiles
import dev.pocketdev.core.workspace.WorkspaceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FilesUiState(
    val projects: List<Project> = emptyList(),
    val selectedProjectId: String? = null,
    val rootPath: String = "",
    val currentDirectory: String = "",
    val entries: List<FileEntry> = emptyList(),
    val showHidden: Boolean = false,
    val query: String = "",
    val previewPath: String? = null,
    val preview: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        val projects = container.projectStore.load().sortedByDescending { it.createdAtEpochMs }
        val selected = container.selectedProject.value
        val root = container.activeProjectRoot()
        val files = WorkspaceFiles(root)
        val entries: WorkspaceResult<List<FileEntry>> = if (state.query.isBlank()) {
            when (val result = files.list(state.currentDirectory, state.showHidden)) {
                is WorkspaceResult.Success -> WorkspaceResult.Success(result.value.entries)
                is WorkspaceResult.Failure -> result
            }
        } else {
            files.search(state.query, state.showHidden)
        }
        when (entries) {
            is WorkspaceResult.Success -> _uiState.update {
                it.copy(
                    projects = projects,
                    selectedProjectId = selected?.id,
                    rootPath = root.absolutePath,
                    entries = entries.value,
                    error = null,
                )
            }
            is WorkspaceResult.Failure -> _uiState.update {
                it.copy(
                    projects = projects,
                    selectedProjectId = selected?.id,
                    rootPath = root.absolutePath,
                    entries = emptyList(),
                    error = entries.message,
                )
            }
        }
    }

    fun selectProject(id: String?) {
        container.selectProject(id)
        _uiState.update {
            it.copy(
                currentDirectory = "",
                query = "",
                previewPath = null,
                preview = null,
            )
        }
        refresh()
    }

    fun openDirectory(relativePath: String) {
        _uiState.update {
            it.copy(
                currentDirectory = relativePath,
                query = "",
                previewPath = null,
                preview = null,
            )
        }
        refresh()
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectory
        val parent = current.substringBeforeLast('/', "")
        openDirectory(parent)
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query, previewPath = null, preview = null) }
        refresh()
    }

    fun toggleHidden() {
        _uiState.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun clearFeedback() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    fun create(name: String, directory: Boolean) {
        val current = _uiState.value.currentDirectory
        report(WorkspaceFiles(container.activeProjectRoot()).create(current, name, directory), "Created $name")
        refresh()
    }

    fun rename(entry: FileEntry, newName: String) {
        report(WorkspaceFiles(container.activeProjectRoot()).rename(entry.relativePath, newName), "Renamed to $newName")
        refresh()
    }

    fun delete(entry: FileEntry) {
        report(WorkspaceFiles(container.activeProjectRoot()).delete(entry.relativePath), "Deleted ${entry.name}")
        refresh()
    }

    fun copy(entry: FileEntry, newName: String? = null) {
        val current = _uiState.value.currentDirectory
        report(WorkspaceFiles(container.activeProjectRoot()).copy(entry.relativePath, current, newName), "Copied ${entry.name}")
        refresh()
    }

    fun move(entry: FileEntry, newName: String? = null) {
        val current = _uiState.value.currentDirectory
        report(WorkspaceFiles(container.activeProjectRoot()).move(entry.relativePath, current, newName), "Moved ${entry.name}")
        refresh()
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) {
            openDirectory(entry.relativePath)
            return
        }
        val result = WorkspaceFiles(container.activeProjectRoot()).preview(entry.relativePath)
        when (result) {
            is WorkspaceResult.Success -> _uiState.update {
                it.copy(previewPath = entry.relativePath, preview = result.value, error = null)
            }
            is WorkspaceResult.Failure -> _uiState.update {
                it.copy(previewPath = null, preview = null, error = result.message)
            }
        }
    }

    fun importDocument(uri: Uri, name: String) {
        val destination = WorkspaceFiles(container.activeProjectRoot())
            .destination(_uiState.value.currentDirectory, name)
        when (destination) {
            is WorkspaceResult.Failure -> report(destination, "")
            is WorkspaceResult.Success -> {
                val target = destination.value
                val copied = runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val input = resolver.openInputStream(uri) ?: error("Could not open source")
                    input.use { source -> target.outputStream().use { source.copyTo(it) } }
                }
                report(copied.fold(
                    onSuccess = { WorkspaceResult.Success(Unit) },
                    onFailure = { WorkspaceResult.Failure(it.message ?: "Import failed") },
                ), "Imported $name")
            }
        }
        refresh()
    }

    fun exportDocument(entry: FileEntry, uri: Uri) {
        val source = WorkspaceFiles(container.activeProjectRoot()).source(entry.relativePath)
        when (source) {
            is WorkspaceResult.Failure -> report(source, "")
            is WorkspaceResult.Success -> {
                val copied = runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val output = resolver.openOutputStream(uri) ?: error("Could not open destination")
                    output.use { target -> source.value.inputStream().use { it.copyTo(target) } }
                }
                report(copied.fold(
                    onSuccess = { WorkspaceResult.Success(Unit) },
                    onFailure = { WorkspaceResult.Failure(it.message ?: "Export failed") },
                ), "Exported ${entry.name}")
            }
        }
    }

    private fun report(result: WorkspaceResult<*>, successMessage: String) {
        when (result) {
            is WorkspaceResult.Success -> _uiState.update { it.copy(message = successMessage, error = null) }
            is WorkspaceResult.Failure -> _uiState.update { it.copy(message = null, error = result.message) }
        }
    }
}

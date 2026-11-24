package dev.pocketdev.ui.files

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.workspace.FileEntry
import dev.pocketdev.core.workspace.Project
import dev.pocketdev.core.workspace.WorkspaceFiles
import dev.pocketdev.core.workspace.WorkspaceResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FilesOperation(
    val label: String,
    val status: FilesOperationStatus = FilesOperationStatus.RUNNING,
    val completed: Long = 0,
    val total: Long = 0,
)

enum class FilesOperationStatus {
    RUNNING,
    SUCCESS,
    FAILURE,
    CANCELLED,
    PARTIAL_FAILURE,
}

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
    val operation: FilesOperation? = null,
)

private data class ListingData(
    val projects: List<Project>,
    val selectedProjectId: String?,
    val rootPath: String,
    val entries: List<FileEntry>,
    val error: String?,
)

private data class PreviewUpdate(val path: String?, val content: String?)

private data class OperationOutcome(
    val result: WorkspaceResult<*>,
    val listing: ListingData? = null,
    val previewUpdate: PreviewUpdate? = null,
    val successMessage: String? = null,
)

class FilesViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private var operationGeneration = 0

    init {
        refresh()
    }

    fun refresh() = runOperation("Refresh") { state, _ ->
        OperationOutcome(WorkspaceResult.Success(Unit), listing = loadListing(state))
    }

    fun selectProject(id: String?) {
        _uiState.update {
            it.copy(
                currentDirectory = "",
                query = "",
                previewPath = null,
                preview = null,
            )
        }
        runOperation("Switch project") { state, _ ->
            container.selectProject(id)
            OperationOutcome(WorkspaceResult.Success(Unit), listing = loadListing(state))
        }
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
        openDirectory(current.substringBeforeLast('/', ""))
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
        _uiState.update {
            it.copy(
                message = null,
                error = null,
                operation = it.operation?.takeIf { operation -> operation.status == FilesOperationStatus.RUNNING },
            )
        }
    }

    fun cancelOperation() {
        operationJob?.cancel()
        _uiState.update {
            it.copy(
                operation = it.operation?.copy(status = FilesOperationStatus.CANCELLED),
                message = null,
                error = "Operation cancelled",
            )
        }
    }

    fun create(name: String, directory: Boolean) =
        runOperation("Create $name") { state, _ ->
            val result = withDisplayedRoot(state) { files ->
                files.create(state.currentDirectory, name, directory)
            }
            OperationOutcome(result, loadListing(state), successMessage = "Created $name")
        }

    fun rename(entry: FileEntry, newName: String) =
        runOperation("Rename ${entry.name}") { state, _ ->
            val result = withDisplayedRoot(state) { files ->
                files.rename(entry.relativePath, newName)
            }
            OperationOutcome(result, loadListing(state), successMessage = "Renamed to $newName")
        }

    fun delete(entry: FileEntry) =
        runOperation("Delete ${entry.name}") { state, scope ->
            val result = withDisplayedRoot(state) { files ->
                files.delete(entry.relativePath) { completed, total -> scope.progress(completed, total) }
            }
            OperationOutcome(result, loadListing(state), successMessage = "Deleted ${entry.name}")
        }

    fun copy(entry: FileEntry, newName: String? = null) =
        runOperation("Copy ${entry.name}") { state, scope ->
            val result = withDisplayedRoot(state) { files ->
                files.copy(
                    relativePath = entry.relativePath,
                    targetDirectory = state.currentDirectory,
                    newName = newName,
                    onProgress = { completed, total -> scope.progress(completed, total) },
                )
            }
            OperationOutcome(result, loadListing(state), successMessage = "Copied ${entry.name}")
        }

    fun move(entry: FileEntry, newName: String? = null) =
        runOperation("Move ${entry.name}") { state, _ ->
            val result = withDisplayedRoot(state) { files ->
                files.move(entry.relativePath, state.currentDirectory, newName)
            }
            OperationOutcome(result, loadListing(state), successMessage = "Moved ${entry.name}")
        }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) {
            openDirectory(entry.relativePath)
            return
        }
        runOperation("Open ${entry.name}") { state, _ ->
            val result = withDisplayedRoot(state) { files -> files.preview(entry.relativePath) }
            val preview = when (result) {
                is WorkspaceResult.Success -> PreviewUpdate(entry.relativePath, result.value)
                is WorkspaceResult.Failure -> PreviewUpdate(null, null)
                is WorkspaceResult.PartialFailure -> PreviewUpdate(null, null)
            }
            OperationOutcome(result, previewUpdate = preview)
        }
    }

    fun importDocument(uri: Uri, suggestedName: String? = null) =
        runOperation("Import document") { state, scope ->
            val result = withDisplayedRoot(state) { files ->
                val name = resolveDisplayName(uri, suggestedName)
                when (val destination = files.destination(state.currentDirectory, name)) {
                    is WorkspaceResult.Success -> {
                        val resolver = getApplication<Application>().contentResolver
                        val source = resolver.openInputStream(uri)
                            ?: return@withDisplayedRoot WorkspaceResult.Failure("Could not open source")
                        source.use { input ->
                            destination.value.outputStream().use { output ->
                                asUnit(
                                    files.copyStream(input, output, onProgress = { completed, total ->
                                        scope.progress(completed, total)
                                    })
                                )
                            }
                        }
                    }
                    else -> destination
                }
            }
            val name = resolveDisplayName(uri, suggestedName)
            OperationOutcome(result, loadListing(state), successMessage = "Imported $name")
        }

    fun exportDocument(entry: FileEntry, uri: Uri) =
        runOperation("Export ${entry.name}") { state, scope ->
            val result = withDisplayedRoot(state) { files ->
                when (val source = files.source(entry.relativePath)) {
                    is WorkspaceResult.Success -> {
                        val resolver = getApplication<Application>().contentResolver
                        val output = resolver.openOutputStream(uri)
                            ?: return@withDisplayedRoot WorkspaceResult.Failure("Could not open destination")
                        output.use { target ->
                            source.value.inputStream().use { input ->
                                asUnit(
                                    files.copyStream(
                                        input = input,
                                        output = target,
                                        totalBytes = source.value.length(),
                                        onProgress = { completed, total -> scope.progress(completed, total) },
                                    )
                                )
                            }
                        }
                    }
                    else -> source
                }
            }
            OperationOutcome(result, successMessage = "Exported ${entry.name}")
        }

    override fun onCleared() {
        operationJob?.cancel()
        super.onCleared()
    }

    private fun runOperation(
        label: String,
        block: suspend (FilesUiState, OperationScope) -> OperationOutcome,
    ) {
        operationJob?.cancel()
        val token = ++operationGeneration
        val snapshot = _uiState.value
        val scope = OperationScope(token, label)
        val job = viewModelScope.launch(mainDispatcher) {
            _uiState.update {
                it.copy(
                    operation = FilesOperation(label),
                    message = null,
                    error = null,
                )
            }
            try {
                val outcome = withContext(ioDispatcher) { block(snapshot, scope) }
                if (token == operationGeneration) applyOutcome(outcome)
            } catch (cancelled: CancellationException) {
                if (token == operationGeneration) applyCancelled()
            } catch (error: Exception) {
                if (token == operationGeneration) applyFailure(error)
            } finally {
                if (token == operationGeneration) operationJob = null
            }
        }
        operationJob = job
    }

    private suspend fun loadListing(state: FilesUiState): ListingData {
        currentCoroutineContext().ensureActive()
        val projects = container.projectStore.load().sortedByDescending { it.createdAtEpochMs }
        val selected = container.selectedProject.value
        val context = runCatching { container.projectContext(selected) }
        val rootPath = context.getOrNull()?.rootPath ?: container.projectsRoot.absolutePath
        val listing: WorkspaceResult<List<FileEntry>> = context.getOrNull()?.let { projectContext ->
            val files = WorkspaceFiles(File(projectContext.rootPath))
            if (state.query.isBlank()) {
                when (val result = files.list(state.currentDirectory, state.showHidden)) {
                    is WorkspaceResult.Success -> WorkspaceResult.Success(result.value.entries)
                    is WorkspaceResult.Failure -> result
                    is WorkspaceResult.PartialFailure -> result
                }
            } else {
                files.search(state.query, state.showHidden)
            }
        } ?: WorkspaceResult.Failure(context.exceptionOrNull()?.message ?: "Invalid project root")
        val (entries, error) = when (listing) {
            is WorkspaceResult.Success -> listing.value to null
            is WorkspaceResult.Failure -> emptyList<FileEntry>() to listing.message
            is WorkspaceResult.PartialFailure -> emptyList<FileEntry>() to
                "${listing.message} (${listing.completed}/${listing.total})"
        }
        return ListingData(projects, selected?.id, rootPath, entries, error)
    }

    private suspend fun <T> withDisplayedRoot(
        state: FilesUiState,
        operation: suspend (WorkspaceFiles) -> WorkspaceResult<T>,
    ): WorkspaceResult<T> =
        try {
            operation(WorkspaceFiles(container.validatedRootPath(state.rootPath)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WorkspaceResult.Failure(error.message ?: "Invalid project root")
        }

    private fun resolveDisplayName(uri: Uri, suggestedName: String?): String {
        suggestedName?.let { return it }
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment ?: "imported"
    }

    private fun asUnit(result: WorkspaceResult<*>): WorkspaceResult<Unit> = when (result) {
        is WorkspaceResult.Success -> WorkspaceResult.Success(Unit)
        is WorkspaceResult.Failure -> result
        is WorkspaceResult.PartialFailure -> result
    }

    private fun applyOutcome(outcome: OperationOutcome) {
        val listing = outcome.listing
        val status = when (outcome.result) {
            is WorkspaceResult.Success -> FilesOperationStatus.SUCCESS
            is WorkspaceResult.Failure -> FilesOperationStatus.FAILURE
            is WorkspaceResult.PartialFailure -> FilesOperationStatus.PARTIAL_FAILURE
        }
        val message = (outcome.result as? WorkspaceResult.Success)?.let { outcome.successMessage }
        val error = when (val result = outcome.result) {
            is WorkspaceResult.Success -> listing?.error
            is WorkspaceResult.Failure -> result.message
            is WorkspaceResult.PartialFailure -> "${result.message} (${result.completed}/${result.total})"
        }
        _uiState.update { state ->
            var next = state.copy(
                message = message,
                error = error,
                operation = state.operation?.copy(status = status),
            )
            outcome.previewUpdate?.let { preview ->
                next = next.copy(previewPath = preview.path, preview = preview.content)
            }
            if (listing != null) {
                next = next.copy(
                    projects = listing.projects,
                    selectedProjectId = listing.selectedProjectId,
                    rootPath = listing.rootPath,
                    entries = listing.entries,
                )
            }
            next
        }
    }

    private fun applyCancelled() {
        _uiState.update {
            it.copy(
                operation = it.operation?.copy(status = FilesOperationStatus.CANCELLED),
                message = null,
                error = "Operation cancelled",
            )
        }
    }

    private fun applyFailure(error: Exception) {
        _uiState.update {
            it.copy(
                operation = it.operation?.copy(status = FilesOperationStatus.FAILURE),
                message = null,
                error = error.message ?: "Operation failed",
            )
        }
    }

    private inner class OperationScope(private val token: Int, private val label: String) {
        suspend fun progress(completed: Long, total: Long) {
            withContext(mainDispatcher) {
                if (token != operationGeneration) return@withContext
                _uiState.update { state ->
                    val operation = state.operation
                    if (operation?.label == label && operation.status == FilesOperationStatus.RUNNING) {
                        state.copy(operation = operation.copy(completed = completed, total = total))
                    } else {
                        state
                    }
                }
            }
        }
    }
}

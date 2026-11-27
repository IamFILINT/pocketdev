package dev.pocketdev.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketdev.PocketDevApplication
import dev.pocketdev.core.editor.EditorBuffer
import dev.pocketdev.core.editor.EditorDocumentId
import dev.pocketdev.core.editor.EditorFormatter
import dev.pocketdev.core.editor.EditorLanguage
import dev.pocketdev.core.editor.EditorSaveSnapshot
import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.NewlineStyle
import dev.pocketdev.core.editor.TextEncoding
import dev.pocketdev.core.editor.TextFileSnapshot
import dev.pocketdev.core.editor.TextMatch
import dev.pocketdev.core.editor.TextSearch
import dev.pocketdev.core.workspace.WorkspaceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorTabState(
    val path: String,
    val name: String,
    val language: EditorLanguage,
    val readOnly: Boolean,
)

data class ExternalChangeState(
    val path: String,
    val diskSignature: FileSignature,
    val dirty: Boolean,
)

data class EditorUiState(
    val tabs: List<EditorTabState> = emptyList(),
    val activePath: String? = null,
    val text: String = "",
    val selection: IntRange = 0..0,
    val dirty: Boolean = false,
    val readOnly: Boolean = false,
    val truncated: Boolean = false,
    val encoding: TextEncoding = TextEncoding.UTF_8,
    val newlineStyle: NewlineStyle = NewlineStyle.LF,
    val lineCount: Int = 1,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceText: String = "",
    val ignoreCase: Boolean = false,
    val wholeWord: Boolean = false,
    val matches: List<TextMatch> = emptyList(),
    val activeMatch: Int = -1,
    val message: String? = null,
    val error: String? = null,
    val externalChange: ExternalChangeState? = null,
    val busyLabel: String? = null,
)

private sealed interface EditorResult {
    data class Loaded(
        val path: String,
        val documentId: EditorDocumentId,
        val snapshot: TextFileSnapshot,
    ) : EditorResult

    data class Saved(
        val path: String,
        val documentId: EditorDocumentId,
        val revision: Long,
        val savedText: String,
        val signature: FileSignature,
    ) : EditorResult

    data class Reloaded(
        val path: String,
        val documentId: EditorDocumentId,
        val snapshot: TextFileSnapshot,
    ) : EditorResult

    data class Checked(
        val path: String,
        val documentId: EditorDocumentId,
        val snapshot: TextFileSnapshot,
    ) : EditorResult

    data class Error(val message: String) : EditorResult
}

class EditorViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val workspaceGateway: EditorWorkspaceGateway =
        DefaultEditorWorkspaceGateway((application as PocketDevApplication).container),
) : AndroidViewModel(application) {

    private val container = (application as PocketDevApplication).container
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val buffers = mutableMapOf<String, EditorBuffer>()
    private val snapshots = mutableMapOf<String, TextFileSnapshot>()
    private val selections = mutableMapOf<String, IntRange>()
    private val documentIds = mutableMapOf<String, EditorDocumentId>()
    private val saveMutexes = mutableMapOf<String, Mutex>()
    private var operationJob: Job? = null
    private var operationGeneration = 0
    private var activeSaveCount = 0

    fun open(relativePath: String) {
        if (relativePath.isBlank()) return
        if (buffers.containsKey(relativePath)) {
            activateTab(relativePath)
            checkExternalChanges()
            return
        }
        runTask("Open $relativePath") { readSnapshot(relativePath) }
    }

    fun activateTab(path: String) {
        if (!buffers.containsKey(path)) return
        val selection = selections[path] ?: 0..0
        _uiState.update {
            it.copy(
                activePath = path,
                selection = selection,
                externalChange = null,
                message = null,
                error = null,
            )
        }
        publishActive()
    }

    fun closeTab(path: String) {
        val buffer = buffers[path]
        if (buffer?.dirty == true) {
            _uiState.update {
                it.copy(error = "Save or discard changes before closing $path.")
            }
            return
        }
        buffers.remove(path)
        snapshots.remove(path)
        selections.remove(path)
        val tabs = _uiState.value.tabs.filterNot { it.path == path }
        val nextPath = tabs.lastOrNull()?.path
        _uiState.update {
            it.copy(
                tabs = tabs,
                activePath = nextPath,
                selection = nextPath?.let { path -> selections[path] } ?: 0..0,
                externalChange = null,
                message = null,
                error = null,
            )
        }
        publishActive()
    }

    fun onTextChanged(newText: String, selection: IntRange) {
        val path = _uiState.value.activePath ?: return
        val buffer = buffers[path] ?: return
        if (_uiState.value.readOnly) return
        val oldText = buffer.text
        var start = 0
        val commonLength = minOf(oldText.length, newText.length)
        while (start < commonLength && oldText[start] == newText[start]) start++
        var oldEnd = oldText.length
        var newEnd = newText.length
        while (oldEnd > start && newEnd > start && oldText[oldEnd - 1] == newText[newEnd - 1]) {
            oldEnd--
            newEnd--
        }
        if (oldEnd != start || newEnd != start) {
            buffer.replace(start, oldEnd, newText.substring(start, newEnd))
        }
        val safeSelection = selection.first.coerceIn(0, newText.length)..
            selection.last.coerceIn(0, newText.length)
        publishActive(safeSelection)
    }

    fun setSelection(selection: IntRange) {
        publishActive(selection)
    }

    fun save() {
        val state = _uiState.value
        val path = state.activePath ?: return
        val buffer = buffers[path] ?: return
        if (state.readOnly) {
            _uiState.update { it.copy(error = "Read-only files cannot be saved.") }
            return
        }
        if (state.externalChange != null) {
            _uiState.update { it.copy(error = "Resolve external changes before saving.") }
            return
        }
        val documentId = documentIds[path] ?: currentDocumentId(path).also { documentIds[path] = it }
        val request = EditorSaveSnapshot(
            documentId = documentId,
            text = buffer.text,
            revision = buffer.revision,
            encoding = state.encoding,
            newlineStyle = state.newlineStyle,
            expectedSignature = snapshots[path]?.signature,
        )
        enqueueSave(path, documentId, request)
    }

    private fun enqueueSave(
        path: String,
        documentId: EditorDocumentId,
        request: EditorSaveSnapshot,
    ) {
        val mutex = saveMutexes.getOrPut(documentId.key) { Mutex() }
        val label = "Save $path"
        activeSaveCount += 1
        _uiState.update { it.copy(busyLabel = label, message = null, error = null) }
        viewModelScope.launch(mainDispatcher) {
            try {
                val result = mutex.withLock {
                    withContext(ioDispatcher) {
                        when (val result = workspaceGateway.writeTextFile(request)) {
                            is WorkspaceResult.Success -> EditorResult.Saved(
                                path = path,
                                documentId = documentId,
                                revision = request.revision,
                                savedText = request.text,
                                signature = result.value,
                            )
                            is WorkspaceResult.Failure -> EditorResult.Error(result.message)
                            is WorkspaceResult.PartialFailure -> EditorResult.Error(result.message)
                        }
                    }
                }
                if (documentIds[path] == documentId && buffers.containsKey(path)) {
                    applyResult(result, label)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (documentIds[path] == documentId) {
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Editor operation failed",
                            busyLabel = null,
                        )
                    }
                }
            } finally {
                activeSaveCount -= 1
                if (activeSaveCount == 0) {
                    _uiState.update {
                        if (it.busyLabel == label) it.copy(busyLabel = null) else it
                    }
                }
            }
        }
    }

    fun checkExternalChanges() {
        val path = _uiState.value.activePath ?: return
        runTask("Check changes") { readSnapshot(path, forCheck = true) }
    }

    fun discardChanges() = reloadFromDisk()

    fun reloadFromDisk() {
        val path = _uiState.value.activePath ?: return
        runTask("Reload $path") { readSnapshot(path) }
    }

    fun keepLocalChanges() {
        val conflict = _uiState.value.externalChange ?: return
        val snapshot = snapshots[conflict.path] ?: return
        snapshots[conflict.path] = snapshot.copy(signature = conflict.diskSignature)
        _uiState.update { it.copy(externalChange = null) }
        save()
    }

    fun undo() {
        val path = _uiState.value.activePath ?: return
        val buffer = buffers[path] ?: return
        if (buffer.undo()) publishActive(0..0)
    }

    fun redo() {
        val path = _uiState.value.activePath ?: return
        val buffer = buffers[path] ?: return
        if (buffer.redo()) publishActive(0..0)
    }

    fun formatActive() {
        val state = _uiState.value
        val path = state.activePath ?: return
        val buffer = buffers[path] ?: return
        if (state.readOnly) return
        val language = state.tabs.firstOrNull { it.path == path }?.language ?: EditorLanguage.PLAIN_TEXT
        val formatted = EditorFormatter.format(buffer.text, language)
        if (formatted != buffer.text) {
            buffer.replace(0, buffer.text.length, formatted)
            publishActive(0..0)
            _uiState.update { it.copy(message = "Formatted $path") }
        }
    }

    fun setSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(searchVisible = visible) }
        publishActive()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        publishActive()
    }

    fun setReplaceText(text: String) {
        _uiState.update { it.copy(replaceText = text) }
    }

    fun setIgnoreCase(value: Boolean) {
        _uiState.update { it.copy(ignoreCase = value) }
        publishActive()
    }

    fun setWholeWord(value: Boolean) {
        _uiState.update { it.copy(wholeWord = value) }
        publishActive()
    }

    fun nextMatch() {
        val state = _uiState.value
        val buffer = buffers[state.activePath ?: return] ?: return
        val match = TextSearch.findNext(
            text = buffer.text,
            query = state.searchQuery,
            fromOffset = state.selection.first + 1,
            ignoreCase = state.ignoreCase,
            wholeWord = state.wholeWord,
        ) ?: return
        publishActive(match.start..match.end)
    }

    fun replaceNext() {
        val state = _uiState.value
        val buffer = buffers[state.activePath ?: return] ?: return
        val match = state.matches.getOrNull(state.activeMatch) ?: state.matches.firstOrNull() ?: return
        if (buffer.replaceRange(match.start..<match.end, state.replaceText)) {
            publishActive(match.start..match.start + state.replaceText.length)
        }
    }

    fun replaceAll() {
        val state = _uiState.value
        val buffer = buffers[state.activePath ?: return] ?: return
        val (text, count) = TextSearch.replaceAll(
            text = buffer.text,
            query = state.searchQuery,
            replacement = state.replaceText,
            ignoreCase = state.ignoreCase,
            wholeWord = state.wholeWord,
        )
        if (count == 0) return
        buffer.replace(0, buffer.text.length, text)
        publishActive(0..0)
        _uiState.update { it.copy(message = "Replaced $count matches") }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun runTask(label: String, block: suspend () -> EditorResult) {
        operationJob?.cancel()
        val token = ++operationGeneration
        _uiState.update { it.copy(busyLabel = label, message = null, error = null) }
        val job = viewModelScope.launch(mainDispatcher) {
            try {
                val result = withContext(ioDispatcher) { block() }
                if (token == operationGeneration) applyResult(result, label)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == operationGeneration) {
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Editor operation failed",
                            busyLabel = null,
                        )
                    }
                }
            } finally {
                if (token == operationGeneration) _uiState.update { it.copy(busyLabel = null) }
            }
        }
        operationJob = job
    }

    private suspend fun readSnapshot(path: String, forCheck: Boolean = false): EditorResult =
        when (val result = workspaceGateway.readTextFile(selectedDocumentId(path))) {
            is WorkspaceResult.Success -> if (forCheck) {
                EditorResult.Checked(path, result.value)
            } else {
                EditorResult.Loaded(path, result.value)
            }
            is WorkspaceResult.Failure -> EditorResult.Error(result.message)
            is WorkspaceResult.PartialFailure -> EditorResult.Error(result.message)
        }

    private fun selectedDocumentId(path: String): EditorDocumentId {
        val selected = container.selectedProject.value
        val context = runCatching { container.projectContext(selected) }.getOrNull()
        return EditorDocumentId(
            workspaceId = context?.projectId ?: EditorDocumentId.SHARED_WORKSPACE_ID,
            rootPath = context?.rootPath ?: container.projectsRoot.absolutePath,
            relativePath = path,
        )
    }

    private fun applyResult(result: EditorResult, label: String) {
        when (result) {
            is EditorResult.Error -> _uiState.update { it.copy(error = result.message) }
            is EditorResult.Loaded -> applyLoaded(result.path, result.documentId, result.snapshot)
            is EditorResult.Saved -> applySaved(
                path = result.path,
                documentId = result.documentId,
                revision = result.revision,
                savedText = result.savedText,
                signature = result.signature,
            )
            is EditorResult.Reloaded -> applyReloaded(result.path, result.documentId, result.snapshot)
            is EditorResult.Checked -> applyChecked(result.path, result.documentId, result.snapshot)
        }
        if (result !is EditorResult.Error) {
            _uiState.update { it.copy(message = "$label complete") }
        }
    }

    private fun applyLoaded(
        path: String,
        documentId: EditorDocumentId,
        snapshot: TextFileSnapshot,
    ) {
        if (documentIds[path] != documentId) return
        val tab = EditorTabState(
            path = path,
            name = path.substringAfterLast('/').ifBlank { path },
            language = EditorLanguage.fromPath(path),
            readOnly = snapshot.truncated,
        )
        buffers[path] = EditorBuffer(snapshot.text)
        snapshots[path] = snapshot
        selections[path] = 0..0
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.filterNot { it.path == path } + tab,
                activePath = path,
                selection = 0..0,
                externalChange = null,
            )
        }
        publishActive()
    }

    private fun applySaved(path: String, signature: FileSignature) {
        val buffer = buffers[path] ?: return
        buffer.markSaved()
        val snapshot = snapshots[path] ?: return
        snapshots[path] = snapshot.copy(
            signature = signature,
            sizeBytes = signature.sizeBytes,
            text = buffer.text,
        )
        publishActive()
    }

    private fun applyReloaded(path: String, snapshot: TextFileSnapshot) {
        buffers[path] = EditorBuffer(snapshot.text)
        snapshots[path] = snapshot
        selections[path] = 0..0
        _uiState.update { it.copy(activePath = path, selection = 0..0, externalChange = null) }
        publishActive()
    }

    private fun applyChecked(path: String, snapshot: TextFileSnapshot) {
        val current = snapshots[path]
        if (current?.signature == snapshot.signature) return
        val buffer = buffers[path]
        if (buffer?.dirty == true) {
            _uiState.update {
                it.copy(
                    externalChange = ExternalChangeState(path, snapshot.signature, true),
                )
            }
            return
        }
        buffers[path] = EditorBuffer(snapshot.text)
        snapshots[path] = snapshot
        selections[path] = 0..0
        _uiState.update { it.copy(activePath = path, selection = 0..0, externalChange = null) }
        publishActive()
    }

    private fun publishActive(selection: IntRange? = null) {
        val state = _uiState.value
        val path = state.activePath ?: return
        val buffer = buffers[path] ?: return
        val range = selection ?: state.selection
        val safeRange = range.first.coerceIn(0, buffer.text.length)..
            range.last.coerceIn(0, buffer.text.length)
        selections[path] = safeRange
        val matches = if (state.searchQuery.isEmpty()) {
            emptyList()
        } else {
            TextSearch.findAll(
                text = buffer.text,
                query = state.searchQuery,
                ignoreCase = state.ignoreCase,
                wholeWord = state.wholeWord,
            )
        }
        val activeMatch = if (matches.isEmpty()) {
            -1
        } else {
            val containing = matches.indexOfFirst { safeRange.first in it.start..it.end }
            val following = matches.indexOfFirst { it.start >= safeRange.first }
            if (containing >= 0) containing else following
        }
        val (cursorLine, cursorColumn) = buffer.lineAndColumn(safeRange.first)
        _uiState.update {
            it.copy(
                text = buffer.text,
                selection = safeRange,
                dirty = buffer.dirty,
                readOnly = it.tabs.firstOrNull { tab -> tab.path == path }?.readOnly ?: false,
                truncated = snapshots[path]?.truncated ?: false,
                encoding = snapshots[path]?.encoding ?: TextEncoding.UTF_8,
                newlineStyle = snapshots[path]?.newlineStyle ?: NewlineStyle.LF,
                lineCount = buffer.lineStarts.size,
                cursorLine = cursorLine,
                cursorColumn = cursorColumn,
                canUndo = buffer.canUndo,
                canRedo = buffer.canRedo,
                matches = matches,
                activeMatch = activeMatch,
            )
        }
    }

    override fun onCleared() {
        operationJob?.cancel()
        super.onCleared()
    }
}
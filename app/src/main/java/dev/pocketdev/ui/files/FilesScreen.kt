package dev.pocketdev.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.R
import dev.pocketdev.core.workspace.FileEntry
import dev.pocketdev.core.workspace.FileKind

@Composable
fun FilesRoute(viewModel: FilesViewModel, onOpenInEditor: (FileEntry) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingExport = remember { mutableStateOf<FileEntry?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importDocument(uri)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) pendingExport.value?.let { viewModel.exportDocument(it, uri) }
    }

    FilesScreen(
        state = state,
        onSelectProject = viewModel::selectProject,
        onOpen = viewModel::open,
        onOpenInEditor = onOpenInEditor,
        onNavigateUp = viewModel::navigateUp,
        onToggleHidden = viewModel::toggleHidden,
        onQueryChange = viewModel::setQuery,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onCopy = { entry, name -> viewModel.copy(entry, name) },
        onMove = { entry, name -> viewModel.move(entry, name) },
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        onExport = { entry ->
            pendingExport.value = entry
            exportLauncher.launch(entry.name)
        },
        onClearFeedback = viewModel::clearFeedback,
        onCancelOperation = viewModel::cancelOperation,
    )
}

@Composable
fun FilesScreen(
    state: FilesUiState,
    onSelectProject: (String?) -> Unit,
    onOpen: (FileEntry) -> Unit,
    onOpenInEditor: (FileEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onToggleHidden: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onRename: (FileEntry, String) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onCopy: (FileEntry, String) -> Unit,
    onMove: (FileEntry, String) -> Unit,
    onImport: () -> Unit,
    onExport: (FileEntry) -> Unit,
    onClearFeedback: () -> Unit,
    onCancelOperation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    var input by remember { mutableStateOf("") }
    var projectMenuExpanded by remember { mutableStateOf(false) }

    fun showDialog(next: FileDialog, initial: String = "") {
        input = initial
        dialog = next
    }

    val selectedName = state.projects.firstOrNull { it.id == state.selectedProjectId }?.name
        ?: stringResource(R.string.files_no_project)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.files_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.files_root_label, state.rootPath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column {
            Button(onClick = { projectMenuExpanded = true }) {
                Text(stringResource(R.string.files_project_label, selectedName))
            }
            DropdownMenu(
                expanded = projectMenuExpanded,
                onDismissRequest = { projectMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_shared_root)) },
                    onClick = {
                        onSelectProject(null)
                        projectMenuExpanded = false
                    },
                )
                state.projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            onSelectProject(project.id)
                            projectMenuExpanded = false
                        },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.showHidden, onCheckedChange = { _ -> onToggleHidden() })
            Text(stringResource(R.string.files_hidden))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNavigateUp) {
                Text(stringResource(R.string.files_up))
            }
            OutlinedButton(onClick = { showDialog(FileDialog.Create(directory = true)) }) {
                Text(stringResource(R.string.files_new_folder))
            }
            OutlinedButton(onClick = { showDialog(FileDialog.Create(directory = false)) }) {
                Text(stringResource(R.string.files_new_file))
            }
            OutlinedButton(onClick = onImport) {
                Text(stringResource(R.string.files_import))
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.files_search)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text(
            text = if (state.currentDirectory.isBlank()) {
                stringResource(R.string.files_directory_root)
            } else {
                stringResource(R.string.files_directory_label, state.currentDirectory)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        state.operation?.let { operation ->
            val statusText = when (operation.status) {
                FilesOperationStatus.RUNNING -> stringResource(R.string.files_operation_running, operation.label)
                FilesOperationStatus.SUCCESS -> stringResource(R.string.files_operation_success, operation.label)
                FilesOperationStatus.FAILURE -> stringResource(R.string.files_operation_failure, operation.label)
                FilesOperationStatus.CANCELLED -> stringResource(R.string.files_operation_cancelled, operation.label)
                FilesOperationStatus.PARTIAL_FAILURE -> stringResource(
                    R.string.files_operation_partial,
                    operation.label,
                    operation.completed,
                    operation.total,
                )
            }
            Text(
                text = statusText,
                color = if (operation.status == FilesOperationStatus.FAILURE ||
                    operation.status == FilesOperationStatus.PARTIAL_FAILURE
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (operation.status == FilesOperationStatus.RUNNING) {
                if (operation.total > 0) {
                    LinearProgressIndicator(
                        progress = { operation.completed.toFloat() / operation.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.files_operation_progress, operation.completed, operation.total),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.files_operation_indeterminate),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onCancelOperation) {
                    Text(stringResource(R.string.files_cancel))
                }
            }
        }

        state.message?.let {
            Text(text = stringResource(R.string.files_message, it), color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onClearFeedback) {
                Text(stringResource(R.string.files_dismiss))
            }
        }
        state.error?.let {
            Text(text = stringResource(R.string.files_error, it), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onClearFeedback) {
                Text(stringResource(R.string.files_dismiss))
            }
        }

        state.previewPath?.let { path ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.files_preview_title, path),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = state.preview ?: stringResource(R.string.files_preview_binary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (state.entries.isEmpty()) {
            Text(
                text = stringResource(R.string.files_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.entries, key = { it.relativePath }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (entry.isDirectory) "[dir] ${entry.name}" else "[file] ${entry.name}",
                                modifier = Modifier
                                    .testTag("file_entry_${entry.relativePath}")
                                    .clickable { onOpen(entry) },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.files_entry_meta, entry.kind.name, entry.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (entry.kind == FileKind.TEXT) {
                                    TextButton(
                                        onClick = { onOpenInEditor(entry) },
                                        modifier = Modifier.testTag("open_in_editor"),
                                    ) {
                                        Text(stringResource(R.string.files_open_in_editor))
                                    }
                                }
                                TextButton(onClick = { showDialog(FileDialog.Rename(entry), entry.name) }) {
                                    Text(stringResource(R.string.files_rename))
                                }
                                TextButton(onClick = { showDialog(FileDialog.Copy(entry), "${entry.name}-copy") }) {
                                    Text(stringResource(R.string.files_copy))
                                }
                                TextButton(onClick = { showDialog(FileDialog.Move(entry), entry.name) }) {
                                    Text(stringResource(R.string.files_move))
                                }
                                TextButton(onClick = { onExport(entry) }) {
                                    Text(stringResource(R.string.files_export))
                                }
                                TextButton(onClick = { showDialog(FileDialog.Delete(entry)) }) {
                                    Text(stringResource(R.string.files_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    dialog?.let { active ->
        val needsName = active !is FileDialog.Delete
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = {
                Text(
                    text = when (active) {
                        is FileDialog.Create -> if (active.directory) {
                            stringResource(R.string.files_dialog_create_folder)
                        } else {
                            stringResource(R.string.files_dialog_create_file)
                        }
                        is FileDialog.Rename -> stringResource(R.string.files_dialog_rename)
                        is FileDialog.Copy -> stringResource(R.string.files_dialog_copy)
                        is FileDialog.Move -> stringResource(R.string.files_dialog_move)
                        is FileDialog.Delete -> stringResource(R.string.files_dialog_delete)
                    },
                )
            },
            text = {
                if (needsName) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(stringResource(R.string.files_name_label)) },
                        singleLine = true,
                    )
                } else {
                    val deleteEntry = active as FileDialog.Delete
                    Text(stringResource(R.string.files_delete_confirm, deleteEntry.entry.name))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (active) {
                            is FileDialog.Create -> onCreate(input.trim(), active.directory)
                            is FileDialog.Rename -> onRename(active.entry, input.trim())
                            is FileDialog.Copy -> onCopy(active.entry, input.trim())
                            is FileDialog.Move -> onMove(active.entry, input.trim())
                            is FileDialog.Delete -> onDelete(active.entry)
                        }
                        dialog = null
                    },
                    enabled = !needsName || input.isNotBlank(),
                ) {
                    Text(stringResource(R.string.files_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) {
                    Text(stringResource(R.string.files_cancel))
                }
            },
        )
    }
}

private sealed interface FileDialog {
    data class Create(val directory: Boolean) : FileDialog
    data class Rename(val entry: FileEntry) : FileDialog
    data class Copy(val entry: FileEntry) : FileDialog
    data class Move(val entry: FileEntry) : FileDialog
    data class Delete(val entry: FileEntry) : FileDialog
}

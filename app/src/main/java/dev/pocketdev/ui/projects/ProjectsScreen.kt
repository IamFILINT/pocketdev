package dev.pocketdev.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.R
import dev.pocketdev.core.validation.ProjectNameValidator
import dev.pocketdev.core.validation.ValidationResult
import dev.pocketdev.core.workspace.Project

@Composable
fun ProjectsRoute(viewModel: ProjectsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectsScreen(
        state = state,
        onCreate = viewModel::createProject,
        onDelete = viewModel::deleteProject,
    )
}

@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onCreate: (String) -> CreateProjectResult,
    onDelete: (Project) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectPendingDeletion by remember { mutableStateOf<Project?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.projects_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.projects_create),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.projects_root_label, state.projectsRoot),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (state.projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.projects_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.projects_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.projects, key = { it.id }) { project ->
                    ProjectRow(
                        project = project,
                        onDelete = { projectPendingDeletion = project },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreate,
            onCreated = { showCreateDialog = false },
        )
    }

    projectPendingDeletion?.let { project ->
        AlertDialog(
            onDismissRequest = { projectPendingDeletion = null },
            title = { Text(stringResource(R.string.projects_delete)) },
            text = { Text(project.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(project)
                        projectPendingDeletion = null
                    },
                ) { Text(stringResource(R.string.projects_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDeletion = null }) {
                    Text(stringResource(R.string.projects_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = project.rootPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.projects_delete))
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> CreateProjectResult,
    onCreated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var errorReason by remember { mutableStateOf<ValidationResult.Reason?>(null) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorReason = null
                        failureMessage = null
                    },
                    label = { Text(stringResource(R.string.projects_name_label)) },
                    singleLine = true,
                    isError = errorReason != null || failureMessage != null,
                )
                val message = failureMessage
                    ?: errorReason?.let { reasonText(it) }
                if (message != null) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = onCreate(name)) {
                        CreateProjectResult.Created -> onCreated()
                        is CreateProjectResult.Invalid -> errorReason = result.reason
                        is CreateProjectResult.Failed -> failureMessage = result.message
                    }
                },
            ) { Text(stringResource(R.string.projects_create_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.projects_cancel_action))
            }
        },
    )
}

@Composable
private fun reasonText(reason: ValidationResult.Reason): String = when (reason) {
    ValidationResult.Reason.EMPTY -> stringResource(R.string.projects_name_error_empty)
    ValidationResult.Reason.TOO_LONG ->
        stringResource(R.string.projects_name_error_too_long, ProjectNameValidator.MAX_LENGTH)
    ValidationResult.Reason.INVALID_CHARACTERS ->
        stringResource(R.string.projects_name_error_characters)
    ValidationResult.Reason.RESERVED_NAME -> stringResource(R.string.projects_name_error_reserved)
    ValidationResult.Reason.LEADING_OR_TRAILING_WHITESPACE ->
        stringResource(R.string.projects_name_error_whitespace)
    ValidationResult.Reason.HIDDEN_NAME -> stringResource(R.string.projects_name_error_hidden)
}
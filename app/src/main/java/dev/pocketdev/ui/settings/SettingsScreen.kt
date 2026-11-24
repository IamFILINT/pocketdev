package dev.pocketdev.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.R
import dev.pocketdev.core.settings.AgentSettings
import dev.pocketdev.settings.ThemeMode

@Composable
fun SettingsRoute(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val apiKeyDraft by viewModel.apiKeyDraft.collectAsStateWithLifecycle()
    val apiKeyConfigured by viewModel.apiKeyConfigured.collectAsStateWithLifecycle()
    val apiKeyError by viewModel.apiKeyError.collectAsStateWithLifecycle()
    val agentSettings by viewModel.agentSettings.collectAsStateWithLifecycle()
    val agentSettingsError by viewModel.agentSettingsError.collectAsStateWithLifecycle()

    SettingsScreen(
        themeMode = themeMode,
        apiKeyDraft = apiKeyDraft,
        apiKeyConfigured = apiKeyConfigured,
        apiKeyError = apiKeyError,
        agentSettings = agentSettings,
        agentSettingsError = agentSettingsError,
        workspaceRoot = viewModel.workspaceRoot,
        onThemeChange = viewModel::setThemeMode,
        onApiKeyChange = viewModel::setApiKeyDraft,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onAgentSettingsChange = viewModel::setAgentSettings,
        onClearProjects = viewModel::clearProjects,
    )
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    apiKeyDraft: String,
    apiKeyConfigured: Boolean,
    apiKeyError: String?,
    agentSettings: AgentSettings,
    agentSettingsError: String?,
    workspaceRoot: String,
    onThemeChange: (ThemeMode) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onAgentSettingsChange: (String, String) -> Unit,
    onClearProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_appearance_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                },
                            )
                        },
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_ai_title)) {
            OutlinedTextField(
                value = agentSettings.provider,
                onValueChange = { onAgentSettingsChange(it, agentSettings.model) },
                label = { Text(stringResource(R.string.settings_provider_label)) },
                placeholder = { Text(stringResource(R.string.settings_provider_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = agentSettings.model,
                onValueChange = { onAgentSettingsChange(agentSettings.provider, it) },
                label = { Text(stringResource(R.string.settings_model_label)) },
                placeholder = { Text(stringResource(R.string.settings_model_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_agent_defaults_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            agentSettingsError?.let {
                Text(
                    text = stringResource(R.string.settings_agent_settings_error, it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.settings_ai_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_ai_key_placeholder)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_ai_key_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    if (apiKeyConfigured) R.string.settings_ai_key_saved else R.string.settings_ai_key_missing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = apiKeyDraft.isNotBlank(),
                    onClick = { onSaveApiKey(apiKeyDraft) },
                ) {
                    Text(stringResource(R.string.settings_ai_key_save))
                }
                TextButton(
                    enabled = apiKeyConfigured,
                    onClick = onClearApiKey,
                ) {
                    Text(stringResource(R.string.settings_ai_key_clear))
                }
            }
            apiKeyError?.let {
                Text(
                    text = stringResource(R.string.settings_ai_key_error, it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_delete_all_title)) {
            Text(
                text = stringResource(R.string.settings_workspace_body, workspaceRoot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { showClearConfirm = true }) {
                Text(stringResource(R.string.settings_clear_projects))
            }
        }

        SectionCard(title = stringResource(R.string.settings_about_title)) {
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_clear_projects)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearProjects()
                        showClearConfirm = false
                    },
                ) { Text(stringResource(R.string.projects_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.projects_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}
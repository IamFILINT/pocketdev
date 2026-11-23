package dev.pocketdev.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.R
import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.core.agent.OpenCodeSession
import dev.pocketdev.core.agent.PermissionDecision
import dev.pocketdev.core.agent.PermissionRequest
import dev.pocketdev.core.ai.TaskState
import dev.pocketdev.runtime.OpenCodeServerState

@Composable
fun AgentRoute(viewModel: AgentWorkspaceViewModel) {
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val taskState by viewModel.taskState.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val streamError by viewModel.streamError.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val permissionRequest by viewModel.permissionRequest.collectAsStateWithLifecycle()

    AgentScreen(
        agentState = agentState,
        serverState = serverState,
        taskState = taskState,
        streaming = streaming,
        streamError = streamError,
        sessions = sessions,
        busy = busy,
        actionError = actionError,
        activeSessionId = activeSessionId,
        sending = sending,
        permissionRequest = permissionRequest,
        onInstall = viewModel::installAgent,
        onStartServer = viewModel::startServer,
        onStopServer = viewModel::stopServer,
        onSelectSession = viewModel::selectSession,
        onCreateSession = viewModel::createSession,
        onSendPrompt = viewModel::sendPrompt,
        onPermissionDecision = viewModel::respondToPermission,
    )
}

@Composable
fun AgentScreen(
    agentState: AgentState,
    serverState: OpenCodeServerState,
    taskState: TaskState,
    streaming: Boolean,
    streamError: String?,
    sessions: List<OpenCodeSession>,
    busy: Boolean,
    actionError: String?,
    activeSessionId: String?,
    sending: Boolean,
    permissionRequest: PermissionRequest?,
    onInstall: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onPermissionDecision: (PermissionDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val serverReady = serverState is OpenCodeServerState.Listening

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.agent_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.agent_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        actionError?.let {
            Text(
                text = stringResource(R.string.agent_action_error, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        AgentCard(
            title = stringResource(R.string.dashboard_agent_title),
            status = agentStatusText(agentState),
        ) {
            if (agentState is AgentState.NotInstalled || agentState is AgentState.Failed) {
                Button(onClick = onInstall, enabled = !busy) {
                    Text(
                        text = if (agentState is AgentState.Failed) {
                            stringResource(R.string.dashboard_agent_retry)
                        } else {
                            stringResource(R.string.dashboard_agent_install)
                        },
                    )
                }
            }
        }

        AgentCard(
            title = stringResource(R.string.agent_server_title),
            status = serverStatusText(serverState),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serverState is OpenCodeServerState.Stopped ||
                    serverState is OpenCodeServerState.Failed ||
                    serverState is OpenCodeServerState.Exited
                ) {
                    Button(
                        onClick = onStartServer,
                        enabled = !busy && agentState is AgentState.Ready,
                    ) {
                        Text(stringResource(R.string.agent_server_start))
                    }
                }
                if (serverState is OpenCodeServerState.Starting ||
                    serverState is OpenCodeServerState.Listening
                ) {
                    OutlinedButton(onClick = onStopServer, enabled = !busy) {
                        Text(stringResource(R.string.agent_server_stop))
                    }
                }
            }
        }

        AgentCard(
            title = stringResource(R.string.agent_task_title),
            status = stringResource(R.string.agent_task_state, taskState.name),
        ) {
            if (streaming) {
                Text(
                    text = stringResource(R.string.agent_task_streaming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            streamError?.let {
                Text(
                    text = stringResource(R.string.agent_task_error, it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        permissionRequest?.let { request ->
            AgentCard(
                title = stringResource(R.string.agent_permission_title),
                status = stringResource(R.string.agent_permission_action, request.action),
            ) {
                Text(
                    text = stringResource(
                        R.string.agent_permission_resources,
                        request.resources.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                request.message?.let {
                    Text(
                        text = stringResource(R.string.agent_permission_message, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPermissionDecision(PermissionDecision.ONCE) }) {
                        Text(stringResource(R.string.agent_permission_allow_once))
                    }
                    OutlinedButton(onClick = { onPermissionDecision(PermissionDecision.ALWAYS) }) {
                        Text(stringResource(R.string.agent_permission_allow_always))
                    }
                    OutlinedButton(onClick = { onPermissionDecision(PermissionDecision.REJECT) }) {
                        Text(stringResource(R.string.agent_permission_reject))
                    }
                }
            }
        }

        AgentCard(
            title = stringResource(R.string.agent_sessions_title),
            status = activeSessionId?.let { stringResource(R.string.agent_session_selected, it) },
        ) {
            OutlinedButton(onClick = onCreateSession, enabled = serverReady && !sending) {
                Text(stringResource(R.string.agent_session_new))
            }
            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.agent_sessions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                sessions.forEach { session ->
                    OutlinedButton(
                        onClick = { onSelectSession(session.id) },
                        enabled = !sending,
                    ) {
                        Text(session.title ?: session.id)
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.agent_prompt_label)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = serverReady && !sending,
                minLines = 3,
            )
            Button(
                onClick = {
                    onSendPrompt(draft)
                    draft = ""
                },
                enabled = serverReady && !sending && draft.isNotBlank(),
            ) {
                Text(stringResource(R.string.agent_prompt_send))
            }
        }
    }
}

@Composable
private fun AgentCard(
    title: String,
    status: String?,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            status?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            content()
        }
    }
}

@Composable
private fun agentStatusText(state: AgentState): String = when (state) {
    AgentState.NotInstalled -> stringResource(R.string.dashboard_agent_not_installed)
    is AgentState.Downloading ->
        stringResource(R.string.dashboard_agent_downloading, (state.fraction * 100).toInt())
    AgentState.Verifying -> stringResource(R.string.dashboard_agent_verifying)
    is AgentState.Ready -> stringResource(R.string.dashboard_agent_ready, state.version)
    is AgentState.Failed -> stringResource(R.string.dashboard_agent_failed, state.message)
}

@Composable
private fun serverStatusText(state: OpenCodeServerState): String = when (state) {
    OpenCodeServerState.Stopped -> stringResource(R.string.agent_server_stopped)
    OpenCodeServerState.Starting -> stringResource(R.string.agent_server_starting)
    is OpenCodeServerState.Listening ->
        stringResource(R.string.agent_server_listening, state.connection.baseUrl)
    is OpenCodeServerState.Failed -> stringResource(R.string.agent_server_failed, state.message)
    is OpenCodeServerState.Exited -> stringResource(R.string.agent_server_exited, state.exitCode)
}

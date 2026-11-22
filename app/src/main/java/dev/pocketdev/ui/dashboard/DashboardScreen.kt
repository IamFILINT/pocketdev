package dev.pocketdev.ui.dashboard

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import dev.pocketdev.BuildConfig
import dev.pocketdev.R
import dev.pocketdev.core.agent.AgentState
import dev.pocketdev.core.runtime.RuntimeState

@Composable
fun DashboardRoute(
    projectCount: Int,
    runtimeState: RuntimeState,
    provisioning: Boolean,
    onInstall: () -> Unit,
    agentState: AgentState,
    agentProvisioning: Boolean,
    onInstallAgent: () -> Unit,
) {
    DashboardScreen(
        projectCount = projectCount,
        runtimeState = runtimeState,
        provisioning = provisioning,
        onInstall = onInstall,
        agentState = agentState,
        agentProvisioning = agentProvisioning,
        onInstallAgent = onInstallAgent,
    )
}

@Composable
fun DashboardScreen(
    projectCount: Int,
    runtimeState: RuntimeState,
    provisioning: Boolean,
    onInstall: () -> Unit,
    agentState: AgentState,
    agentProvisioning: Boolean,
    onInstallAgent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.dashboard_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusCard(
            title = stringResource(R.string.dashboard_runtime_title),
            status = runtimeStatusText(runtimeState),
            hint = stringResource(R.string.dashboard_runtime_hint),
        )

        RuntimeAction(
            runtimeState = runtimeState,
            provisioning = provisioning,
            onInstall = onInstall,
        )

        StatusCard(
            title = stringResource(R.string.dashboard_agent_title),
            status = agentStatusText(agentState),
            hint = stringResource(R.string.dashboard_agent_hint),
        )

        AgentAction(
            agentState = agentState,
            agentProvisioning = agentProvisioning,
            onInstall = onInstallAgent,
        )

        StatusCard(
            title = stringResource(R.string.dashboard_projects_title),
            status = stringResource(R.string.dashboard_project_count, projectCount),
            hint = null,
        )
    }
}

@Composable
private fun runtimeStatusText(state: RuntimeState): String = when (state) {
    RuntimeState.NotInstalled -> stringResource(R.string.dashboard_runtime_not_installed)
    RuntimeState.Checking -> stringResource(R.string.dashboard_runtime_checking)
    is RuntimeState.Downloading ->
        stringResource(R.string.dashboard_runtime_downloading, (state.fraction * 100).toInt())
    RuntimeState.Verifying -> stringResource(R.string.dashboard_runtime_verifying)
    is RuntimeState.Extracting -> stringResource(R.string.dashboard_runtime_extracting)
    is RuntimeState.Ready -> stringResource(R.string.dashboard_runtime_ready, state.runtimeVersion)
    RuntimeState.Updating -> stringResource(R.string.dashboard_runtime_updating)
    is RuntimeState.Failed ->
        stringResource(R.string.dashboard_runtime_failed, state.stage.name, state.message)
}

@Composable
private fun AgentAction(
    agentState: AgentState,
    agentProvisioning: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (agentState as? AgentState.Downloading)?.fraction
    val busy = agentProvisioning || agentState is AgentState.Downloading ||
        agentState is AgentState.Verifying

    if (busy) {
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = modifier.fillMaxWidth())
        }
    }

    if (agentState is AgentState.NotInstalled || agentState is AgentState.Failed) {
        Button(onClick = onInstall, enabled = !agentProvisioning, modifier = modifier) {
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
private fun RuntimeAction(
    runtimeState: RuntimeState,
    provisioning: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = when (runtimeState) {
        is RuntimeState.Downloading -> runtimeState.fraction
        is RuntimeState.Extracting -> if (runtimeState.totalEntries > 0) {
            runtimeState.completedEntries.toFloat() / runtimeState.totalEntries
        } else {
            null
        }
        else -> null
    }
    val busy = provisioning || runtimeState is RuntimeState.Checking ||
        runtimeState is RuntimeState.Downloading || runtimeState is RuntimeState.Verifying ||
        runtimeState is RuntimeState.Extracting || runtimeState is RuntimeState.Updating

    if (busy) {
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = modifier.fillMaxWidth())
        }
    }

    if (runtimeState is RuntimeState.NotInstalled || runtimeState is RuntimeState.Failed) {
        Button(onClick = onInstall, enabled = !provisioning, modifier = modifier) {
            Text(
                text = if (runtimeState is RuntimeState.Failed) {
                    stringResource(R.string.dashboard_runtime_retry)
                } else {
                    stringResource(R.string.dashboard_runtime_install)
                },
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    status: String,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
package dev.pocketdev.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.core.editor.EditorLanguage
import dev.pocketdev.core.editor.SyntaxHighlighter
import dev.pocketdev.core.editor.SyntaxTokenType

private const val MAX_HIGHLIGHT_CHARS = 250_000

@Composable
fun EditorRoute(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EditorScreen(
        state = state,
        onOpenTab = viewModel::activateTab,
        onCloseTab = viewModel::closeTab,
        onTextChanged = viewModel::onTextChanged,
        onSave = viewModel::save,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onFormat = viewModel::formatActive,
        onToggleSearch = { viewModel.setSearchVisible(!state.searchVisible) },
        onSearchQueryChange = viewModel::setSearchQuery,
        onReplaceTextChange = viewModel::setReplaceText,
        onIgnoreCaseChange = viewModel::setIgnoreCase,
        onWholeWordChange = viewModel::setWholeWord,
        onNextMatch = viewModel::nextMatch,
        onReplaceNext = viewModel::replaceNext,
        onReplaceAll = viewModel::replaceAll,
        onCheckChanges = viewModel::checkExternalChanges,
        onReloadDisk = viewModel::reloadFromDisk,
        onKeepLocalChanges = viewModel::keepLocalChanges,
        onDiscardLocalChanges = viewModel::discardChanges,
        onClearFeedback = viewModel::clearFeedback,
    )
}

@Composable
fun EditorScreen(
    state: EditorUiState,
    onOpenTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onTextChanged: (String, IntRange) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onReplaceTextChange: (String) -> Unit,
    onIgnoreCaseChange: (Boolean) -> Unit,
    onWholeWordChange: (Boolean) -> Unit,
    onNextMatch: () -> Unit,
    onReplaceNext: () -> Unit,
    onReplaceAll: () -> Unit,
    onCheckChanges: () -> Unit,
    onReloadDisk: () -> Unit,
    onKeepLocalChanges: () -> Unit,
    onDiscardLocalChanges: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTab = state.tabs.firstOrNull { it.path == state.activePath }
    val language = activeTab?.language ?: EditorLanguage.PLAIN_TEXT
    val syntaxText = remember(state.text, language, MaterialTheme.colorScheme) {
        if (state.text.length > MAX_HIGHLIGHT_CHARS) {
            buildAnnotatedString { append(state.text) }
        } else {
            buildAnnotatedString {
                append(state.text)
                SyntaxHighlighter.tokenize(state.text, language).forEach { token ->
                    val color = when (token.type) {
                        SyntaxTokenType.KEYWORD -> MaterialTheme.colorScheme.primary
                        SyntaxTokenType.STRING -> MaterialTheme.colorScheme.tertiary
                        SyntaxTokenType.COMMENT -> MaterialTheme.colorScheme.onSurfaceVariant
                        SyntaxTokenType.NUMBER -> MaterialTheme.colorScheme.secondary
                        SyntaxTokenType.ANNOTATION -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    addStyle(SpanStyle(color = color), token.start, token.end)
                }
            }
        }
    }
    val lineNumbers = remember(state.lineCount) {
        buildString {
            repeat(state.lineCount) { line ->
                if (line > 0) append('\n')
                append(line + 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = activeTab?.path ?: "Code editor",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (state.tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.tabs.forEach { tab ->
                    val label = when {
                        tab.path == state.activePath && tab.readOnly ->
                            "${tab.name} (read only)"
                        tab.path == state.activePath && state.dirty ->
                            "${tab.name} *"
                        else -> tab.name
                    }
                    TextButton(
                        onClick = { onOpenTab(tab.path) },
                        enabled = state.busyLabel == null,
                    ) {
                        Text(text = label, maxLines = 1)
                    }
                    if (tab.path == state.activePath) {
                        TextButton(
                            onClick = { onCloseTab(tab.path) },
                            enabled = state.busyLabel == null,
                        ) {
                            Text(text = "Close")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSave, enabled = state.activePath != null && state.busyLabel == null) {
                Text("Save")
            }
            OutlinedButton(onClick = onUndo, enabled = state.canUndo && state.busyLabel == null) {
                Text("Undo")
            }
            OutlinedButton(onClick = onRedo, enabled = state.canRedo && state.busyLabel == null) {
                Text("Redo")
            }
            OutlinedButton(onClick = onFormat, enabled = state.activePath != null && state.busyLabel == null) {
                Text("Format")
            }
            OutlinedButton(onClick = onToggleSearch, enabled = state.activePath != null) {
                Text(if (state.searchVisible) "Hide search" else "Search")
            }
            OutlinedButton(onClick = onCheckChanges, enabled = state.activePath != null && state.busyLabel == null) {
                Text("Check changes")
            }
        }

        if (state.busyLabel != null) {
            Text(
                text = state.busyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        state.externalChange?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = "The file changed on disk while you were editing.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReloadDisk) {
                            Text("Reload from disk")
                        }
                        Button(onClick = onKeepLocalChanges) {
                            Text("Keep my changes")
                        }
                        TextButton(onClick = onDiscardLocalChanges) {
                            Text("Discard my changes")
                        }
                    }
                }
            }
        }

        if (state.truncated) {
            Text(
                text = "Large file opened read-only; showing first 1 MiB.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        state.error?.let { error ->
            Text(text = "Error: $error", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onClearFeedback) {
                Text("Dismiss")
            }
        }
        state.message?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onClearFeedback) {
                Text("Dismiss")
            }
        }

        if (state.activePath == null) {
            Text(
                text = "Open a text file from Files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (state.searchVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChange,
                            label = { Text("Find") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.replaceText,
                            onValueChange = onReplaceTextChange,
                            label = { Text("Replace with") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onIgnoreCaseChange(!state.ignoreCase) }) {
                            Text(if (state.ignoreCase) "Ignore case: on" else "Ignore case: off")
                        }
                        TextButton(onClick = { onWholeWordChange(!state.wholeWord) }) {
                            Text(if (state.wholeWord) "Whole word: on" else "Whole word: off")
                        }
                        TextButton(onClick = onNextMatch) {
                            Text("Next match")
                        }
                        TextButton(onClick = onReplaceNext) {
                            Text("Replace next")
                        }
                        TextButton(onClick = onReplaceAll) {
                            Text("Replace all")
                        }
                    }
                    if (state.matches.isNotEmpty()) {
                        Text(
                            text = "${state.activeMatch + 1} / ${state.matches.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = lineNumbers,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(48.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = TextFieldValue(
                        annotatedString = syntaxText,
                        selection = TextRange(state.selection.first, state.selection.last),
                    ),
                    onValueChange = { value ->
                        onTextChanged(value.text, value.selection.start..value.selection.end)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                                return@onPreviewKeyEvent false
                            }
                            when (event.key) {
                                Key.S -> {
                                    onSave()
                                    true
                                }
                                Key.Z -> {
                                    onUndo()
                                    true
                                }
                                Key.Y -> {
                                    onRedo()
                                    true
                                }
                                Key.F -> {
                                    onToggleSearch()
                                    true
                                }
                                else -> false
                            }
                        },
                    enabled = !state.readOnly && state.busyLabel == null,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Line ${state.cursorLine}, column ${state.cursorColumn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Lines: ${state.lineCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${state.encoding.label} · ${state.newlineStyle.value.replace("\n", "\\n").replace("\r", "\\r")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
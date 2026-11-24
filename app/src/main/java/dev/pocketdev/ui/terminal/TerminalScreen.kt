package dev.pocketdev.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.R
import dev.pocketdev.core.terminal.TerminalLine

@Composable
fun TerminalRoute(viewModel: TerminalViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    TerminalScreen(
        state = state,
        onSend = viewModel::send,
        onResize = viewModel::resize,
        onNewSession = viewModel::createSession,
        onSelectSession = viewModel::selectSession,
        onCloseSession = viewModel::closeSession,
        onCopy = {
            val text = viewModel.plainText()
            if (text.isNotEmpty()) {
                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
            }
        },
        onPaste = {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            if (text.isNotEmpty()) viewModel.send(text)
        },
    )
}

@Composable
fun TerminalScreen(
    state: TerminalUiState,
    onSend: (String) -> Unit,
    onResize: (Int, Int) -> Unit,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var input by remember { mutableStateOf("") }
    var terminalSize by remember { mutableStateOf(80 to 24) }

    LaunchedEffect(state.activeId) {
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(state.snapshot.lines.size) {
        if (state.snapshot.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.snapshot.lines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.terminal_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.terminal_root_label, state.rootPath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.activeContext?.let {
                stringResource(R.string.terminal_session_context, it.projectName, it.rootPath)
            } ?: stringResource(R.string.terminal_no_active_session),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.activeContext != null &&
            state.selectedProjectContext != null &&
            state.activeContext.rootPath != state.selectedProjectContext.rootPath
        ) {
            Text(
                text = stringResource(R.string.terminal_project_mismatch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.sessions.forEach { session ->
                Button(
                    onClick = { onSelectSession(session.id) },
                    enabled = session.id != state.activeId,
                ) {
                    Text(session.name)
                }
            }
            OutlinedButton(onClick = onNewSession) {
                Text(stringResource(R.string.terminal_new_session))
            }
            OutlinedButton(onClick = { state.activeId?.let(onCloseSession) }) {
                Text(stringResource(R.string.terminal_close_session))
            }
            OutlinedButton(onClick = onCopy) {
                Text(stringResource(R.string.terminal_copy))
            }
            OutlinedButton(onClick = onPaste) {
                Text(stringResource(R.string.terminal_paste))
            }
        }

        state.error?.let {
            Text(text = stringResource(R.string.terminal_error, it), color = MaterialTheme.colorScheme.error)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { runCatching { focusRequester.requestFocus() } }
                .padding(8.dp)
                .onSizeChanged { size ->
                    val characterWidth = with(density) { 7.2.sp.toPx() }
                    val lineHeight = with(density) { 15.sp.toPx() }
                    val cols = (size.width / characterWidth).toInt().coerceIn(20, 200)
                    val rows = (size.height / lineHeight).toInt().coerceIn(4, 80)
                    if (cols != terminalSize.first || rows != terminalSize.second) {
                        terminalSize = cols to rows
                        onResize(cols, rows)
                    }
                },
        ) {
            LazyColumn(state = listState) {
                itemsIndexed(state.snapshot.lines) { _, line ->
                    Text(
                        text = renderLine(line),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        BasicTextField(
            value = input,
            onValueChange = { value ->
                val delta = value.removePrefix(input)
                input = ""
                if (delta.isNotEmpty() && delta != "\n") onSend(delta)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when {
                        event.key == Key.Enter -> {
                            onSend("\r")
                            true
                        }
                        event.key == Key.Backspace -> {
                            onSend("\u007F")
                            true
                        }
                        event.key == Key.Tab -> {
                            onSend("\t")
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            onSend("\u001B[A")
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            onSend("\u001B[B")
                            true
                        }
                        event.key == Key.DirectionLeft -> {
                            onSend("\u001B[D")
                            true
                        }
                        event.key == Key.DirectionRight -> {
                            onSend("\u001B[C")
                            true
                        }
                        event.isCtrlPressed && event.key == Key.C -> {
                            onSend("\u0003")
                            true
                        }
                        event.isCtrlPressed && event.key == Key.D -> {
                            onSend("\u0004")
                            true
                        }
                        event.isCtrlPressed && event.key == Key.L -> {
                            onSend("\u000C")
                            true
                        }
                        else -> false
                    }
                },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            decorationBox = { innerTextField ->
                if (input.isEmpty()) {
                    Text(
                        text = stringResource(R.string.terminal_input_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

private fun renderLine(line: TerminalLine): AnnotatedString = buildAnnotatedString {
    line.cells.forEach { cell ->
        val foreground = if (cell.cursor) 0xFF202124.toInt() else cell.foreground ?: 0xFFE8EAED.toInt()
        val background = if (cell.cursor) 0xFF8AB4F8.toInt() else cell.background
        withStyle(
            SpanStyle(
                color = Color(foreground),
                background = background?.let(::Color) ?: Color.Transparent,
                fontWeight = if (cell.bold) FontWeight.Bold else null,
            ),
        ) {
            append(cell.char)
        }
    }
}

package dev.pocketdev

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSetText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import dev.pocketdev.core.editor.EditorLanguage
import dev.pocketdev.ui.editor.EditorScreen
import dev.pocketdev.ui.editor.EditorTabState
import dev.pocketdev.ui.editor.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorInputUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setEditorContent(state: EditorUiState, onTextChanged: (String, IntRange) -> Unit) {
        composeRule.setContent {
            EditorScreen(
                state = state,
                onOpenTab = {},
                onCloseTab = {},
                onTextChanged = onTextChanged,
                onSave = {},
                onUndo = {},
                onRedo = {},
                onFormat = {},
                onToggleSearch = {},
                onSearchQueryChange = {},
                onReplaceTextChange = {},
                onIgnoreCaseChange = {},
                onWholeWordChange = {},
                onNextMatch = {},
                onReplaceNext = {},
                onReplaceAll = {},
                onCheckChanges = {},
                onReloadDisk = {},
                onKeepLocalChanges = {},
                onDiscardLocalChanges = {},
                onClearFeedback = {},
            )
        }
    }

    private fun editorState(text: String = "val a = 1") = EditorUiState(
        tabs = listOf(EditorTabState("main.kt", "main.kt", EditorLanguage.KOTLIN, false)),
        activePath = "main.kt",
        text = text,
    )

    @Test
    fun imeCompositionSurvivesSyntaxRecomposition() {
        var state by mutableStateOf(editorState())
        setEditorContent(state) { text, selection ->
            state = state.copy(text = text, selection = selection)
        }
        val field = composeRule.onNodeWithTag("editor_text_field")

        field.performSetText(
            text = "val a = 1",
            composition = TextRange(0, 3),
            selection = TextRange(3),
        )
        composeRule.waitForIdle()
        composeRule.runOnIdle { state = state.copy(message = "recompose") }
        composeRule.waitForIdle()
        field.performTextInput("x")

        composeRule.runOnIdle { assertEquals("x a = 1", state.text) }
    }

    @Test
    fun selectionRemainsValidAfterProgrammaticTextChange() {
        var state by mutableStateOf(editorState())
        setEditorContent(state) { text, selection ->
            state = state.copy(text = text, selection = selection)
        }
        val field = composeRule.onNodeWithTag("editor_text_field")

        field.performSetText(
            text = "val a = 1",
            selection = TextRange(1, 1),
        )
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state = state.copy(text = "🚀 val", selection = 0..0)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("🚀 val", state.text)
            assertTrue(state.selection.first in state.text.indices)
            assertTrue(state.selection.last in 0..state.text.length)
        }
    }
}

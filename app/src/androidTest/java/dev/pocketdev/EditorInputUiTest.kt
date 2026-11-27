package dev.pocketdev

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.pocketdev.core.editor.EditorLanguage
import dev.pocketdev.ui.editor.EditorScreen
import dev.pocketdev.ui.editor.EditorTabState
import dev.pocketdev.ui.editor.EditorUiState
import dev.pocketdev.ui.editor.preserveEditorTextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                onRestoreDraft = {},
                onKeepDiskDraft = {},
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
    fun compositionSurvivesSyntaxUpdateAndClearsOnExternalChange() {
        val syntax = AnnotatedString("val a = 1")
        val composing = TextFieldValue(
            annotatedString = syntax,
            selection = TextRange(3),
            composition = TextRange(0, 3),
        )

        val preserved = preserveEditorTextFieldValue(
            current = composing,
            syntaxText = AnnotatedString("val a = 1"),
            stateText = "val a = 1",
            stateSelection = TextRange(3),
        )
        assertEquals(TextRange(0, 3), preserved.composition)
        assertEquals(TextRange(3), preserved.selection)

        val external = preserveEditorTextFieldValue(
            current = composing,
            syntaxText = AnnotatedString("x a = 1"),
            stateText = "x a = 1",
            stateSelection = TextRange(1),
        )
        assertNull(external.composition)
        assertEquals("x a = 1", external.text)
        assertEquals(TextRange(1), external.selection)
    }

    @Test
    fun unicodeInputAndSelectionStayValidInCompose() {
        var state by mutableStateOf(editorState(text = ""))
        setEditorContent(state) { text, selection ->
            state = state.copy(text = text, selection = selection)
        }
        val field = composeRule.onNodeWithTag("editor_text_field")

        field.performTextInput("سلام ")
        field.performTextInput("🚀")
        field.performTextInputSelection(TextRange(0, 0))

        composeRule.runOnIdle {
            assertEquals("سلام 🚀", state.text)
            assertEquals(0..0, state.selection)
            assertTrue(state.selection.last <= state.text.length)
        }
    }
}

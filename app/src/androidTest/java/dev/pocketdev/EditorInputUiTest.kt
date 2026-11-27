package dev.pocketdev

import android.os.Build
import android.view.Choreographer
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
import java.util.Locale

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

    @Test
    fun recompositionAndFrameBaselinesAreInstrumented() {
        val fixtureText = performanceFixture(100 * 1024)
        val updateSamples = 8
        var state by mutableStateOf(editorState(text = fixtureText))
        setEditorContent(state) { text, selection ->
            state = state.copy(text = text, selection = selection)
        }
        composeRule.waitForIdle()

        val frameIntervalsMs = ArrayList<Long>()
        composeRule.runOnIdle {
            val choreographer = Choreographer.getInstance()
            var previousFrameNanos = -1L
            var frameCount = 0
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (previousFrameNanos >= 0L) {
                        frameIntervalsMs.add((frameTimeNanos - previousFrameNanos) / 1_000_000L)
                    }
                    previousFrameNanos = frameTimeNanos
                    frameCount += 1
                    if (frameCount <= 40) choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(callback)
        }

        val updateDurationsMs = ArrayList<Long>()
        repeat(updateSamples) {
            val started = System.nanoTime()
            composeRule.runOnIdle {
                val current = state.text
                val offset = current.length / 2
                state = state.copy(
                    text = current.substring(0, offset) + "x" + current.substring(offset),
                    selection = (offset + 1)..(offset + 1),
                )
            }
            setEditorContent(state)
            composeRule.waitForIdle()
            updateDurationsMs.add((System.nanoTime() - started) / 1_000_000L)
        }
        Thread.sleep(250)

        var frameSnapshot = emptyList<Long>()
        composeRule.runOnIdle { frameSnapshot = frameIntervalsMs.toList() }
        composeRule.runOnIdle {
            assertEquals(fixtureText.length + updateSamples, state.text.length)
            assertTrue(state.selection.last <= state.text.length)
        }

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        println(
            "M7_5_BENCH_ANDROID {" +
                "\"runner\":\"android-instrumentation\"," +
                "\"api\":${Build.VERSION.SDK_INT}," +
                "\"abi\":\"$abi\"," +
                "\"fixtureBytes\":${fixtureText.toByteArray().size}," +
                "\"samples\":$updateSamples," +
                "\"warmups\":1," +
                "\"recomposeP50Ms\":${fmtMs(percentileMs(updateDurationsMs, 0.50))}," +
                "\"recomposeP95Ms\":${fmtMs(percentileMs(updateDurationsMs, 0.95))}," +
                "\"frameSamples\":${frameSnapshot.size}," +
                "\"frameP50Ms\":${fmtMs(percentileMs(frameSnapshot, 0.50))}," +
                "\"frameP95Ms\":${fmtMs(percentileMs(frameSnapshot, 0.95))}" +
                "}",
        )
    }

    private fun performanceFixture(targetBytes: Int): String {
        val builder = StringBuilder(targetBytes)
        var byteCount = 0
        var index = 0
        while (byteCount < targetBytes) {
            val chunk = if (index % 8 == 0) {
                "long line 🚀 ${"x".repeat(256)}\n"
            } else {
                "line $index val café = \"🚀 text\"\n"
            }
            builder.append(chunk)
            byteCount += chunk.toByteArray().size
            index++
        }
        return builder.toString()
    }

    private fun percentileMs(values: List<Long>, percentile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = (percentile * (sorted.size - 1)).coerceIn(0.0, (sorted.size - 1).toDouble())
        val lower = rank.toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (rank - lower)
    }

    private fun fmtMs(value: Double): String = String.format(Locale.US, "%.3f", value)
}

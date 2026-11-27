package dev.pocketdev

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.pocketdev.ui.projects.ProjectsViewModel
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test

class EditorUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
    private val projectsRoot: File

    init {
        projectsRoot = app.container.projectsRoot
        ProjectsViewModel(app).clearRegistry()
        app.container.selectProject(null)
        projectsRoot.deleteRecursively()
        projectsRoot.mkdirs()
        File(projectsRoot, "editor-ui.kt").writeText("val greeting = \"hi\"\n")
        logState("initialized")
    }

    @After
    fun tearDown() {
        ProjectsViewModel(app).clearRegistry()
        projectsRoot.deleteRecursively()
    }

    @Test
    fun opensTextFileFromFilesIntoSyntaxEditor() {
        logState("before navigation")
        composeRule.onNode(hasText("Files") and isSelectable()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search files", substring = false).assertIsDisplayed()
        logState("files screen")
        logCounts("files screen")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            val found = composeRule.onAllNodesWithText("Open in editor", substring = false)
                .fetchSemanticsNodes().isNotEmpty()
            if (found) logCounts("button appeared")
            found
        }
        composeRule.onNodeWithText("Open in editor", substring = false).fetchSemanticsNode()
        logCounts("before click")
        composeRule.onNodeWithTag("open_in_editor").performScrollTo().performClick()
        composeRule.waitForIdle()
        logCounts("after click")

        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("Line 1, column 1").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            logCounts("editor wait failed")
            throw error
        }
        composeRule.onNodeWithText("editor-ui.kt").fetchSemanticsNode()
        composeRule.onNodeWithText("val greeting = \"hi\"", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Save").fetchSemanticsNode()
        composeRule.onNodeWithText("Line 1, column 1").fetchSemanticsNode()
    }

    private fun logState(stage: String) {
        val selected = app.container.selectedProject.value
        val context = runCatching { app.container.projectContext(selected) }.getOrNull()
        Log.d(
            TAG,
            "$stage root=${projectsRoot.absolutePath} dir=${projectsRoot.isDirectory} " +
                "files=${projectsRoot.list()?.contentToString()} selected=${selected?.id} " +
                "contextRoot=${context?.rootPath}",
        )
    }

    private fun logCounts(stage: String) {
        fun count(text: String): Int =
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
        fun exact(text: String): Int =
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().size
        val errorTexts = composeRule.onAllNodesWithText("Error:", substring = true)
            .fetchSemanticsNodes()
            .joinToString(" / ") { node ->
                node.config[SemanticsProperties.Text]?.joinToString("") { it.text }.orEmpty()
            }
        Log.d(
            TAG,
            "$stage counts title=${count("Project files")} search=${count("Search files")} " +
                "entry=${count("[file] editor-ui.kt")} empty=${count("No files here yet")} " +
                "error=${count("Error:")} button=${exact("Open in editor")} " +
                "editor=${exact("Line 1, column 1")} placeholder=${count("Open a text file from Files")} " +
                "preview=${count("Current directory:")} toolbar=${exact("Check changes")} " +
                "busy=${count("Open editor-ui.kt")} nav=${exact("Editor")} " +
                "errorTexts=[$errorTexts]",
        )
    }

    private companion object {
        const val TAG = "EDITOR_UI_DIAG"
    }
}

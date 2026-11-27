package dev.pocketdev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
    }

    @After
    fun tearDown() {
        ProjectsViewModel(app).clearRegistry()
        projectsRoot.deleteRecursively()
    }

    @Test
    fun opensTextFileFromFilesIntoSyntaxEditor() {
        composeRule.onNode(hasText("Files") and isSelectable()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search files", substring = false).assertIsDisplayed()

        composeRule.onNodeWithTag("files_screen").performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Open in editor", substring = false)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Open in editor", substring = false).fetchSemanticsNode()
        composeRule.onNodeWithTag("open_in_editor").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Line 1, column 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("editor-ui.kt", substring = false).onFirst().fetchSemanticsNode()
        composeRule.onNodeWithText("val greeting = \"hi\"", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Save").fetchSemanticsNode()
        composeRule.onNodeWithText("Line 1, column 1").fetchSemanticsNode()
    }
}

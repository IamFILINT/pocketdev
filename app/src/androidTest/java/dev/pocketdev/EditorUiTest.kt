package dev.pocketdev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.pocketdev.ui.projects.ProjectsViewModel
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditorUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app = ApplicationProvider.getApplicationContext<PocketDevApplication>()
    private val projectsRoot = app.container.projectsRoot

    @Before
    fun setUp() {
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
        composeRule.onNode(hasText("[file] editor-ui.kt", substring = false)).assertIsDisplayed()

        composeRule.onNodeWithText("Open in editor").performScrollTo().performClick()

        composeRule.onNodeWithText("editor-ui.kt").assertIsDisplayed()
        composeRule.onNodeWithText("val greeting = \"hi\"", substring = true).fetchSemanticsNode()
        composeRule.onNodeWithText("Save").fetchSemanticsNode()
        composeRule.onNodeWithText("Line 1, column 1").fetchSemanticsNode()
    }
}
package dev.pocketdev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesOnDashboard() {
        composeRule.onNodeWithText("AI-powered development workstation").assertIsDisplayed()
    }

    @Test
    fun dashboardToProjects() {
        composeRule.onNode(hasText("Projects") and isSelectable()).performClick()

        composeRule.onNodeWithText("No projects yet").assertIsDisplayed()
        composeRule.onNodeWithText("New project").assertIsDisplayed()
    }

    @Test
    fun dashboardToAgentWorkspace() {
        composeRule.onNode(hasText("Agent") and isSelectable()).performClick()

        composeRule.onNodeWithText("Agent workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Task state").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("New session").fetchSemanticsNode()
        composeRule.onNodeWithText("Prompt").fetchSemanticsNode()
    }

    @Test
    fun dashboardToFiles() {
        composeRule.onNode(hasText("Files") and isSelectable()).performClick()

        composeRule.onNodeWithText("Project files").assertIsDisplayed()
        composeRule.onNodeWithText("New folder").fetchSemanticsNode()
        composeRule.onNodeWithText("Search files").fetchSemanticsNode()
    }

    @Test
    fun dashboardToEditor() {
        composeRule.onNode(hasText("Editor") and isSelectable()).performClick()

        composeRule.onNodeWithText("Open a text file from Files").assertIsDisplayed()
        composeRule.onNodeWithText("Save").fetchSemanticsNode()
    }

    @Test
    fun dashboardToTerminal() {
        composeRule.onNode(hasText("Terminal") and isSelectable()).performClick()

        composeRule.onNode(hasText("Working directory:", substring = true)).fetchSemanticsNode()
        composeRule.onNodeWithText("New session").fetchSemanticsNode()
        composeRule.onNodeWithText("Close session").fetchSemanticsNode()
        composeRule.onNodeWithText("Copy").fetchSemanticsNode()
    }

    @Test
    fun projectsToSettingsAndBackToDashboard() {
        composeRule.onNode(hasText("Projects") and isSelectable()).performClick()
        composeRule.onNodeWithText("No projects yet").assertIsDisplayed()

        composeRule.onNode(hasText("Settings") and isSelectable()).performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("AI provider").assertIsDisplayed()
        composeRule.onNodeWithText("Provider").fetchSemanticsNode()
        composeRule.onNodeWithText("Model").fetchSemanticsNode()

        composeRule.onNode(hasText("Dashboard") and isSelectable()).performClick()
        composeRule.onNodeWithText("AI-powered development workstation").assertIsDisplayed()
    }
}

package dev.pocketdev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("Task state").assertIsDisplayed()
        composeRule.onNodeWithText("New session").assertExists()
        composeRule.onNodeWithText("Prompt").assertExists()
    }

    @Test
    fun projectsToSettingsAndBackToDashboard() {
        composeRule.onNode(hasText("Projects") and isSelectable()).performClick()
        composeRule.onNodeWithText("No projects yet").assertIsDisplayed()

        composeRule.onNode(hasText("Settings") and isSelectable()).performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("AI provider").assertIsDisplayed()

        composeRule.onNode(hasText("Dashboard") and isSelectable()).performClick()
        composeRule.onNodeWithText("AI-powered development workstation").assertIsDisplayed()
    }
}

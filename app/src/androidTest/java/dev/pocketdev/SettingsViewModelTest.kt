package dev.pocketdev

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocketdev.settings.ThemeMode
import dev.pocketdev.ui.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private lateinit var app: PocketDevApplication
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.container.projectStore.replaceAll(emptyList())
        viewModel = SettingsViewModel(app)
    }

    @After
    fun tearDown() {
        app.container.projectStore.replaceAll(emptyList())
        app.container.setThemeMode(ThemeMode.SYSTEM)
    }

    @Test
    fun workspaceRootIsNonBlank() {
        assertTrue(viewModel.workspaceRoot.isNotBlank())
    }

    @Test
    fun themeModeRoundTripsThroughContainer() {
        viewModel.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun apiKeyDraftUpdatesAndClears() {
        viewModel.setApiKeyDraft("secret-draft")
        assertEquals("secret-draft", viewModel.apiKeyDraft.value)

        viewModel.setApiKeyDraft("")
        assertEquals("", viewModel.apiKeyDraft.value)
    }

    @Test
    fun clearProjectsEmptiesRegistry() {
        app.container.projectStore.add(
            dev.pocketdev.core.workspace.Project(
                id = "p1",
                name = "p1",
                rootPath = app.container.projectsRoot.absolutePath,
                createdAtEpochMs = 0L,
            )
        )
        assertTrue(app.container.projectStore.load().isNotEmpty())

        viewModel.clearProjects()

        assertTrue(app.container.projectStore.load().isEmpty())
    }
}

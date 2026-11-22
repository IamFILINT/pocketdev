package dev.pocketdev.runtime

import dev.pocketdev.core.runtime.RuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeControllerStateTest {

    @Test
    fun readyWhenJournalCompletedVersionPresentAndPreloadDeployed() {
        val state = RuntimeController.initialStateFrom(
            journalCompleted = true,
            version = "bootstrap-2026.11.15-r1+apt.android-7",
            preloadDeployed = true,
        )
        assertTrue(state is RuntimeState.Ready)
        assertEquals("bootstrap-2026.11.15-r1+apt.android-7", (state as RuntimeState.Ready).version)
    }

    @Test
    fun notInstalledWhenJournalIncomplete() {
        assertEquals(
            RuntimeState.NotInstalled,
            RuntimeController.initialStateFrom(false, "v1", true),
        )
    }

    @Test
    fun notInstalledWhenVersionNull() {
        assertEquals(
            RuntimeState.NotInstalled,
            RuntimeController.initialStateFrom(true, null, true),
        )
    }

    @Test
    fun notInstalledWhenPreloadNotDeployed() {
        assertEquals(
            RuntimeState.NotInstalled,
            RuntimeController.initialStateFrom(true, "v1", false),
        )
    }
}
EOF
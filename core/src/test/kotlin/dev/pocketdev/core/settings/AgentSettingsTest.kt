package dev.pocketdev.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentSettingsTest {

    @Test
    fun defaultsUseServerDefaults() {
        assertEquals(AgentSettings(), AgentSettings.DEFAULT)
        assertEquals("", AgentSettings.DEFAULT.provider)
        assertEquals("", AgentSettings.DEFAULT.model)
    }

    @Test
    fun storageValuesAreTrimmed() {
        val settings = AgentSettings.fromStorage("  anthropic  ", " claude-sonnet-4 ")

        assertEquals("anthropic", settings.provider)
        assertEquals("claude-sonnet-4", settings.model)
    }

    @Test
    fun nullStorageValuesBecomeDefaults() {
        assertEquals(AgentSettings.DEFAULT, AgentSettings.fromStorage(null, null))
    }

    @Test
    fun selectedPairBecomesModelSelection() {
        val selection = AgentSettings("anthropic", "claude-sonnet-4").modelSelection

        assertEquals("anthropic", selection?.providerId)
        assertEquals("claude-sonnet-4", selection?.modelId)
        assertEquals(null, selection?.variant)
    }

    @Test
    fun blankDefaultsHaveNoModelSelection() {
        assertEquals(null, AgentSettings.DEFAULT.modelSelection)
    }

    @Test
    fun partialProviderModelPairIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentSettings(provider = "anthropic", model = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentSettings(provider = "", model = "claude-sonnet-4")
        }
    }

    @Test
    fun controlCharactersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentSettings(provider = "bad\nvalue", model = "")
        }
    }

    @Test
    fun oversizedValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentSettings(provider = "x".repeat(129), model = "")
        }
    }
}

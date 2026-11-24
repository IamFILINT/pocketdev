package dev.pocketdev.runtime

import dev.pocketdev.core.settings.AgentModelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeAgentConfigTest {

    private val model = AgentModelSelection("openrouter", "anthropic/claude-sonnet-4")

    @Test
    fun configUsesPlaceholderInsteadOfTheSecret() {
        val secret = "sk-agent-secret"
        val config = RuntimeAgentConfig(model, secret)

        assertEquals(
            """{"model":"openrouter/anthropic/claude-sonnet-4","providers":{"openrouter":{"env":["POCKETDEV_API_KEY"],"settings":{"apiKey":"{env:POCKETDEV_API_KEY}"}}}}""",
            config.configContent,
        )
        assertFalse(config.configContent!!.contains(secret))
        assertTrue(config.configContent!!.contains("{env:POCKETDEV_API_KEY}"))
        assertEquals(config.configContent, config.environment[RuntimeAgentConfig.CONFIG_ENV])
        assertEquals(secret, config.environment[RuntimeAgentConfig.CREDENTIAL_ENV])
    }

    @Test
    fun configIsOmittedWithoutAModel() {
        val config = RuntimeAgentConfig(null, "sk-agent-secret")

        assertEquals(null, config.configContent)
        assertEquals(emptyMap<String, String>(), config.environment)
    }

    @Test
    fun environmentCarriesConfigAndCarriesTheKeyOnlyWhenPresent() {
        val secret = "sk-agent-secret"
        val withKey = RuntimeAgentConfig(model, secret)
        val withoutKey = RuntimeAgentConfig(model, null)
        val withBlankKey = RuntimeAgentConfig(model, "  ")

        assertEquals(
            mapOf(
                RuntimeAgentConfig.CONFIG_ENV to withKey.configContent!!,
                RuntimeAgentConfig.CREDENTIAL_ENV to secret,
            ),
            withKey.environment,
        )
        assertEquals(
            mapOf(RuntimeAgentConfig.CONFIG_ENV to withoutKey.configContent!!),
            withoutKey.environment,
        )
        assertEquals(
            mapOf(RuntimeAgentConfig.CONFIG_ENV to withBlankKey.configContent!!),
            withBlankKey.environment,
        )
    }
}

package dev.pocketdev.runtime

import dev.pocketdev.core.settings.AgentModelSelection

data class RuntimeAgentConfig(
    val model: AgentModelSelection? = null,
    val apiKey: String? = null,
) {
    val configContent: String?
        get() = model?.let { selection ->
            val placeholder = "{env:$CREDENTIAL_ENV}"
            """{"model":"${selection.providerId}/${selection.modelId}","providers":{"${selection.providerId}":{"env":["$CREDENTIAL_ENV"],"settings":{"apiKey":"$placeholder"}}}}"""
        }

    val environment: Map<String, String>
        get() = if (model != null && !apiKey.isNullOrBlank()) {
            mapOf(CREDENTIAL_ENV to apiKey)
        } else {
            emptyMap()
        }

    companion object {
        const val CREDENTIAL_ENV = "POCKETDEV_API_KEY"
        val NONE = RuntimeAgentConfig()
    }
}

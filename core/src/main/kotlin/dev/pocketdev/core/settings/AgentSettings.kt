package dev.pocketdev.core.settings

/**
 * Non-secret provider/model selection for one OpenCode session.
 *
 * The wire shape for session endpoints is an object with `providerID` and `id`;
 * the `provider/model` shorthand is only valid in configuration files.
 */
data class AgentModelSelection(
    val providerId: String,
    val modelId: String,
    val variant: String? = null,
) {
    init {
        require(validId(providerId)) { "invalid provider id" }
        require(validId(modelId)) { "invalid model id" }
        require(variant == null || validId(variant)) { "invalid model variant" }
    }

    companion object {

        private val VALID_ID = Regex("[A-Za-z0-9._/-]+")

        private fun validId(value: String): Boolean =
            value.isNotEmpty() && value.length <= 128 && VALID_ID.matches(value)
    }
}

/**
 * Non-secret AI provider/model preferences.
 *
 * Blank values mean "use the OpenCode server default"; no secret is stored here.
 * API keys are held by the app's Keystore-backed secret store.
 */
data class AgentSettings(
    val provider: String = "",
    val model: String = "",
) {

    init {
        require(valid(provider)) { "invalid provider value" }
        require(valid(model)) { "invalid model value" }
        require(provider.isEmpty() == model.isEmpty()) {
            "provider and model must both be set or both blank"
        }
    }

    val modelSelection: AgentModelSelection?
        get() = if (provider.isEmpty()) {
            null
        } else {
            AgentModelSelection(provider, model)
        }

    companion object {

        private const val MAX_LENGTH = 128

        val DEFAULT = AgentSettings()

        fun fromStorage(provider: String?, model: String?): AgentSettings =
            AgentSettings(provider.orEmpty().trim(), model.orEmpty().trim())

        private fun valid(value: String): Boolean =
            value.length <= MAX_LENGTH && value.none { it.isISOControl() }
    }
}

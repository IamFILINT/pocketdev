package dev.pocketdev.core.settings

/**
 * Non-secret AI provider/model preferences.
 *
 * Blank values mean "use the OpenCode server default"; no secret is stored here.
 * API keys stay in memory until the Keystore-backed secret store lands.
 */
data class AgentSettings(
    val provider: String = "",
    val model: String = "",
) {

    init {
        require(valid(provider)) { "invalid provider value" }
        require(valid(model)) { "invalid model value" }
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

package dev.pocketdev.core.agent

enum class PermissionDecision(val wireValue: String) {
    ONCE("once"),
    ALWAYS("always"),
    REJECT("reject"),
}

package dev.pocketdev.core.validation

/**
 * Result of validating a user-provided value.
 *
 * Kept deliberately small and dependency-free so it can be unit-tested on the JVM
 * and reused by the UI layer without pulling Android APIs into the core module.
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: Reason) : ValidationResult

    enum class Reason {
        EMPTY,
        TOO_LONG,
        INVALID_CHARACTERS,
        RESERVED_NAME,
        LEADING_OR_TRAILING_WHITESPACE,
        HIDDEN_NAME,
    }
}

/**
 * Validates project names before they are used as a directory name inside the
 * PocketDev workspace.
 *
 * Rules are intentionally conservative: a project name becomes a directory name,
 * so it must not be able to escape the workspace, collide with special names, or
 * produce invisible entries.
 */
object ProjectNameValidator {

    const val MAX_LENGTH: Int = 64

    /** Characters that are unsafe in a filesystem path segment. */
    private val INVALID_CHARACTERS = Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]")

    /** Names that are reserved on common filesystems and should not be used. */
    private val RESERVED_NAMES = setOf(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )

    fun validate(name: String): ValidationResult {
        if (name.isEmpty()) return invalid(ValidationResult.Reason.EMPTY)
        if (name != name.trim()) {
            return invalid(ValidationResult.Reason.LEADING_OR_TRAILING_WHITESPACE)
        }
        if (name.length > MAX_LENGTH) return invalid(ValidationResult.Reason.TOO_LONG)
        if (INVALID_CHARACTERS.containsMatchIn(name)) {
            return invalid(ValidationResult.Reason.INVALID_CHARACTERS)
        }
        if (name.startsWith(".")) return invalid(ValidationResult.Reason.HIDDEN_NAME)
        if (name.lowercase() in RESERVED_NAMES) return invalid(ValidationResult.Reason.RESERVED_NAME)
        return ValidationResult.Valid
    }

    fun isValid(name: String): Boolean = validate(name) == ValidationResult.Valid

    private fun invalid(reason: ValidationResult.Reason) = ValidationResult.Invalid(reason)
}
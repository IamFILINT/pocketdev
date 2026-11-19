package dev.pocketdev.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNameValidatorTest {

    @Test
    fun `valid simple names are accepted`() {
        assertEquals(ValidationResult.Valid, ProjectNameValidator.validate("my-app"))
        assertEquals(ValidationResult.Valid, ProjectNameValidator.validate("App 2"))
        assertEquals(ValidationResult.Valid, ProjectNameValidator.validate("notes_2026"))
    }

    @Test
    fun `empty name is rejected`() {
        assertInvalid("", ValidationResult.Reason.EMPTY)
    }

    @Test
    fun `path separators are rejected`() {
        assertInvalid("a/b", ValidationResult.Reason.INVALID_CHARACTERS)
        assertInvalid("a\\b", ValidationResult.Reason.INVALID_CHARACTERS)
    }

    @Test
    fun `filesystem-special characters are rejected`() {
        listOf("a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b").forEach { name ->
            assertInvalid(name, ValidationResult.Reason.INVALID_CHARACTERS)
        }
    }

    @Test
    fun `control characters are rejected`() {
        assertInvalid("a\u0000b", ValidationResult.Reason.INVALID_CHARACTERS)
        assertInvalid("a\nb", ValidationResult.Reason.INVALID_CHARACTERS)
    }

    @Test
    fun `traversal segments are rejected`() {
        // ".." is a hidden name (starts with '.') and "../etc" contains a separator.
        assertInvalid("..", ValidationResult.Reason.HIDDEN_NAME)
        assertInvalid("../etc", ValidationResult.Reason.INVALID_CHARACTERS)
    }

    @Test
    fun `leading or trailing whitespace is rejected`() {
        assertInvalid(" app", ValidationResult.Reason.LEADING_OR_TRAILING_WHITESPACE)
        assertInvalid("app ", ValidationResult.Reason.LEADING_OR_TRAILING_WHITESPACE)
    }

    @Test
    fun `hidden names are rejected`() {
        assertInvalid(".git", ValidationResult.Reason.HIDDEN_NAME)
    }

    @Test
    fun `reserved device names are rejected case-insensitively`() {
        assertInvalid("con", ValidationResult.Reason.RESERVED_NAME)
        assertInvalid("NUL", ValidationResult.Reason.RESERVED_NAME)
        assertInvalid("Com1", ValidationResult.Reason.RESERVED_NAME)
    }

    @Test
    fun `names longer than the maximum are rejected`() {
        val tooLong = "a".repeat(ProjectNameValidator.MAX_LENGTH + 1)
        assertInvalid(tooLong, ValidationResult.Reason.TOO_LONG)
    }

    @Test
    fun `isValid mirrors validate`() {
        assertTrue(ProjectNameValidator.isValid("pocket"))
        assertFalse(ProjectNameValidator.isValid(""))
    }

    private fun assertInvalid(name: String, reason: ValidationResult.Reason) {
        val result = ProjectNameValidator.validate(name)
        assertTrue("expected invalid for <$name> but was $result", result is ValidationResult.Invalid)
        assertEquals(reason, (result as ValidationResult.Invalid).reason)
    }
}
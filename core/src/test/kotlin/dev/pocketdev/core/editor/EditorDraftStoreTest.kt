package dev.pocketdev.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDraftStoreTest {
    private val documentId = EditorDocumentId("project-1", "/app/projects/project-1", "src/Main.kt")

    private fun draft(text: String = "line 1\nline 2") = EditorDraft(
        schemaVersion = EditorDraftStore.SCHEMA_VERSION,
        documentId = documentId,
        text = text,
        bufferRevision = 7,
        persistedRevision = 5,
        baselineSignature = FileSignature(42L, 1234L, "abc123"),
        encoding = TextEncoding.UTF_8_BOM,
        newlineStyle = NewlineStyle.CRLF,
        newlineStyles = listOf(NewlineStyle.CRLF, NewlineStyle.LF),
        updatedAtEpochMs = 987654321L,
    )

    @Test
    fun `round trip preserves unicode and metadata`() {
        val original = draft("quote \" slash \\ emoji 😀\nsecond line")

        val raw = EditorDraftStore.serialize(original)
        val restored = EditorDraftStore.deserialize(raw)

        assertEquals(original, restored)
    }

    @Test
    fun `unreadable drafts preserve raw bytes without throwing`() {
        val corrupt = EditorDraftStore.read("{\"schemaVersion\":1,\"text\":\"oops\"")
        assertTrue(corrupt is EditorDraftRead.Unreadable)
        assertEquals("{\"schemaVersion\":1,\"text\":\"oops\"", (corrupt as EditorDraftRead.Unreadable).rawText)

        val unknown = EditorDraftStore.read(
            EditorDraftStore.serialize(draft()).replace("\"schemaVersion\":1", "\"schemaVersion\":99"),
        )
        assertTrue(unknown is EditorDraftRead.Unreadable)
        assertEquals("Unsupported draft schema version 99", (unknown as EditorDraftRead.Unreadable).reason)
    }

    @Test
    fun `draft file names are stable hashed and safe`() {
        val name = EditorDraftStore.fileName(documentId)

        assertTrue(EditorDraftStore.isDraftFileName(name))
        assertEquals(name, EditorDraftStore.fileName(documentId))
        assertFalse(EditorDraftStore.isDraftFileName("../../evil.draft.json"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized drafts are rejected`() {
        EditorDraftStore.serialize(draft("a".repeat(EditorDraftStore.MAX_TEXT_BYTES.toInt() + 1)))
    }
}

package dev.pocketdev.core.editor

data class EditorDraft(
    val schemaVersion: Int,
    val documentId: EditorDocumentId,
    val text: String,
    val bufferRevision: Long,
    val persistedRevision: Long,
    val baselineSignature: FileSignature,
    val encoding: TextEncoding,
    val newlineStyle: NewlineStyle,
    val newlineStyles: List<NewlineStyle>,
    val updatedAtEpochMs: Long,
)

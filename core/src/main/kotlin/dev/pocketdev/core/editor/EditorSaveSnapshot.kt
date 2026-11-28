package dev.pocketdev.core.editor

data class EditorSaveSnapshot(
    val documentId: EditorDocumentId,
    val text: String,
    val revision: Long,
    val encoding: TextEncoding,
    val newlineStyle: NewlineStyle,
    val newlineStyles: List<NewlineStyle>,
    val expectedSignature: FileSignature?,
)

package dev.pocketdev.core.editor

import dev.pocketdev.core.json.Json
import dev.pocketdev.core.json.asArray
import dev.pocketdev.core.json.asLong
import dev.pocketdev.core.json.asObject
import dev.pocketdev.core.json.asString
import java.security.MessageDigest

sealed interface EditorDraftRead {
    data object Missing : EditorDraftRead

    data class Usable(val draft: EditorDraft) : EditorDraftRead

    data class Unreadable(val reason: String, val rawText: String) : EditorDraftRead
}

object EditorDraftStore {
    const val SCHEMA_VERSION = 1
    const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    private const val SUFFIX = ".draft.json"
    private val FILE_NAME = Regex("[0-9a-f]{64}\\.draft\\.json")

    fun fileName(documentId: EditorDocumentId): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(documentId.key.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2 + SUFFIX.length) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append("0123456789abcdef"[value ushr 4])
                append("0123456789abcdef"[value and 0x0f])
            }
            append(SUFFIX)
        }
    }

    fun isDraftFileName(name: String): Boolean = FILE_NAME.matches(name)

    fun serialize(draft: EditorDraft): String {
        require(draft.schemaVersion == SCHEMA_VERSION) {
            "Unsupported draft schema version ${draft.schemaVersion}"
        }
        val encodedBytes = TextFileCodec.encode(
            text = draft.text,
            encoding = draft.encoding,
            newlineStyle = draft.newlineStyle,
            newlineStyles = draft.newlineStyles,
        ).size
        require(encodedBytes <= MAX_TEXT_BYTES) {
            "Draft text is $encodedBytes bytes; limit is $MAX_TEXT_BYTES"
        }
        return buildString {
            append("{\"schemaVersion\":").append(draft.schemaVersion)
            append(",\"documentId\":").append(documentIdJson(draft.documentId))
            append(",\"text\":").append(Json.escape(draft.text))
            append(",\"bufferRevision\":").append(draft.bufferRevision)
            append(",\"persistedRevision\":").append(draft.persistedRevision)
            append(",\"baselineSignature\":").append(signatureJson(draft.baselineSignature))
            append(",\"encoding\":").append(Json.escape(draft.encoding.name))
            append(",\"newlineStyle\":").append(Json.escape(draft.newlineStyle.name))
            append(",\"newlineStyles\":[")
            draft.newlineStyles.forEachIndexed { index, style ->
                if (index > 0) append(',')
                append(Json.escape(style.name))
            }
            append("],\"updatedAtEpochMs\":").append(draft.updatedAtEpochMs)
            append('}')
        }
    }

    fun read(rawText: String): EditorDraftRead = try {
        EditorDraftRead.Usable(deserialize(rawText))
    } catch (error: Exception) {
        EditorDraftRead.Unreadable(
            reason = error.message ?: "Unreadable draft",
            rawText = rawText,
        )
    }

    fun deserialize(rawText: String): EditorDraft {
        val root = Json.parseObject(rawText)
        val schemaVersion = requireLong(root, "schemaVersion").toInt()
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported draft schema version $schemaVersion"
        }
        val document = requireObject(root, "documentId")
        val signature = requireObject(root, "baselineSignature")
        val newlineStyles = requireArray(root, "newlineStyles").map { value ->
            enumValue<NewlineStyle>(
                "newlineStyles",
                value.asString() ?: throw IllegalArgumentException("Invalid newlineStyles value"),
            )
        }
        return EditorDraft(
            schemaVersion = schemaVersion,
            documentId = EditorDocumentId(
                workspaceId = requireString(document, "workspaceId"),
                rootPath = requireString(document, "rootPath"),
                relativePath = requireString(document, "relativePath"),
            ),
            text = requireString(root, "text"),
            bufferRevision = requireLong(root, "bufferRevision"),
            persistedRevision = requireLong(root, "persistedRevision"),
            baselineSignature = FileSignature(
                sizeBytes = requireLong(signature, "sizeBytes"),
                lastModifiedEpochMs = requireLong(signature, "lastModifiedEpochMs"),
                contentSha256 = requireString(signature, "contentSha256"),
            ),
            encoding = enumValue("encoding", requireString(root, "encoding")),
            newlineStyle = enumValue("newlineStyle", requireString(root, "newlineStyle")),
            newlineStyles = newlineStyles,
            updatedAtEpochMs = requireLong(root, "updatedAtEpochMs"),
        )
    }

    private fun documentIdJson(id: EditorDocumentId): String = buildString {
        append("{\"workspaceId\":").append(Json.escape(id.workspaceId))
        append(",\"rootPath\":").append(Json.escape(id.rootPath))
        append(",\"relativePath\":").append(Json.escape(id.relativePath))
        append('}')
    }

    private fun signatureJson(signature: FileSignature): String = buildString {
        append("{\"sizeBytes\":").append(signature.sizeBytes)
        append(",\"lastModifiedEpochMs\":").append(signature.lastModifiedEpochMs)
        append(",\"contentSha256\":").append(Json.escape(signature.contentSha256))
        append('}')
    }

    private inline fun <reified T : Enum<T>> enumValue(field: String, value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Invalid $field value: $value")

    private fun requireObject(root: Map<String, Any?>, key: String): Map<String, Any?> =
        root[key].asObject() ?: throw IllegalArgumentException("Missing or invalid $key")

    private fun requireArray(root: Map<String, Any?>, key: String): List<Any?> =
        root[key].asArray() ?: throw IllegalArgumentException("Missing or invalid $key")

    private fun requireString(root: Map<String, Any?>, key: String): String =
        root[key].asString() ?: throw IllegalArgumentException("Missing or invalid $key")

    private fun requireLong(root: Map<String, Any?>, key: String): Long =
        root[key].asLong() ?: throw IllegalArgumentException("Missing or invalid $key")
}

package dev.pocketdev.ui.editor

import dev.pocketdev.AppContainer
import dev.pocketdev.core.editor.EditorDocumentId
import dev.pocketdev.core.editor.EditorDraft
import dev.pocketdev.core.editor.EditorDraftRead
import dev.pocketdev.core.editor.EditorDraftStore
import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.TextFileCodec
import dev.pocketdev.core.workspace.WorkspaceFiles
import dev.pocketdev.core.workspace.WorkspaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface EditorDraftRepository {
    suspend fun save(draft: EditorDraft)

    suspend fun load(documentId: EditorDocumentId): EditorDraftRead

    suspend fun delete(documentId: EditorDocumentId)
}

class DefaultEditorDraftRepository(
    private val container: AppContainer,
) : EditorDraftRepository {

    private val directory: File get() = container.paths.drafts

    override suspend fun save(draft: EditorDraft) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = draftFile(draft.documentId)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(EditorDraftStore.serialize(draft), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: UnsupportedOperationException) {
            moveFallback(temporary, target)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            moveFallback(temporary, target)
        }
        prune()
    }

    override suspend fun load(documentId: EditorDocumentId): EditorDraftRead =
        withContext(Dispatchers.IO) {
            val file = draftFile(documentId)
            if (!file.isFile) {
                EditorDraftRead.Missing
            } else {
                EditorDraftStore.read(file.readText(Charsets.UTF_8))
            }
        }

    override suspend fun delete(documentId: EditorDocumentId) = withContext(Dispatchers.IO) {
        val file = draftFile(documentId)
        File(directory, "${file.name}.tmp").delete()
        if (file.exists()) file.delete()
    }

    private fun draftFile(documentId: EditorDocumentId): File {
        directory.mkdirs()
        val canonicalDirectory = directory.canonicalFile
        val file = File(canonicalDirectory, EditorDraftStore.fileName(documentId))
        require(file.parentFile?.canonicalFile == canonicalDirectory) {
            "Draft path escapes app-private storage"
        }
        require(!Files.isSymbolicLink(file.toPath())) {
            "Draft path is a symlink"
        }
        return file
    }

    private fun moveFallback(source: File, target: File) {
        if (!source.renameTo(target) && source.exists()) {
            throw IllegalStateException("Could not commit editor draft")
        }
    }

    private suspend fun prune() {
        val files = directory.listFiles { file -> EditorDraftStore.isDraftFileName(file.name) }
            ?.toList()
            ?.sortedBy { it.lastModified() }
            ?: return
        val excess = files.size - MAX_DRAFTS
        if (excess <= 0) return
        files.take(excess).forEach { file ->
            val read = runCatching { EditorDraftStore.read(file.readText(Charsets.UTF_8)) }
                .getOrNull()
            if (read is EditorDraftRead.Usable && isRedundant(read.draft)) {
                file.delete()
            }
        }
    }

    private suspend fun isRedundant(draft: EditorDraft): Boolean {
        val root = runCatching { container.validatedRootPath(draft.documentId.rootPath) }.getOrNull()
            ?: return false
        val project = container.projectStore.load()
            .firstOrNull { it.id == draft.documentId.workspaceId }
            ?: return false
        val expectedRoot = runCatching { container.validatedRootPath(project.rootPath) }.getOrNull()
            ?: return false
        if (root.canonicalFile != expectedRoot.canonicalFile) return false
        val result = WorkspaceFiles(root).readTextFile(draft.documentId.normalizedRelativePath)
        if (result !is WorkspaceResult.Success) return false
        if (result.value.signature.contentSha256 != draft.baselineSignature.contentSha256) return false
        val encoded = TextFileCodec.encode(
            text = draft.text,
            encoding = draft.encoding,
            newlineStyle = draft.newlineStyle,
            newlineStyles = draft.newlineStyles,
        )
        return FileSignature.fromBytes(encoded, result.value.signature.lastModifiedEpochMs)
            .contentSha256 == result.value.signature.contentSha256
    }

    private companion object {
        const val MAX_DRAFTS = 32
    }
}

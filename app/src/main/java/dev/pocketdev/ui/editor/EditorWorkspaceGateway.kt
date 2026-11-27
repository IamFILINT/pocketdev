package dev.pocketdev.ui.editor

import dev.pocketdev.AppContainer
import dev.pocketdev.core.editor.EditorDocumentId
import dev.pocketdev.core.editor.EditorSaveSnapshot
import dev.pocketdev.core.editor.FileSignature
import dev.pocketdev.core.editor.TextFileSnapshot
import dev.pocketdev.core.workspace.WorkspaceFiles
import dev.pocketdev.core.workspace.WorkspaceResult
import java.io.File
import kotlinx.coroutines.CancellationException

interface EditorWorkspaceGateway {
    suspend fun readTextFile(documentId: EditorDocumentId): WorkspaceResult<TextFileSnapshot>

    suspend fun writeTextFile(request: EditorSaveSnapshot): WorkspaceResult<FileSignature>
}

class DefaultEditorWorkspaceGateway(
    private val container: AppContainer,
) : EditorWorkspaceGateway {

    override suspend fun readTextFile(documentId: EditorDocumentId): WorkspaceResult<TextFileSnapshot> =
        withBoundRoot(documentId) { root ->
            WorkspaceFiles(root).readTextFile(documentId.normalizedRelativePath)
        }

    override suspend fun writeTextFile(request: EditorSaveSnapshot): WorkspaceResult<FileSignature> =
        withBoundRoot(request.documentId) { root ->
            WorkspaceFiles(root).writeTextFile(
                relativePath = request.documentId.normalizedRelativePath,
                text = request.text,
                encoding = request.encoding,
                newlineStyle = request.newlineStyle,
                expectedSignature = request.expectedSignature,
            )
        }

    private suspend fun <T> withBoundRoot(
        documentId: EditorDocumentId,
        block: suspend (File) -> WorkspaceResult<T>,
    ): WorkspaceResult<T> =
        try {
            block(resolveRoot(documentId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WorkspaceResult.Failure(error.message ?: "Editor workspace is unavailable")
        }

    private fun resolveRoot(documentId: EditorDocumentId): File {
        val root = container.validatedRootPath(documentId.rootPath)
        if (documentId.workspaceId == EditorDocumentId.SHARED_WORKSPACE_ID) {
            require(root.canonicalFile == container.projectsRoot.canonicalFile) {
                "Bound workspace is no longer available"
            }
        } else {
            val project = container.projectStore.load().firstOrNull { it.id == documentId.workspaceId }
                ?: throw IllegalStateException("Bound workspace is no longer available")
            val expectedRoot = container.validatedRootPath(project.rootPath)
            require(root.canonicalFile == expectedRoot.canonicalFile) {
                "Bound workspace root has changed"
            }
        }
        return root
    }
}

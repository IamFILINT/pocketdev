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
        WorkspaceFiles(resolveRoot(documentId)).readTextFile(documentId.relativePath)

    override suspend fun writeTextFile(request: EditorSaveSnapshot): WorkspaceResult<FileSignature> =
        WorkspaceFiles(resolveRoot(request.documentId)).writeTextFile(
            relativePath = request.documentId.relativePath,
            text = request.text,
            encoding = request.encoding,
            newlineStyle = request.newlineStyle,
            expectedSignature = request.expectedSignature,
        )

    private fun resolveRoot(documentId: EditorDocumentId): File {
        val selected = container.selectedProject.value
        val context = runCatching { container.projectContext(selected) }
        val rootPath = context.getOrNull()?.rootPath ?: container.projectsRoot.absolutePath
        return container.validatedRootPath(rootPath)
    }
}

package dev.pocketdev.core.editor

data class EditorDocumentId(
    val workspaceId: String,
    val rootPath: String,
    val relativePath: String,
) {
    val normalizedRelativePath: String
        get() = relativePath.replace('\\', '/').trim('/')

    val key: String
        get() = "$workspaceId\u0000$rootPath\u0000$normalizedRelativePath"

    companion object {
        const val SHARED_WORKSPACE_ID = "shared-projects-root"
    }
}

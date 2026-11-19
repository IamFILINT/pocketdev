package dev.pocketdev.core.workspace

/**
 * Stable identity of a PocketDev project. Every subsystem (editor, terminal, agent,
 * Git) refers to the same project by this identity and the same on-disk root.
 */
data class Project(
    val id: String,
    val name: String,
    val rootPath: String,
    val createdAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(rootPath.isNotBlank()) { "rootPath must not be blank" }
    }
}
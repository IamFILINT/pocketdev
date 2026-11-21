package dev.pocketdev.core.runtime

import java.io.File

/**
 * Canonical app-private runtime layout (docs/BOOTSTRAP_LAYOUT.md).
 *
 * Keeping these paths in one place lets the layout invariants be unit-tested:
 * `$PREFIX` is exactly `<files>/usr`, and project/state data lives outside it so a
 * runtime reinstall can never delete user work.
 */
class RuntimePaths(val filesDir: File) {

    /** `$PREFIX` — the runtime rootfs. */
    val prefix: File = File(filesDir, "usr")

    /** `$HOME`. */
    val home: File = File(filesDir, "home")

    /** Workspace root; never inside [prefix]. */
    val projects: File = File(home, "projects")

    /** PocketDev registry and runtime journal; never inside [prefix]. */
    val state: File = File(filesDir, "state")

    val journal: File = File(state, "runtime.json")
    val staging: File = File(filesDir, "usr.staging")
    val backup: File = File(filesDir, "usr.prev")

    fun ensureBaseDirectories() {
        listOf(projects, state).forEach { it.mkdirs() }
    }
}

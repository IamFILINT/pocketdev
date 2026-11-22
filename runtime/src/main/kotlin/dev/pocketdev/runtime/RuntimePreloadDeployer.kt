package dev.pocketdev.runtime

import java.io.File

/**
 * Ensures that the stock `termux-exec-ld-preload.so` from the pinned bootstrap
 * is present inside the runtime prefix at `$PREFIX/lib/libtermux-exec-ld-preload.so`.
 *
 * After a successful bootstrap provisioning (archive extraction → staging swap),
 * the preload library should already be at this location. This class is a
 * lightweight guard: it exists / does not, and may be checked after provisioning
 * to confirm the chain is ready.
 */
class RuntimePreloadDeployer(private val prefix: File) {
    val preloadPath: File = File(prefix, "lib/$PRELOAD_LIB_NAME")

    /** Returns `true` if the preload library is present at the expected path. */
    fun isDeployed(): Boolean = preloadPath.isFile

    /** Convenience: same as `isDeployed()` but returns the file (or `null`). */
    fun preloadFile(): File? = if (isDeployed()) preloadPath else null

    companion object {
        const val PRELOAD_LIB_NAME = "libtermux-exec-ld-preload.so"
    }
}
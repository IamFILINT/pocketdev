package dev.pocketdev.core.runtime

import kotlin.reflect.KClass

/**
 * Lifecycle of the integrated Android/Bionic runtime.
 *
 * Only states that correspond to real implementation stages are modelled. The UI
 * must render these states directly and never invent progress.
 */
sealed interface RuntimeState {

    /** No runtime has been provisioned yet. */
    data object NotInstalled : RuntimeState

    /** Device checks (ABI, API level, free storage) are running. */
    data object Checking : RuntimeState

    /** A pinned bootstrap artifact is being downloaded. */
    data class Downloading(val bytesReceived: Long, val totalBytes: Long) : RuntimeState {
        init {
            require(bytesReceived >= 0) { "bytesReceived must be >= 0" }
            require(totalBytes >= 0) { "totalBytes must be >= 0" }
        }

        val fraction: Float
            get() = if (totalBytes <= 0L) 0f else (bytesReceived.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
    }

    /** Checksum/signature verification of the downloaded artifact. */
    data object Verifying : RuntimeState

    /** Safe extraction of the verified artifact into app-private storage. */
    data class Extracting(val completedEntries: Int, val totalEntries: Int) : RuntimeState

    /** Runtime is healthy and ready to run commands. */
    data class Ready(val runtimeVersion: String) : RuntimeState

    /** A controlled update of an existing runtime. */
    data object Updating : RuntimeState

    /** Provisioning failed at a specific stage. */
    data class Failed(val stage: Stage, val message: String) : RuntimeState

    enum class Stage { CHECK, DOWNLOAD, VERIFY, EXTRACT, HEALTH_CHECK, UPDATE }
}

/**
 * Explicit allow-list of runtime state transitions. Any transition not listed here
 * is a programming error and is rejected, which keeps bootstrap logic honest and
 * unit-testable.
 */
object RuntimeStateMachine {

    private val transitions: Map<KClass<out RuntimeState>, Set<KClass<out RuntimeState>>> = mapOf(
        RuntimeState.NotInstalled::class to setOf(
            RuntimeState.Checking::class,
            RuntimeState.Failed::class,
        ),
        RuntimeState.Checking::class to setOf(
            RuntimeState.Downloading::class,
            RuntimeState.Verifying::class,
            RuntimeState.Ready::class,
            RuntimeState.Failed::class,
            RuntimeState.NotInstalled::class,
        ),
        RuntimeState.Downloading::class to setOf(
            RuntimeState.Verifying::class,
            RuntimeState.Failed::class,
            RuntimeState.NotInstalled::class,
        ),
        RuntimeState.Verifying::class to setOf(
            RuntimeState.Extracting::class,
            RuntimeState.Failed::class,
            RuntimeState.NotInstalled::class,
        ),
        RuntimeState.Extracting::class to setOf(
            RuntimeState.Ready::class,
            RuntimeState.Failed::class,
            RuntimeState.NotInstalled::class,
        ),
        RuntimeState.Ready::class to setOf(
            RuntimeState.Updating::class,
            RuntimeState.NotInstalled::class,
        ),
        RuntimeState.Updating::class to setOf(
            RuntimeState.Checking::class,
            RuntimeState.Failed::class,
        ),
        RuntimeState.Failed::class to setOf(
            RuntimeState.Checking::class,
            RuntimeState.NotInstalled::class,
            RuntimeState.Downloading::class,
        ),
    )

    fun canTransition(from: RuntimeState, to: RuntimeState): Boolean =
        transitions[from::class]?.contains(to::class) == true

    /**
     * @throws IllegalStateException when the transition is not allowed.
     */
    fun requireTransition(from: RuntimeState, to: RuntimeState): RuntimeState {
        check(canTransition(from, to)) {
            "Illegal runtime transition: ${from::class.simpleName} -> ${to::class.simpleName}"
        }
        return to
    }

    val isInstalled: (RuntimeState) -> Boolean = { it is RuntimeState.Ready }
}
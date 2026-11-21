package dev.pocketdev.core.runtime

/**
 * Bootstrap artifacts PocketDev has actually audited and pinned.
 *
 * The SHA-256 values below were computed from the downloaded release assets on
 * 2026-11-21 (the release does not publish a checksums file). Nothing is
 * provisioned by referencing a pin — it is metadata the bootstrap manager verifies
 * before it touches `$PREFIX`.
 *
 * Only `aarch64` is pinned today because that is the product's device target. An
 * `x86_64` pin is deliberately deferred until the ADR-0002 spike decides whether
 * the emulator may host a runtime for functional tests.
 */
object BootstrapPins {

    const val RELEASE_TAG: String = "bootstrap-2026.11.15-r1+apt.android-7"

    private const val BASE_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.11.15-r1%2Bapt.android-7"

    const val AARCH64_SHA256: String =
        "388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7"

    const val AARCH64_SIZE_BYTES: Long = 33_148_806L

    val aarch64: BootstrapArtifact = BootstrapArtifact(
        releaseTag = RELEASE_TAG,
        fileName = "bootstrap-aarch64.zip",
        downloadUrl = "$BASE_URL/bootstrap-aarch64.zip",
        sha256 = AARCH64_SHA256,
        sizeBytes = AARCH64_SIZE_BYTES,
        abi = Abi.AARCH64,
        minApiLevel = 24,
    )

    val manifest: BootstrapManifest = BootstrapManifest(RELEASE_TAG, listOf(aarch64))
}

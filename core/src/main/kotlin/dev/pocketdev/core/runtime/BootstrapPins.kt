package dev.pocketdev.core.runtime

/**
 * Bootstrap artifacts PocketDev has actually audited and pinned.
 *
 * The SHA-256 values below were computed from the downloaded release assets on
 * 2026-11-21 (the release does not publish a checksums file). Nothing is
 * provisioned by referencing a pin — it is metadata the bootstrap manager verifies
 * before it touches `$PREFIX`.
 *
 * `aarch64` is the product's device target. `x86_64` is pinned only for the
 * ADR-0002 execution spike on the x86_64 API 30 emulator (the only place a Bionic
 * process can actually run in CI); it is never shipped.
 */
object BootstrapPins {

    const val RELEASE_TAG: String = "bootstrap-2026.11.15-r1+apt.android-7"

    private const val BASE_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.11.15-r1%2Bapt.android-7"

    const val AARCH64_SHA256: String =
        "388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7"

    const val AARCH64_SIZE_BYTES: Long = 33_148_806L

    const val X86_64_SHA256: String =
        "fb95765f2ffb4cf5648891199dedb820ed19a63334352fb0bc69b7b7c917e002"

    const val X86_64_SIZE_BYTES: Long = 33_058_188L

    val aarch64: BootstrapArtifact = BootstrapArtifact(
        releaseTag = RELEASE_TAG,
        fileName = "bootstrap-aarch64.zip",
        downloadUrl = "$BASE_URL/bootstrap-aarch64.zip",
        sha256 = AARCH64_SHA256,
        sizeBytes = AARCH64_SIZE_BYTES,
        abi = Abi.AARCH64,
        minApiLevel = 24,
    )

    val x86_64: BootstrapArtifact = BootstrapArtifact(
        releaseTag = RELEASE_TAG,
        fileName = "bootstrap-x86_64.zip",
        downloadUrl = "$BASE_URL/bootstrap-x86_64.zip",
        sha256 = X86_64_SHA256,
        sizeBytes = X86_64_SIZE_BYTES,
        abi = Abi.X86_64,
        minApiLevel = 24,
    )

    val manifest: BootstrapManifest = BootstrapManifest(RELEASE_TAG, listOf(aarch64, x86_64))
}

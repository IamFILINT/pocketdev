package dev.pocketdev.core.agent

import dev.pocketdev.core.runtime.Abi

/**
 * One immutable, pinned OpenCode Bionic artifact.
 *
 * Mirrors [dev.pocketdev.core.runtime.BootstrapArtifact]: pure metadata that has
 * been audited, validated on construction so a malformed pin can never reach the
 * downloader.
 */
data class AgentArtifact(
    /** Upstream release tag, e.g. `v2.3.2`. */
    val releaseTag: String,
    /** Plain file name inside the release, e.g. `opencode`. */
    val fileName: String,
    /** HTTPS location the artifact is downloaded from. */
    val downloadUrl: String,
    /** Lowercase hex SHA-256 of the artifact contents. */
    val sha256: String,
    /** Exact artifact size in bytes. */
    val sizeBytes: Long,
    /** ABI the artifact was built for. */
    val abi: Abi,
    /** Lowest Android API level the artifact supports. */
    val minApiLevel: Int,
    /** Upstream project version the artifact was built from. */
    val upstreamVersion: String,
    /** SPDX license identifier of the upstream project. */
    val license: String,
) {

    init {
        require(releaseTag.isNotBlank()) { "releaseTag must not be blank" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(!fileName.contains('/') && !fileName.contains('\\')) {
            "fileName must be a plain file name, not a path"
        }
        require(downloadUrl.startsWith("https://")) { "downloadUrl must use https" }
        require(SHA256_PATTERN.matches(sha256)) {
            "sha256 must be 64 lowercase hexadecimal characters"
        }
        require(sizeBytes > 0) { "sizeBytes must be positive" }
        require(minApiLevel in 24..100) { "minApiLevel out of range: $minApiLevel" }
        require(upstreamVersion.isNotBlank()) { "upstreamVersion must not be blank" }
        require(license.isNotBlank()) { "license must not be blank" }
    }

    fun supports(abi: Abi, apiLevel: Int): Boolean = this.abi == abi && apiLevel >= minApiLevel

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

/**
 * Pins for OpenCode Bionic releases PocketDev has audited.
 *
 * Source: https://github.com/bd-loser/opencode-bionic (MIT). The `stable` channel
 * ships one self-contained Bionic aarch64 binary; `v2.3.2` was published
 * 2026-11-21 with its own `SHA256SUMS`. The binary embeds the web UI and requires
 * Android 10 / API 29 (patched Bun runtime).
 *
 * x86_64 is *not* pinned: the Bionic project only builds aarch64, so the agent
 * cannot run on the x86_64 CI emulator. Any execution proof must happen on a
 * physical ARM64 device (tracked as a known gap).
 */
object OpenCodePins {

    const val RELEASE_TAG: String = "v2.3.2"
    const val UPSTREAM_VERSION: String = "2.3.2"
    const val LICENSE: String = "MIT"

    const val SHA256: String =
        "ea93b4c42b20af123220f2bb69c9810c3b735c387d23ce78b0145dbb4f1b5f63"

    const val SIZE_BYTES: Long = 211_486_984L

    const val DOWNLOAD_URL: String =
        "https://github.com/bd-loser/opencode-bionic/releases/download/v2.3.2/opencode"

    val aarch64: AgentArtifact = AgentArtifact(
        releaseTag = RELEASE_TAG,
        fileName = "opencode",
        downloadUrl = DOWNLOAD_URL,
        sha256 = SHA256,
        sizeBytes = SIZE_BYTES,
        abi = Abi.AARCH64,
        minApiLevel = 29,
        upstreamVersion = UPSTREAM_VERSION,
        license = LICENSE,
    )
}

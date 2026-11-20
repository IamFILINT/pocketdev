package dev.pocketdev.core.runtime

/**
 * CPU architectures a runtime bootstrap can be provisioned for.
 *
 * [ndkAbi] matches the ABI strings used by Android (`Build.SUPPORTED_ABIS`) and
 * the Termux bootstrap file names.
 */
enum class Abi(val displayName: String, val ndkAbi: String) {
    AARCH64("ARM64", "arm64-v8a"),
    X86_64("x86_64", "x86_64"),
    ;
}

/**
 * One immutable, pinned bootstrap artifact.
 *
 * This is pure metadata: it describes *which* upstream artifact a future bootstrap
 * manager may download and how to verify it. It contains no live URLs until a real
 * upstream release has been audited and pinned (Milestone 3), so nothing here can be
 * mistaken for a provisioned runtime.
 *
 * Every field is validated on construction so a malformed pin can never reach the
 * downloader: the URL must be HTTPS, the checksum must be a well-formed SHA-256
 * digest, and the API range must be coherent.
 */
data class BootstrapArtifact(
    /** Upstream release tag the artifact belongs to, e.g. `bootstrap-2026.11.15-r1`. */
    val releaseTag: String,
    /** File name of the archive inside the release, e.g. `bootstrap-aarch64.zip`. */
    val fileName: String,
    /** HTTPS location the archive is downloaded from. */
    val downloadUrl: String,
    /** Lowercase hex SHA-256 of the archive contents. */
    val sha256: String,
    /** Exact archive size in bytes, used for progress reporting and sanity checks. */
    val sizeBytes: Long,
    /** Architecture the archive was built for. */
    val abi: Abi,
    /** Lowest Android API level the artifact supports. */
    val minApiLevel: Int,
    /** Highest Android API level the artifact supports, or `null` for no upper bound. */
    val maxApiLevel: Int? = null,
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
        require(minApiLevel in MIN_SUPPORTED_API..MAX_SUPPORTED_API) {
            "minApiLevel out of range: $minApiLevel"
        }
        require(maxApiLevel == null || maxApiLevel in minApiLevel..MAX_SUPPORTED_API) {
            "maxApiLevel must be >= minApiLevel and <= $MAX_SUPPORTED_API"
        }
    }

    /** Whether this artifact can run on [abi] at API level [apiLevel]. */
    fun supports(abi: Abi, apiLevel: Int): Boolean =
        this.abi == abi && apiLevel >= minApiLevel && (maxApiLevel == null || apiLevel <= maxApiLevel)

    companion object {
        const val MIN_SUPPORTED_API = 24
        const val MAX_SUPPORTED_API = 100

        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

/**
 * A validated set of pinned bootstrap artifacts covering one release.
 *
 * The manifest is the unit that a bootstrap manager pins and that CI checks: it must
 * contain exactly one artifact per supported ABI, and lookups by device ABI + API
 * level either return a matching artifact or `null` (never an ambiguous choice).
 */
class BootstrapManifest(
    val releaseTag: String,
    artifacts: List<BootstrapArtifact>,
) {
    val artifacts: List<BootstrapArtifact> = artifacts.toList()

    init {
        require(releaseTag.isNotBlank()) { "releaseTag must not be blank" }
        require(this.artifacts.isNotEmpty()) { "manifest must contain at least one artifact" }
        require(this.artifacts.all { it.releaseTag == releaseTag }) {
            "every artifact must belong to manifest releaseTag '$releaseTag'"
        }
        val duplicateAbis = this.artifacts.groupBy { it.abi }.filterValues { it.size > 1 }.keys
        require(duplicateAbis.isEmpty()) {
            "duplicate artifacts for ABI(s): ${duplicateAbis.joinToString { it.name }}"
        }
    }

    /** The artifact for this device, or `null` when no pin covers it. */
    fun artifactFor(abi: Abi, apiLevel: Int): BootstrapArtifact? =
        artifacts.firstOrNull { it.supports(abi, apiLevel) }
}

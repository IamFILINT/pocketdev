# PocketDev — CI/CD

> Maintained by the coding agent. See master prompt §5.

## Principle

**All compilation, testing, static analysis, emulator execution, and release builds run
on GitHub-hosted runners. The Android phone is the control surface only.** No local
build/test fallback is permitted.

## Workflows

### `verify.yml` — required pull-request gate

Triggers:
- `pull_request` to `main`
- `push` to any branch
- `workflow_dispatch`

Steps: checkout → Gradle wrapper validation → JDK 17 (Temurin) → Gradle setup →
Android SDK (`platforms;android-37.0`, `build-tools;36.0.0`/`37.0.0`) →
`./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug` (runs for
`:core`, `:runtime` and `:app`) → upload unit-test reports, lint report, and debug
APK artifacts.

Evidence: run `44623877843` (Milestone 0), run `44670913592` (Milestone 1 green),
run `44999755169` (Milestone 4 PR #16 green: +`OpenCodeServeOutputParser`,
`Json`, `OpenCodeApiClient`, `OpenCodeServer` tests), run `45006056315`
(native-runtime static verification green).

### `native-runtime-ci.yml` — static native payload gate

Runs on every PR and push, plus a weekly schedule and manual dispatch: downloads the
pinned OpenCode Bionic release and Termux bootstrap, verifies size + SHA-256 against
the in-source pins, confirms the agent ELF is 64-bit AArch64, validates the bootstrap
zip, and uploads an evidence artifact. This is static validation on an x86_64 runner;
it does not claim ARM64 execution (risk R1). All action references are immutable SHAs.

### `android-emulator.yml` — instrumentation gate (Milestone 1)

Boots x86_64 API 29 and API 36 AVDs with KVM enabled via
`ReactiveCircus/android-emulator-runner` (pinned SHA) and runs
`./gradlew connectedDebugAndroidTest` with the opt-in PTY classes excluded. Tests
cover app launch, navigation, Files, and real Android loopback HTTP through
`OpenCodeApiClient`. On failure the job uploads API-scoped instrumentation reports
and screenshots.

Evidence: baseline run `44672914788` (API 30, 3/3 tests); M6.5 API 29/36 matrix
evidence is recorded in `SESSION_NOTES.md` after the green run.

### `arm64-spike.yml` — ARM64 feasibility spike (Milestone 2, prompt §5.4)

Manual `workflow_dispatch` only — a measurement, **not** a merge gate. Runs a matrix
of `ubuntu-24.04-arm` (GitHub-hosted ARM64 Linux runner) and `ubuntu-latest` (x86_64
control) and, for each:

1. probes host arch, `/dev/kvm`, and whether the emulator binary can even execute;
2. installs the first available `arm64-v8a` system image (API 30 → 34 → 35);
3. boots a headless AVD with a 25-minute budget;
4. if booted: builds, installs, and launches the debug APK, then records ABI,
   Bionic (`toybox`), `linker64`, and app-data write evidence;
5. writes an explicit YES/NO verdict for each §5.4 question to the step summary and
   a `spike-<runner>` artifact.

The human-readable verdict is recorded in `docs/ARM64_FEASIBILITY.md`.

**Measured result (runs `44862773422`, `44866937289`):** no GitHub-hosted runner can
build or boot Android on ARM64 with the stock toolchain — x86_64 hosts reject ARM64
guests (emulator arch check), `ubuntu-24.04-arm` has neither an emulator host package
nor `/dev/kvm`, and its build fails because `aapt2` ships x86_64-only.

### `runtime-spike.yml` — opt-in x86_64 Bionic execution

Manual `workflow_dispatch` only, not a required gate. Provisions the pinned x86_64
bootstrap on an API 30 emulator and runs `RuntimeSpikeTest`,
`TerminalViewModelTest`, and `PtyReliabilityTest` with `bionicPty=true`. This is the
honest evidence for the `/system/bin/linker64` → `$PREFIX/bin/bash -i` PTY path,
including `PREFIX`, `HOME`, `LD_LIBRARY_PATH`, Termux data-dir variables, exit-status
reporting, launch-failure propagation, and rapid/concurrent session lifecycles. It is
opt-in because it downloads the full bootstrap and proves x86_64 behavior only.

Evidence: run `45329524820` passed the first two opt-in tests
(`RuntimeSpikeTest` and `TerminalViewModelTest`). After PTY reliability hardening,
run `45336872176` on `23df376` passed 5 tests with 0 failures, errors, or skips:
`RuntimeSpikeTest`, `TerminalViewModelTest`, and all three `PtyReliabilityTest`
cases. The artifact XML reports `time="9.217"`; PTY logcat records `linker64`
executing Bionic `bash`/`coreutils` and the real shell resolving `PREFIX`. This is
x86_64 evidence only and does not close ARM64 risk R1.

### Planned (later milestones)

- `release.yml` — signed APK + GitHub Release, gated on approved tags/dispatch.

## Branch protection (manual repository setting)

Baseline on 2026-11-24: `main` has no branch protection (`main/protection` returned
404 and rulesets returned `[]`). Maintainers must enable protection with these
required status checks:

- `Build, test, lint` (`verify.yml`)
- `Launch and navigation (API 29)` (`android-emulator.yml`)
- `Launch and navigation (API 36)` (`android-emulator.yml`)
- `Verify pinned aarch64 payloads (static, x86_64 runner)` (`native-runtime-ci.yml`)

This cannot be enforced from repository files. Until it is set in GitHub settings,
CI evidence is recorded here but merge enforcement is not claimed.

## Termux-inspired practices (adopted)

Patterns borrowed from Termux's own CI (`termux/termux-app`, `termux/termux-packages`),
which solve the same "build cross-arch payloads on x86_64 runners" problem:

- **Fast, separate unit-test gate** (`run_tests.yml` style) — JVM unit tests run on
  plain `ubuntu-latest` with JDK 17, no emulator cost.
- **APK assembly as its own build** with versioned names and a `sha256sums` file,
  plus per-ABI artifact uploads (PocketDev ships one universal APK, but keeps the
  sha256sums discipline).
- **Dedicated Gradle-wrapper validation workflow** (pinned action) instead of an
  inline step only.
- **Static validation of native payloads on x86_64** rather than claiming an
  emulator proves ARM64 — the Termux model for `aarch64` packages built on
  `x86_64` runners.
- **Least-privilege `permissions`** (`contents: read` default) and **SHA-scoped
  concurrency** with no cancellation of an earlier commit's verification.
- **No `pull_request_target`** checkouts of untrusted PR code (already policy).

Differences, by design: PocketDev keeps one required `verify.yml` build/test/lint gate,
one required instrumentation gate, and one required static native-payload gate (so
green checks are meaningful), and never auto-upgrades pinned runtime dependencies —
every pin bump is a reviewed PR with passing CI (master prompt §5.2E, §9.4).

- Third-party actions pinned to full immutable commit SHAs with a version comment.
- Least-privilege `permissions` (`contents: read` default).
- No secrets in source; signing via GitHub Environment secrets.
- No `pull_request_target` checkouts of untrusted PR code.

## Secrets (not yet set)

Documented for release (values never committed):
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Reproducibility

- Use the committed Gradle wrapper; validate distribution URL + checksum.
- Pin dependency versions; use lockfiles where supported.
- Cache Gradle dependencies.
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
- `push` to `main`
- `workflow_dispatch`

Steps: checkout → Gradle wrapper validation → JDK 17 (Temurin) → Gradle setup →
Android SDK (`platforms;android-37.0`, `build-tools;36.0.0`/`37.0.0`) →
`./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug` → upload unit-test
reports, lint report, and debug APK artifacts.

Evidence: run `44623877843` (Milestone 0), run `44670913592` (Milestone 1 green).

### `android-emulator.yml` — instrumentation gate (Milestone 1)

Boots an x86_64 API 30 emulator with KVM enabled via
`ReactiveCircus/android-emulator-runner` (pinned SHA) and runs
`./gradlew connectedDebugAndroidTest`. Tests cover app launch plus
dashboard → projects → settings navigation. On failure the job uploads the
instrumentation reports and an emulator screenshot.

Evidence: run `44672914788` (3/3 tests passed).

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

### Planned (later milestones)

- `native-runtime-ci.yml` — native/runtime pipeline (NDK/Clang/Rust/Zig cross-compile,
  ELF verification, OpenCode Bionic artifact acquisition).
- `release.yml` — signed APK + GitHub Release, gated on approved tags/dispatch.

## Security

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
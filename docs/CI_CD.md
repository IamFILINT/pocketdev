# PocketDev — CI/CD

> Maintained by the coding agent. See master prompt §5.

## Principle

**All compilation, testing, static analysis, emulator execution, and release builds run
on GitHub-hosted runners. The Android phone is the control surface only.** No local
build/test fallback is permitted.

## Workflows

### `verify.yml` — required pull-request gate (Milestone 0)

**Status:** scaffolded; currently proves GitHub Actions executes on this repo.

Triggers:
- `pull_request` to `main`
- `push` to `main`
- `workflow_dispatch`

Jobs (current):
- `bootstrap` — a trivial job that confirms the runner works and reports environment.

Planned jobs (added as the project grows):
1. Repository/config validation
2. Formatting checks
3. Android lint
4. Unit tests
5. Kotlin/static analysis (when Kotlin is introduced)
6. Dependency/license inventory validation
7. Debug APK compilation
8. Upload reports + debug APK as artifacts

### Planned (later milestones)

- `android-emulator-tests.yml` — instrumentation/UI tests on x86_64 emulator via
  `ReactiveCircus/android-emulator-runner` (pinned to an immutable SHA).
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
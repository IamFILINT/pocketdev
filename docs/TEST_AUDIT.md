# PocketDev — Testing Audit (Phase A)

> Per the mandatory testing policy, Phase A: audit the entire existing repository and all completed milestones before new feature work. This audit inspects implementation, not documentation.

## Scope

M0 (CI foundation), M1 (Android foundation), M2 (runtime architecture/feasibility), M3 (runtime bootstrap), ADR-0002 (prefix/exec), runtime execution spike, M4 (OpenCode provisioning + supervision), all Kotlin components, all Android components, all workflows, security/filesystem protections.

## Method

For each component: responsibilities, expected behavior, dependencies, failure conditions, existing tests, missing coverage, plan. Cross-referenced against `docs/TEST_COVERAGE.md` (actual files only; no invented tests).

## Findings

### Well covered (Verified in CI)

- `:core` bootstrap: pins, artifact, checksums, archive (Zip Slip), journal, manager, health, downloader, paths, state machine — all unit-tested.
- `:core` agent: pins, installer (checksum/resume/fail-closed), serve-output parser, API client (auth, body, path escaping, real loopback server).
- `:core` workspace/validation/security: PathSafety, ProjectStore, ProjectNameValidator, SecretRedaction.
- `:core` ai/state: TaskStateMachine, RuntimeStateMachine.
- `:core` json: Json reader.
- `:runtime`: ABI selection, env builder, launcher, preload deployer, server supervision state machine.
- `:app`: Navigation instrumentation, runtime spike (manual, x86_64).

### Gaps (Pending)

1. Android-bound orchestration (`OpenCodeController`, `RuntimeController`) has no direct tests. Covered indirectly by `:core`/`:runtime` unit tests and manual device runs. Needs Robolectric or instrumented tests to close; not configured today.
2. `RuntimeExecutionHealthCheck` requires a real linker64 + prefix; only runs on a device/emulator with a provisioned prefix. Structural part (`BootstrapHealth`) is unit-tested.
3. App ViewModels (`DashboardViewModel`, `ProjectsViewModel`, `SettingsViewModel`) and `AppContainer` are `AndroidViewModel`/Context-bound; no unit tests. Logic is thin (delegates to ProjectStore, ProjectNameValidator, PathSafety which are tested), but provisioning guards and create/delete flows are not directly verified.
4. Compose screens have no instrumentation tests beyond navigation.
5. Native payload identity (OpenCode aarch64 ELF, bootstrap zip) is not validated in CI. Added `native-runtime-ci.yml` (pending first green run).
6. ARM64 device execution remains unverifiable in GitHub Actions (risk R1).

### Defects found during audit

None new. Existing tests pass on `main` (verify 45002125992, android-emulator 45002125996, native-runtime pending).

### Actions taken (this audit)

- Added `docs/TEST_COVERAGE.md` (coverage matrix, honest statuses).
- Added `.github/workflows/native-runtime-ci.yml` (static aarch64 payload verification).
- Updated `docs/TESTING.md`, `docs/CI_CD.md`, `docs/IMPLEMENTATION_ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/DEPENDENCY_INVENTORY.md`, `README.md`.

## Phase B plan (historical test completion)

Priority order, each as its own commit + CI run:

1. Add instrumented tests for `ProjectsViewModel` create/delete/validation against a temp projects root (Robolectric not configured; use the x86_64 emulator job). Highest value because create/delete touches filesystem + registry.
2. Add `OpenCodeController` provisioning/supervision smoke test as an instrumentation test (device-bound), or extract a testable coordinator. Document limitation if not feasible.
3. Add Compose screen smoke tests (Dashboard/Projects/Settings) to the emulator job.
4. Add `native-runtime-ci.yml` green run as evidence.

Android-bound items that cannot be unit-tested on a plain JVM will be recorded as `Pending` in `TEST_COVERAGE.md` with the reason, per policy section 11, rather than faked.

## Phase C (CI enforcement)

- `verify.yml` already runs lint + unit tests + assembleDebug on every PR/push and uploads artifacts.
- `android-emulator.yml` runs instrumentation.
- `native-runtime-ci.yml` added (pending first run).
- Concurrency + least-privilege already in place.
- Remaining: ensure `native-runtime-ci.yml` is required in branch protection (manual GitHub setting, out of repo).

## Phase D (defect resolution)

No defects identified in this audit pass. Re-run after each new test lands.

## Phase E (future development)

M5 (SSE events, sessions, provider/model, permissions) proceeds only after the pending items above are tracked; new features follow testing-first, commit-by-commit verification.
EOF
echo done
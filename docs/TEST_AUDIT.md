# PocketDev — Testing Audit (Phase A)

> Per the mandatory testing policy, Phase A: audit the entire existing repository and all completed milestones before new feature work. This audit inspects implementation, not documentation.

## Scope

M0 (CI foundation), M1 (Android foundation), M2 (runtime architecture/feasibility), M3 (runtime bootstrap), ADR-0002 (prefix/exec), runtime execution spike, M4 (OpenCode provisioning + supervision), all Kotlin components, all Android components, all workflows, security/filesystem protections.

## Method

For each component: responsibilities, expected behavior, dependencies, failure conditions, existing tests, missing coverage, plan. Cross-referenced against `docs/TEST_COVERAGE.md` (actual files only; no invented tests).

## Findings

### Well covered (Verified in CI)

- `:core` bootstrap: pins, artifact, checksums, archive (Zip Slip), journal, manager, health, downloader, paths, state machine — all unit-tested.
- `:core` agent: pins, installer (checksum/resume/fail-closed), serve-output parser, API client (auth, body, path escaping, real loopback server), SSE event parser (`ServerSentEvent`/`ServerSentEvents`).
- `:core` workspace/validation/security: PathSafety, ProjectStore, ProjectNameValidator, SecretRedaction.
- `:core` ai/state: TaskStateMachine, RuntimeStateMachine, TaskStateMapper (SSE -> TaskState).
- `:core` json: Json reader.
- `:runtime`: ABI selection, env builder, launcher, preload deployer, server supervision state machine.
- `:app`: Navigation instrumentation, runtime spike (manual, x86_64), ProjectsViewModel/DashboardViewModel/SettingsViewModel/AgentSessionViewModel instrumented tests.

### Gaps (Pending)

1. Android-bound orchestration (`OpenCodeController`, `RuntimeController`) has no direct tests. Covered indirectly by `:core`/`:runtime` unit tests and manual device runs. Needs Robolectric or instrumented tests to close; not configured today.
2. `RuntimeExecutionHealthCheck` requires a real linker64 + prefix; only runs on a device/emulator with a provisioned prefix. Structural part (`BootstrapHealth`) is unit-tested.
3. `AppContainer` is Context-bound; no unit tests (object graph is trivial wiring).
4. Compose screens have no instrumentation tests beyond navigation.
5. ARM64 device execution remains unverifiable in GitHub Actions (risk R1).

### Closed since original audit

- App ViewModels: `ProjectsViewModel` (PR #19), `DashboardViewModel` and `SettingsViewModel` (PR #22) now have instrumented tests.
- Native payload identity: `native-runtime-ci.yml` is green (run 45006056315).
- M5 SSE: `ServerSentEvent` parser and `TaskStateMapper` unit-tested (PRs #24, #26); `AgentSessionViewModel` instrumented (PR #28).
- M5 SSE consumption: `OpenCodeSessionApi` seam + `AgentSessionViewModel.start/stop` streaming, failure and stop coverage on the emulator (PR #32).

### Defects found during audit

None new. Existing tests pass on `main` (verify, android-emulator, native-runtime-ci all green).

### Actions taken (this audit)

- Added `docs/TEST_COVERAGE.md` (coverage matrix, honest statuses).
- Added `.github/workflows/native-runtime-ci.yml` (static aarch64 payload verification) — green.
- Updated `docs/TESTING.md`, `docs/CI_CD.md`, `docs/IMPLEMENTATION_ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/DEPENDENCY_INVENTORY.md`, `README.md`.
- Added instrumented tests for `ProjectsViewModel`, `DashboardViewModel`, `SettingsViewModel`, `AgentSessionViewModel`.
- Added unit tests for `TaskStateMapper`, `ServerSentEvent` parser, `RuntimeLauncher`, `RuntimeController.initialStateFrom`.
- CI triggers updated to run on every branch push (`ci/enforce-triggers-and-docs-consistency`).

## Phase B plan (historical test completion)

Priority order, each as its own commit + CI run:

1. Add instrumented tests for `ProjectsViewModel` create/delete/validation against a temp projects root (Robolectric not configured; use the x86_64 emulator job). Highest value because create/delete touches filesystem + registry.
2. Add `OpenCodeController` provisioning/supervision smoke test as an instrumentation test (device-bound), or extract a testable coordinator. Document limitation if not feasible.
3. Add Compose screen smoke tests (Dashboard/Projects/Settings) to the emulator job.
4. Add `native-runtime-ci.yml` green run as evidence.

Android-bound items that cannot be unit-tested on a plain JVM will be recorded as `Pending` in `TEST_COVERAGE.md` with the reason, per policy section 11, rather than faked.

## Phase C (CI enforcement)

- `verify.yml` runs lint + unit tests + assembleDebug on every PR/push (now `push: branches: ['**']` after CI enforcement task) and uploads artifacts.
- `android-emulator.yml` runs instrumentation.
- `native-runtime-ci.yml` green (run 45006056315).
- Concurrency + least-privilege in place; groups are per-ref so unrelated branches do not cancel each other.
- Remaining: ensure `native-runtime-ci.yml` is required in branch protection (manual GitHub setting, out of repo).

## Phase D (defect resolution)

No defects identified in this audit pass. Re-run after each new test lands.

## Phase E (future development)

M5 (SSE events, sessions, provider/model, permissions) proceeds only after the pending items above are tracked; new features follow testing-first, commit-by-commit verification.
EOF
echo done
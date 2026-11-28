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
4. Compose screens have instrumentation coverage only for navigation/Files/Terminal smoke.
5. ARM64 device execution remains unverifiable in GitHub Actions (risk R1).

### Closed since original audit

- App ViewModels: `ProjectsViewModel` (PR #19), `DashboardViewModel` and `SettingsViewModel` (PR #22) now have instrumented tests.
- Native payload identity: `native-runtime-ci.yml` is green (run 45006056315).
- M5 SSE: `ServerSentEvent` parser and `TaskStateMapper` unit-tested (PRs #24, #26); `AgentSessionViewModel` instrumented (PR #28).
- M5 SSE consumption: `OpenCodeSessionApi` seam + `AgentSessionViewModel.start/stop` streaming, failure and stop coverage on the emulator (PR #32).
- M5 workspace: `AgentRuntime`/`OpenCodeSessionApi` seams + Agent workspace ViewModel (install, attach on listening, detach on stop) and Agent tab navigation (PR #33).
- M5 prompt: session creation/selection/prompt dispatch with fake API, blank-prompt rejection, and prompt composer UI (PR #34).
- M5 settings: non-secret provider/model preferences validated in `:core`, persisted in `AppContainer`, surfaced in Settings with reject-invalid tests (PR #35). Secret API keys remain in memory by design.
- M5 permissions: `permission.asked` parsed into a pending request, `once`/`always`/`reject` reply posted to the upstream session permission endpoint, state/action exposed through both Agent ViewModels, permission card rendered in the Agent tab, and unit/instrumented coverage added (PR #36).
- M6 explorer: `WorkspaceFiles` keeps all create/rename/copy/move/delete/search/preview operations inside the selected project root, skips symlinks, rejects traversal, and is covered by JVM unit tests plus instrumented `FilesViewModel`/navigation tests (PR #37).
- M6 terminal: JNI `openpty`/`fork`/`execve` PTY sessions, ANSI buffer, resize, scrollback, copy/paste, signals, multi-session support, and selected-project cwd are covered by JVM ANSI unit tests plus instrumented `TerminalViewModel`/navigation tests (PR #38).

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
- Concurrency + least-privilege in place; groups are SHA-scoped so a newer commit never cancels verification of an earlier SHA.
- Remaining: enable branch protection on `main` manually with the three required checks listed in `docs/CI_CD.md` (manual GitHub setting, out of repo).

## Phase D (defect resolution)

No defects identified in this audit pass. Re-run after each new test lands.

## Phase E (future development)

M5 (SSE events, sessions, provider/model, permissions) proceeds only after the pending items above are tracked; new features follow testing-first, commit-by-commit verification.

## Milestone 7.5 reconciliation

Required CI executes the critical editor regressions:

- `verify.yml` command is `./gradlew --no-daemon --stacktrace lintDebug testDebugUnitTest :core:test assembleDebug`; it runs codec, buffer/revision, signature, workspace-write, path-safety, search, syntax, formatter, draft-domain, and performance-baseline tests.
- `android-emulator.yml` runs `connectedDebugAndroidTest` on x86_64 API 29 and 36; it runs `EditorViewModelTest`, `EditorUiTest`, `EditorInputUiTest`, and project-switch isolation tests, excluding only the opt-in PTY classes.
- `native-runtime-ci.yml` remains a static aarch64 payload check; editor-only changes are not represented as native runtime execution evidence.

Defects reproduced by failing regressions before fixes, then closed with CI evidence:

- D1/D2 stale revision save and unstable tab identity: stable `EditorDocumentId`, exact `markSaved(revision, text)`, late-save and project-switch regressions.
- D3/D4 unsafe commit and external-change conflict: staged atomic move with fallback/cleanup, content-digest signature, same-size replacement detection, conflict/reload regressions.
- D5/D6 symlink escape and weak decoding: `resolveWithinNoFollow` component checks and strict UTF-8 decode with mixed-newline round-trip fidelity.
- D7/D8 IME/Unicode/selection and language-aware formatting: Compose composition/Unicode tests and formatter regressions for syntax-significant whitespace.
- D9/D10 draft recovery and unreadable/unknown drafts: app-private versioned blobs, raw-byte retention, hash+size auto-restore gate, explicit restore/keep/discard.
- D11/D12 baseline evidence and documentation drift: deterministic JVM/Android benchmark records in `docs/PERFORMANCE_BASELINE.md` and this reconciliation.

No editor assertion was deleted, skipped, or loosened. The remaining open items are the pre-existing runtime ARM64 gap (R1), provider-specific atomicity (R23), and performance monitoring without thresholds (R25). Latest passing evidence for this reconciliation is recorded in `docs/IMPLEMENTATION_ROADMAP.md`.
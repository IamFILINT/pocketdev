# PocketDev — Session Notes

> Working notes for the PocketDev build. Maintained by the coding agent.

## Session 1 — 2026-11-20

### Environment confirmed
- **Device:** Android, `arm64-v8a`, API 30 (Android 11)
- **Termux home:** `/data/data/com.termux/files/home`
- **Project root (on FUSE storage):** `/storage/emulated/0/Projects/pocketdev`
- **Git:** v2.56.1, works on FUSE with `safe.directory` exception (already set)
- **gh CLI:** v2.105.0, authenticated as `IamFILINT`, scopes: `gist, read:org, repo, workflow`
- **Disk:** ~4.5 GB free on `/storage/emulated/0` (91% used — tight)

### Decisions made by product owner
- No pre-existing PocketDev repo; **start fresh**.
- Parent dir: `Projects`, project dir: `pocketdev`.
- GitHub repo: **`IamFILINT/pocketdev` — PUBLIC** (user's explicit choice).
- Remote wired: `origin` → https://github.com/IamFILINT/pocketdev.git

### Open items
- [ ] Milestone 0: baseline CI (`verify.yml`) that runs on GitHub-hosted runners only.
- [ ] Confirm branch protection / default branch (currently `master` locally).
- [ ] No local Gradle/SDK/emulator builds — phone is control surface only.

### Operating rule (from master prompt)
- **All builds/tests/analysis/package ops run in GitHub Actions. Never on the phone.**
## Session 2 — 2026-11-20

### Milestone 1 completed (PR #1 merged, merge commit `5105004`)
- Root causes of red CI, fixed in sequence:
  1. AndroidX 2026.11 requires AGP ≥9.1.0 + compileSdk 37 → AGP 9.1.1,
     Gradle 9.3.1 (SHA-256 pinned), compileSdk 37.
  2. AGP 9 DSL/Kotlin migration avoided via `android.newDsl=false` and
     `android.builtInKotlin=false` in `gradle.properties`.
  3. API 37 platform is `platforms;android-37.0` (minor-versioned); there is no
     bare `platforms;android-37`.
  4. Deleted unused duplicate UI shell `dev/pocketdev/app/*` (used icons absent
     from `material-icons-core`).
  5. Emulator test exposed a real crash: manifest lacked
     `android:name=".PocketDevApplication"` → added.
- New CI gate: `.github/workflows/android-emulator.yml` (API 30 x86_64, KVM,
  `connectedDebugAndroidTest`, 3 Compose navigation tests).
- Evidence: `verify` 44673636850, `android-emulator` 44673636885 (3/3 passed).
- Wrapper jar was NOT regenerated (only `distributionUrl`/SHA changed) — the
  bootstrap jar is version-agnostic; wrapper-validation still passes.

### Next
- Milestone 2: runtime architecture audit, integration ADR, license inventory,
  ARM64 emulator feasibility spike.

## Session 3 — 2026-11-21

### Milestone 2 completed
- PR #2 merged (`e1ce1e7`): Termux audit, ADR-0001, bootstrap/storage layout,
  `BootstrapManifest`/`BootstrapArtifact` model + unit tests, arm64-spike workflow.
- PR #3 (`2cc922d`) and PR #4 (`27b6f3a`): spike fixes after real evidence —
  1. `yes | sdkmanager` exits 141 (SIGPIPE) under `pipefail` → installs were
     false-negatives; replaced with a one-shot `--licenses` and plain installs.
  2. Missing `libpulse0` broke the preinstalled emulator binary; KVM needed the
     same udev snippet as `android-emulator.yml`.
  3. `avdmanager` and the emulator resolve different default AVD homes; pin
     `ANDROID_AVD_HOME` and locate `spike.ini` defensively.
- ARM64 feasibility measured (runs `44762039967`, `44768697070`, `44862773422`):
  - `ubuntu-latest` (x86_64): image installs, KVM usable, but emulator 37.3.10
    refuses ARM64 guests (`System image must match the host architecture`).
  - `ubuntu-24.04-arm`: no `emulator` package for `linux-aarch64`, no `/dev/kvm`.
  - Verdict + consequences in `docs/ARM64_FEASIBILITY.md`; risk R1 updated.
- Follow-up (PR #6, run `44866937289`): the native `ubuntu-24.04-arm` runner
  cannot even build the APK — `aapt2-…-linux` is an x86_64 binary and fails with
  `Syntax error: ")" unexpected`. Building, like emulation, requires `x86_64`.
- Fixed a spike-workflow bug where GitHub's `bash -e` aborted the verdict step
  when `arm64-build.txt` was absent (x86_64 leg).
- Functional CI remains `x86_64` API 30 (M1 gate green).

### Next
- Milestone 3: pinned bootstrap metadata + checksum verification, safe extraction,
  resumable init + health checks, unit tests; then ADR-0002 prefix spike.

## Session 4 — 2026-11-21

### Milestone 3 completed (PR #8)
- Real pin measured, not guessed: `bootstrap-2026.11.15-r1+apt.android-7`,
  `bootstrap-aarch64.zip`, 33,148,806 bytes, SHA-256
  `388acf91cc247e7062d80cd8aa791fe72f360515fb78dc54ff45742120facae7`
  (the release publishes no checksum file, so the digest was computed from the
  downloaded asset). `x86_64` stays unpinned until ADR-0002.
- `BootstrapArchive` mirrors upstream `TermuxInstaller` semantics: prefix-root
  archive + `SYMLINKS.txt` lines (`target←./path`), exec bits on `bin`, `libexec`,
  `lib/apt/{methods,apt-helper}`; rejects traversal, absolute paths, NUL,
  backslashes, duplicate entries, oversized/too-many entries, and symlinks whose
  link or target escapes the prefix.
- `BootstrapManager`: resumable `.part` download, verify-before-extract,
  `usr.staging` → swap with `usr.prev` rollback, journaled stages in
  `files/state/runtime.json`; an interrupted run resumes at the byte offset, a
  healthy runtime is never overwritten, a bad checksum deletes the artifact.
- Layout invariant enforced in code: `RuntimePaths` keeps projects/state outside
  `$PREFIX`; `AppContainer` now uses `files/home/projects`.
- 45 new JVM tests (checksums, archive safety, health, journal, pins, paths,
  manager incl. resume/mismatch/rollback/idempotence) — `verify` green on the
  first push.
- Honest scope: health checks are **structural**; execution remains unproven in
  CI (no ARM64 Android environment), so the milestone's "real Bionic execution"
  clause is documented as open.

### Next
- ADR-0002: prefix-relocation + W^X exec spike on the x86_64 API 30 emulator
  (input for whether a runtime can boot there for functional tests).

## Session 5 — 2026-11-21

### ADR-0002 spike (risks R11/R12)
- Pinned the x86_64 bootstrap (33,058,188 B, SHA-256
  `fb95765f2ffb4cf5648891199dedb820ed19a63334352fb0bc69b7b7c917e002`) for the
  emulator only; aarch64 remains the shipped pin.
- Spike runs and fixes:
  - `44875058681`: emulator booted, but the workflow's `\` line continuations
    were passed literally to `sh` (`Task '\' not found`).
  - `44875631930`: extraction failed on 20 absolute `SYMLINKS.txt` targets
    (`/data/data/com.termux/files/usr/...`); fixed by rewriting them to relative
    links inside the extraction root (works in staging and after the swap).
  - `44876316131`: **success.** Provisioned `Ready` on the emulator, then:
    direct exec **blocked** (W^X); `linker64 + bash` and `linker64 + ls` exit 0;
    shebang exec fails (126, `bad interpreter`) with and without stock
    `termux-exec` 2.5.0 — its binary hardcodes the Termux prefix.
- `docs/adr/0002-prefix-and-exec-strategy.md`: keep our prefix, launch through
  `linker64`, ship a **prefix-built** `termux-exec` (Apache-2.0, pinned commit
  `2cd0ba6b…`, built with `TERMUX_APP__PACKAGE_NAME=dev.pocketdev`); no binary
  rewriting; package manager stays disabled.
- `RuntimeSpikeTest` is opt-in (`runtimeSpike=true`), so the normal emulator gate
  stays fast; report is now written to `filesDir` and pulled with `run-as`.

### Next
- `:runtime` Android wiring (downloader, env builder, linker-hop launcher,
  preload deployment of the stock `termux-exec-ld-preload.so` from the pinned
  bootstrap; env `TERMUX_APP__DATA_DIR`/`TERMUX__PREFIX` override).

## Session 6 — 2026-11-22

### Android runtime wiring (`:runtime`)
- New Android library module `:runtime` (depends on `:core`; app depends on it),
  with Android-only classes around the pure bootstrap manager:
  - `RuntimeEnvBuilder` — env from `docs/BOOTSTRAP_LAYOUT.md` §8.5, including
    `TERMUX_APP__DATA_DIR` / `TERMUX_APP__LEGACY_DATA_DIR` / `TERMUX__PREFIX`
    overrides and a child-only `LD_PRELOAD`.
  - `RuntimeLauncher` — every launch is `/system/bin/linker64 <program> <args>`
    (W^X first hop), with the runtime env injected.
  - `RuntimePreloadDeployer` — tracks the stock
    `libtermux-exec-ld-preload.so` shipped by the pinned bootstrap.
  - `RuntimeExecutionHealthCheck` — structural checks from `:core` plus real
    execution checks (`bash -lc 'echo pd-runtime-ok'` and `ls -1 bin`) through
    the linker; used by `BootstrapManager` so a broken prefix fails provisioning.
  - `RuntimeController` — wires the manager, `HttpBootstrapDownloader`, free-space
    probe, env builder and launcher into one Android entry point.
- `AppContainer` exposes `runtime`; `verify.yml` artefact paths now include
  `:runtime` reports. Unit tests cover the env layout/overrides and preload
  presence (`TemporaryFolder`, no emulator needed).
- Dashboard wiring: `RuntimeController` exposes a `StateFlow<RuntimeState>`
  seeded from the journal, detects the device ABI (`abiFor`), and
  `DashboardViewModel` runs provisioning off the main thread; the UI shows
  install/retry plus determinate/indeterminate progress and never invents state.
  Unit tests cover the ABI allow-list.
- Next: the OpenCode Bionic controller for Milestone 4.

## Session 7 — 2026-11-22

### OpenCode Bionic pin and installer
- Audited upstream `bd-loser/opencode-bionic` `v2.3.2` (published 2026-11-21,
  MIT): self-contained aarch64 binary 211,486,984 B,
  SHA-256 `ea93b4c4…`; upstream OpenCode 2.3.2; `bun-termux` 1.4.5-patched;
  minimum API 29. x86_64 is not published, so the agent cannot run on the CI
  emulator (ARM64 gap).
- `:core` gains `OpenCodePins`/`AgentArtifact` (validated metadata) and
  `AgentInstaller` (resumable download, size + SHA-256 verification, atomic
  replace, chmod, fail-closed). Unit tests cover success, checksum mismatch and
  resume offsets.
- `:runtime` gains `HttpAgentDownloader` (reuses the HTTPS/range downloader) and
  `OpenCodeController`: seeds state from the installed binary, refuses non-ARM64
  or pre-API-29 devices, and starts the agent through `/system/bin/linker64`
  with the project directory as cwd. ADR-0003 records fetch-vs-embed and pinning
  decisions; layout/inventory/roadmap updated.
- Dashboard: the AI-agent card now shows the real agent state with an
  install/retry action and determinate/indeterminate progress.
- Next: health/API supervision and OpenCode server (`opencode serve`)
  integration, plus a real-device ARM64 run.

## Session 8 — 2026-11-22

### M4 health/API supervision (`opencode serve`)
- Researched the pinned upstream version instead of guessing: `anomalyco/opencode`
  tag `v2.3.2` `server-process.ts` prints exactly
  `server listening on <url>` then `server password <base64url>` in foreground
  mode; auth is HTTP Basic with fixed username `opencode`
  (`packages/server/src/auth.ts`). Verified against the release, not `main`.
- `:core`:
  - `OpenCodeServeOutputParser` — incremental, tolerant of log/ANSI noise,
    loopback-only (fail closed on non-loopback URLs), completes once.
  - `Json` — dependency-free JSON reader (no serialization plugin) reused for
    client responses and later SSE events.
  - `OpenCodeApiClient` — `HttpURLConnection` + Basic auth; `/api/info`,
    `GET /api/session`, `POST /api/session`, `POST /api/session/{id}/prompt`.
- `:runtime`: `ProcessLauncher`/`RuntimeProcessLauncher` and `OpenCodeServer`
  (real `Starting`/`Listening`/`Exited`/`Failed` state machine; reader thread
  drains output so the child cannot block; waiter records exit). Launched as
  `serve --hostname 127.0.0.1` through the linker hop.
- `OpenCodeController` exposes `serverState`, `startServer`, `stopServer`,
  `connection()`, `newApiClient()`; refuses non-ARM64/pre-API-29 devices.
- ADR-0004 records foreground-vs-stdio/service, loopback binding, and the auth
  model. Roadmap M4 updated; event plumbing deferred to M5.
- Tests: parser (documented/ANSI/reordered/incomplete/non-loopback), JSON reader,
  client (fake transport header/body assertions plus a real loopback
  `com.sun.net.httpserver.HttpServer`), and the supervisor against `/bin/sh`
  script processes.
- Honest scope: ARM64 Bionic execution still cannot run in Actions (R1); CI covers
  the state machine, auth client and loopback server. Real-device proof remains
  open.
- Next: Milestone 5 AI workspace — SSE event stream into UI state, sessions,
  provider/model settings, permission prompts.


## Session 9 — 2026-11-22

### Merge + Termux CI research + docs refresh
- Merged PR #16 (merge commit `35e6716`); `verify` `45000833539` and
  `android-emulator` `45000833447` both green on `main`.
- Researched Termux's own CI to see how they build and test on GitHub-hosted
  runners:
  - `termux/termux-app`: `run_tests.yml` runs `./gradlew test` on
    `ubuntu-latest` only (no emulator tests at all); `debug_build.yml` builds
    matrixed package variants, names APKs with a `versionName+shortsha` tag,
    validates every expected ABI APK, generates `sha256sums`, and uploads per-ABI
    artifacts; `gradle-wrapper-validation.yml` is its own workflow.
  - `termux/termux-packages`: cross-builds aarch64/arm/i686/x86_64 packages in
    Docker on `ubuntu`, with `fail-fast: false`, PR-only concurrency cancellation,
    artifact reuse between PR jobs, `permissions: {}` default with job-level
    grants, and a scheduled `bootstrap_archives.yml` that builds/publishes
    per-arch zips to a GitHub Release.
  - Takeaway adopted for PocketDev: static validation of native payloads on x86_64
    (don't pretend an emulator proves ARM64), fast separate JVM tests, sha256sums
    discipline, least-privilege permissions, PR-only cancellation. Differences:
    PocketDev keeps one combined `verify.yml` gate (one green check is meaningful),
    ships one universal APK (not per-ABI), and never auto-upgrades pinned runtime
    deps — every pin bump is a reviewed PR with passing CI.
- Docs refresh to reflect M4 landed: README (status, progress, "what works today",
  doc links), ARCHITECTURE (module map, agent data flow, decisions, security),
  DEPENDENCY_INVENTORY (in-house components, no new third-party deps),
  CI_CD (M4 evidence + Termux-inspired practices), IMPLEMENTATION_ROADMAP (exit
  evidence), new `docs/OPENCODE_BIONIC.md` (integration contract) and
  `docs/TESTING.md` (layers, honest gaps, inventory).
- Next: Milestone 5 — SSE event stream from `GET /api/event` into UI state,
  sessions, provider/model settings, permission prompts; plus a physical ARM64
  device run (accepted CI gap) and the planned `native-runtime-ci.yml`.

## Session 10 — 2026-11-23

### M5 slice 1 — real SSE consumption in AgentSessionViewModel
- Added `:core` `OpenCodeSessionApi` (list/create/prompt/streamEvents); made
  `OpenCodeApiClient` implement it so UI code depends on a fakeable seam.
- `AgentSessionViewModel` now opens the stream via `start(api, sessionId)` on
  `Dispatchers.IO` (`runInterruptible`), maps every delivered event through
  `TaskStateMapper`, exposes `streaming`/`error`, and stops with `stop()`.
  Only real events may change `taskState`; failures leave the last state and set
  `error` instead of inventing a state.
- Instrumented tests: terminal stream -> `COMPLETED`, failure -> `error` with
  state unchanged, blocking stream + `stop()` -> not streaming, no error.
- Next: Agent tab (server/session/prompt UI) consuming this VM; then sessions,
  provider/model settings and permission prompts.

## Session 11 — 2026-11-23

### M5 slice 2 — Agent workspace
- `:runtime` `AgentRuntime` seam; `OpenCodeController` implements it (`install`,
  `startServer(dir)`, `stopServer`, `newApiClient`).
- `AgentWorkspaceViewModel` (`@JvmOverloads` for `viewModel()`): installs the
  agent, starts/stops the server, and attaches the SSE stream only once
  `OpenCodeServerState.Listening` arrives; detaches and clears sessions when the
  server stops. Task state still comes exclusively from real events.
- Agent tab (4th destination): agent status/install, server start/stop with URL,
  task state + live/error indicators, session list. Strings added.
- Instrumented tests: attach -> sessions + `COMPLETED`, stop -> detach, install
  delegation; navigation test for the Agent tab.
- Next: prompt composer + new-session action, provider/model settings, permission
  prompts, then ARM64 device verification (R1).

## Session 12 — 2026-11-23

### M5 slice 3 — prompt composer and sessions
- `AgentWorkspaceViewModel`: active session state, `createSession`, `selectSession`,
  `sendPrompt`, `sending` guard; prompts are sent through the session API only
  while the server is listening, and blank prompts are rejected locally.
- Agent tab: session selector, "New session", multiline prompt field and "Send
  prompt" button; task state remains event-driven.
- Instrumented tests: create-on-first-prompt, explicit new session, blank prompt
  rejection; coverage/audit notes updated.
- Next: provider/model settings, permission prompts, then ARM64 device run (R1).

## Session 13 — 2026-11-23

### M5 slice 4 — provider/model preferences
- `:core` `AgentSettings` (non-secret provider/model, trimming and validation);
  `AppContainer` persists them in SharedPreferences; `SettingsViewModel` exposes
  validated setters with an error StateFlow.
- Settings screen: Provider/Model text fields with "server default" placeholders
  and an explicit note that applying them to a running server is not wired yet.
  API key remains in-memory only (Keystore store still pending).
- Tests: core validation unit tests, instrumented persistence/invalid-input tests,
  Settings navigation assertions.
- Next: permission prompts; ARM64 device run remains R1.

## Session 14 — 2026-11-23

### M5 slice 5 — permission prompts
- Researched the pinned OpenCode v2.3.2 OpenAPI schema: `permission.asked` carries
  `id`, `sessionID`, `action`, `resources` and optional `message` inside `data`;
  `POST /api/session/{sessionID}/permission/{requestID}/reply` accepts
  `{"decision":"once"|"always"|"reject"}` and returns 204.
- `:core` gains `PermissionRequest` (event projection), `PermissionDecision`
  (upstream reply values), `ServerSentEvent.type` (SSE name with JSON payload
  fallback for generic/missing `event:`), and `OpenCodeSessionApi.replyPermission`
  implemented by `OpenCodeApiClient`.
- `AgentSessionViewModel` exposes the pending request, replies through the active
  session API, and clears it on `permission.replied`; `AgentWorkspaceViewModel`
  forwards the action. The Agent tab renders action/resources/message with Allow
  once / Always allow / Reject buttons.
- Tests: permission parsing/type fallback/reply-body unit tests and instrumented
  end-to-end event-to-reply coverage through both ViewModels.
- First CI run exposed missing `type` extension imports in `TaskStateMapper` and
  `AgentSessionViewModel`; fixed in a follow-up commit. PR #36 is green:
  verify `45237366899`/`45237378788`, android-emulator `45237367091`/`45237378747`.
- Next: ARM64 device run remains R1.

## Session 15 — 2026-11-23

### M6 slice 1 — file explorer and shared workspace
- PR #36 merged into `main` at `904196f`; local `main` fast-forwarded and branch deleted.
- Added `:core` `WorkspaceFiles`/`FileEntry` with safe listing, create, rename, copy,
  move, delete, search, hidden-file handling, text preview, and symlink escape
  rejection. Pure-JVM unit tests cover all operations.
- `AppContainer` now owns `selectedProject` and `activeProjectRoot()`; projects and
  Files views share it, and the Agent server now starts in the selected project root.
- Added Files tab with its own ViewModel, project selector, breadcrumb, hidden toggle,
  search, create/rename/copy/move/delete dialogs, text preview, and SAF import/export.
- CI green on PR #37 at `4e6d093` and final head `5104fd1`: verify
  `45248176459`/`45248191102`, `android-emulator` `45248176792`/`45248190987`.
- PR #37 merged into `main` at `81dfd49` after local fast-forward; branch deleted.
  Next: PTY terminal for M6; ARM64 device run remains R1.

## Session 16 — 2026-11-23

### M6 slice 2 — real PTY terminal
- Added native JNI PTY host (`openpty`, `fork`, `execve`, `TIOCSWINSZ`, signals,
  reap) for `arm64-v8a` and `x86_64`, with Kotlin session lifecycle and shared
  project cwd.
- Added pure-Kotlin ANSI buffer (cursor, SGR, erase, scrollback, resize, OSC, plain
  text) with JVM unit tests.
- Added Terminal tab with multi-session tabs, scrollback, keyboard input, copy/paste,
  resize handling, and instrumentation coverage for real x86_64 PTY sessions/navigation.
- First PR #38 CI run failed on ANSI buffer API/type errors; second failed on Compose
  BOM clipboard/`SpanStyle` API changes. Fixed in follow-up commits; final code CI is
  green: verify `45254925593`/`45254937737`, android-emulator `45254925748`/`45254937817`.
- PR #38 merged into `main` at `27dbcc7`; final docs verification green on verify
  `45257636050`/`45257644825` and android-emulator `45257635719`/`45257644889`.
- Next: Milestone 7 code editor. Physical ARM64 device run remains R1.

## Session 17 — 2026-11-24

### M6.5 Phase 0 — runtime integration reconnaissance
- Started Milestone 6.5 from clean `main` at `a1cfff7` with PR #38 merged at
  `27dbcc7`. Baseline M6 green evidence: verify `45259268881`,
  android-emulator `45259268072`. No local build or device verification.
- Confirmed prompt source is external
  `/storage/emulated/0/Download/POCKETDEV_MILESTONE_6_5_PROMPT.md`; no literal
  M6.5 phase text exists in the repository.
- Verified current manifest has `INTERNET` but no cleartext opt-in; with
  `minSdk 29`/`targetSdk 36`, loopback HTTP needs a narrow domain config.
- Verified PTY creation still uses `/system/bin/sh` directly and does not consume
  `RuntimeLauncher`/`RuntimeEnvBuilder`; runtime execution on a real ARM64 device
  remains an accepted R1 gap, not a passing claim.
- Verified branch protection is absent (`main/protection` 404, rulesets `[]`),
  `verify.yml`/`android-emulator.yml` use mutable `cancel-in-progress: true`,
  and `native-runtime-ci.yml` still uses mutable `actions/upload-artifact@v4`.
- Verified docs still describe native-runtime-ci as planned in `docs/CI_CD.md`
  and `docs/TESTING.md`; they need reconciliation after workflow stabilization.
- Implementation order: CI stabilization, loopback HTTP, PTY/runtime integration,
  PTY reliability, project context, filesystem I/O, provider/model verification,
  then final docs. No M6.5 implementation changes are included in this commit.

### M6.5 Phase 7 — CI enforcement hardening
- Switched all five workflows to SHA-scoped concurrency groups with
  `cancel-in-progress: false`, so a newer push cannot cancel an earlier SHA's
  verification.
- Removed path filters from `native-runtime-ci.yml`, so the static payload gate runs
  on every PR/push and can be a required status check; pinned the remaining mutable
  `actions/upload-artifact@v4` reference to `ea165f8d65b6e75b540449e92b4886f43607fa02`.
- Documented the three required status checks and the manual branch-protection step
  in `docs/CI_CD.md`; reconciled `docs/TESTING.md` and `docs/TEST_AUDIT.md`.
- Branch protection itself remains absent; repository files cannot set it.

### M6.5 Phase 1 — loopback HTTP on Android
- Added a narrow Network Security Config permitting cleartext only for
  `127.0.0.1`, `localhost`, and `::1`; production traffic remains TLS-only.
- Added `LoopbackHttpTest` instrumentation coverage that drives the real
  `OpenCodeApiClient`/`HttpUrlConnectionTransport` against a loopback HTTP server,
  asserting `/api/info` parsing, Basic auth, and response projection.
- Expanded the emulator gate to API 29 and API 36 with API-scoped artifacts, covering
  the oldest practical supported API and a newer API without claiming ARM64 behavior.
- The first API 36 run exposed a pre-existing `NavigationTest` assertion against
  off-screen scrollable content; the test now scrolls to "Task state" before asserting
  it is displayed, keeping the visibility assertion intact.

### M6.5 Phase 2 — Bionic PTY launch path
- Commit `818e758` adds `RuntimeLaunchPlan` (`executable`, `argv`, `environment`,
  `workingDirectory`) to `:runtime`; `RuntimeLauncher`/`RuntimeController` expose it
  for any child process, with linker64 as executable and argv[0].
- `TerminalViewModel` now requires `RuntimeState.Ready` and fails with a visible
  error instead of silently spawning `/system/bin/sh`; sessions are named `bash-*`.
- `TerminalSession`, `PtyNative`, and `pty.cpp` now pass full argv and environment
  into `execve`; JNI strings are copied before `fork`, so the child no longer uses
  JNI pointers or allocates a fresh argv vector after forking.
- `TerminalViewModelTest` is gated by `bionicPty=true`: it provisions the pinned
  x86_64 bootstrap, then asserts real `$PREFIX/bin/bash -i` output, `PREFIX`
  expansion, `command -v bash`, multi-session, resize, and close behavior. The
  normal API 29/36 gate keeps this opt-in because it downloads the full bootstrap.
- PR #39 checks green at `818e758`: verify push `45328936634`/PR `45328945207`,
  API 29/36 push `45328936554`/PR `45328945230`, native-runtime push `45328936579`/
  PR `45328945126`.
- Manual `runtime-spike` run `45329524820` passed 2 tests with 0 skipped; the PTY
  logcat records SELinux granting `linker64` execute on
  `/data/user/0/dev.pocketdev/files/usr/bin/bash`. This proves the x86_64 linker-hop
  Bionic PTY path, not ARM64 execution (R1 unchanged).

### M6.5 Phase 3 — PTY lifecycle and status hardening
- Commit `bc0a1d0` adds a pre-fork argv/env/error-pipe setup, checked child setup
  calls, waitpid status caching, packed exit info, `exitInfo`/`waitForExit`/
  `requestStop` JNI entry points, a poll-based reader with a close flag, process-group
  termination, separate listener/lifecycle/operation locks, and idempotent close.
- Added `PtyDriver` seam plus JVM `TerminalSessionTest` and gated instrumentation
  `PtyReliabilityTest` covering normal/non-zero/signal exits, launch failures, rapid
  lifecycle, and concurrent listeners. `runtime-spike.yml` now includes
  `PtyReliabilityTest`; `android-emulator.yml` excludes it from the normal gate.
- The first push failed native compilation because `isExited(const Handle*)` tried to
  lock a const mutex; fixed in `23df376` by taking `Handle*`, then re-verified green.
- Required checks for `23df376` all green: verify push `45336254935`/PR
  `45336260899`, API 29 push `45336254876`/PR `45336260781`, API 36 push
  `45336254876`/PR `45336260781`, native-runtime push `45336254863`/PR
  `45336260847`.
- Manual `runtime-spike` run `45336872176` on `23df376` passed 5 tests with 0
  failures/errors/skips: `RuntimeSpikeTest.pinnedBootstrapRunsThroughTheSystemLinker`,
  `TerminalViewModelTest.runsRealBionicPtyInSelectedProjectAndSupportsSessions`, and
  all three `PtyReliabilityTest` cases. Artifact XML reports `time="9.217"`; the PTY
  logcat again records SELinux granting `linker64` execute on Bionic `bash` and
  `coreutils`. This proves x86_64 PTY reliability only; R1 remains unchanged.

### M6.5 Phase 4 — project context binding
- Commits `77ba5af` (binding + UI + tests), `8e7cff7` (canonical path assertions), and
  `dbd9ce9` (deterministic busy-switch assertion) implement explicit project context.
- `AppContainer.ProjectContext` canonicalizes and validates roots under `projectsRoot`;
  outside roots are rejected. Agent servers stay bound to the root used at start and
  show a mismatch warning after selection changes; stop/start is the explicit retarget.
- Terminal sessions carry their own `ProjectContext`: existing PTYs keep their launch
  directory, new PTYs use the selected project, and the UI shows active-session context
  plus mismatch guidance. Files operations use the displayed root so an external
  selection change cannot silently retarget in-flight operations.
- First `77ba5af` run failed API 29 on a canonical path assertion
  (`/data/user/0/...` vs `/data/data/...`); fixed in `8e7cff7`. PR API 36 at `8e7cff7`
  then failed because a test observed a transient `sending` flag; fixed in `dbd9ce9`
  with a deterministic prompt latch. No test was skipped, weakened, or deleted.
- Required checks for `dbd9ce9` all green: verify push `45341636963`/PR `45341646980`,
  API 29 push `45341636953`/PR `45341647148`, API 36 push `45341636953`/PR
  `45341647148`, native-runtime push `45341636984`/PR `45341647036`.
- Manual `runtime-spike` run `45347183919` on `dbd9ce9` passed 5 tests with 0
  failures/errors/skips; artifact XML reports `time="8.461"`. The gated terminal test
  verified context-bound sessions and project switching on x86_64 API 30; R1 unchanged.

### M6.5 Phase 5 — filesystem and SAF off the main thread
- Commits `5bb2b28` (implementation), `d090838` (suspend path helpers), `6010f81`
  (wrapped refresh outcomes), and `de36fe8` (deterministic hidden-file await) implement
  the phase.
- `WorkspaceFiles` operations are suspend functions on coroutine context with
  `ensureActive()` cancellation checks, progress callbacks, explicit
  `WorkspaceResult.PartialFailure`, recursive copy/delete plans that skip symlink
  escapes, and streamed copies that check cancellation per chunk; failed copies clean
  up their destination.
- `FilesViewModel` launches operations through `viewModelScope`, moves blocking listing,
  mutation, project-store loads, SAF queries, and stream copies to `Dispatchers.IO`,
  returns state updates to `Dispatchers.Main`, exposes `FilesOperation` progress/cancel
  state, and maps success, failure, cancellation, and partial failure explicitly.
- `FilesScreen` shows operation status/progress and a cancel action; SAF import no
  longer resolves `DISPLAY_NAME` on the composition thread.
- Interim failures were fixed without weakening tests: non-suspend `destination`/`source`
  calling `wrapped` (`WorkspaceFiles.kt:156`, `:165`) in `d090838`; `ListingData` passed
  where `WorkspaceResult` was expected (`FilesViewModel.kt:92`, `:106`) in `6010f81`;
  a stale same-label `Refresh` success let the hidden-toggle assertion observe the old
  listing on API 29, fixed with explicit state waits in `de36fe8`.
- Required checks for `de36fe8` all green: verify push `45353945435`/PR `45353953532`,
  API 29 push `45353945512`/PR `45353953646`, API 36 push `45353945512`/PR
  `45353953646`, native-runtime push `45353945597`/PR `45353953703`.

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

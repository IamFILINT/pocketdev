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

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

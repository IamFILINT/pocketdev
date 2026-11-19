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
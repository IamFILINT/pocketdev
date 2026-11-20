# PocketDev — Implementation Roadmap

Each milestone is complete only when the relevant GitHub Actions run has passed.
Checklist state is updated as work lands.

## Milestone 0 — Audit and CI foundation ✅

- [x] Repository inspected; fresh repo confirmed
- [x] GitHub repo created (`IamFILINT/pocketdev`), default branch `main`
- [x] `verify.yml` runs on GitHub-hosted runners (run 44623877843 — success)
- [x] Audit documented (`docs/PROJECT_AUDIT.md`)

**Exit evidence:** workflow run 44623877843.

## Milestone 1 — Android foundation ✅

- [x] Gradle wrapper (9.3.1, checksum-pinned), AGP 9.1.1, Kotlin 2.4.21, compileSdk 37
- [x] `:core` domain module with unit tests
- [x] Compose app shell: dashboard, projects, settings; Material 3 theme
- [x] Project create/delete with validation and safe directory handling
- [x] CI: lint + unit tests + debug APK build + artifacts (run 44670913592)
- [x] Emulator launch/navigation test workflow (run 44672914788 — 3/3 passed)
- [x] PR #1 merged (merge commit `5105004`)

**Exit evidence:** `verify` run 44673636850 and `android-emulator` run 44673636885
(3/3 instrumentation tests passed), both green on the final PR commit.

## Milestone 2 — Runtime architecture and feasibility

- [ ] Audit Termux components (app, `termux-shared`, bootstrap artifacts)
- [ ] Choose integration strategy (ADR)
- [ ] License inventory for reused components
- [ ] Bootstrap/storage layout design
- [ ] ARM64 Android emulator feasibility spike in Actions

## Milestone 3 — Runtime bootstrap

- [ ] Pinned bootstrap metadata + checksum verification
- [ ] Safe extraction (zip-slip/symlink defenses)
- [ ] Resumable initialization + health checks
- [ ] Unit tests for resume/verify logic

## Milestone 4 — OpenCode Bionic provisioning

- [ ] Pin upstream release/commit + checksums in a manifest
- [ ] Process launch/stop/health supervision
- [ ] API client + event plumbing
- [ ] Deterministic controller tests

## Milestone 5 — AI workspace

- [ ] Streaming event UI, sessions, provider/model settings, permission prompts

## Milestone 6 — Terminal and filesystem

- [ ] PTY-backed terminal; file explorer; shared project identity

## Milestone 7 — Code editor

- [ ] Real editor component, syntax highlighting, multi-file tabs, external-change handling

## Milestone 8 — Package manager and Git

- [ ] Package search/install/remove UI; runtime inventory; Git status/diff/commit/branch

## Milestone 9 — Preview and development servers

- [ ] Server session management, port detection, localhost preview

## Milestone 10 — Security, performance, packaging

- [ ] Security review; size/memory measurement; signing in Actions; notices

## Milestone 11 — Release candidate

- [ ] Signed APK, checksums, draft GitHub Release

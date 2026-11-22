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

- [x] Audit Termux components (app, `termux-shared`, bootstrap artifacts)
- [x] Choose integration strategy (ADR)
- [x] License inventory for reused components
- [x] Bootstrap/storage layout design
- [x] ARM64 Android emulator feasibility spike in Actions
  — measured: no GitHub-hosted runner can build or boot Android on ARM64 with the
  stock toolchain (`docs/ARM64_FEASIBILITY.md`); functional CI stays on `x86_64`
  API 30 and ARM64 stays cross-compiled/static

## Milestone 3 — Runtime bootstrap

- [x] Pinned bootstrap metadata + checksum verification (`BootstrapPins`,
  `BootstrapChecksums`: audited tag/size/SHA-256, fail-closed, artifact deleted)
- [x] Safe extraction (zip-slip/symlink defenses) (`BootstrapArchive`: traversal/
  absolute/NUL/backslash rejection, zip-bomb caps, contained `SYMLINKS.txt`
  re-creation, upstream exec bits)
- [x] Resumable initialization + health checks (`BootstrapManager`: byte-range
  resume, verify-before-extract, `usr.prev` rollback, journal; `BootstrapHealth`:
  structural checks)
- [x] Unit tests for resume/verify logic (45 new JVM tests, 101 total in `:core`)

Honest scope: health checks are structural only — real Bionic execution stays
unverified because no ARM64 Android environment exists in Actions
(`docs/ARM64_FEASIBILITY.md`).

**Post-M3 gate (ADR-0002, decided and validated 2026-11-21):** x86_64 API 30 spike
run `44876316131` proved relocation via `linker64` (`bash`/`ls` run; direct exec
blocked by W^X). Shebang exec remained blocked with the stock `termux-exec`
release 2.5.0 because of the compiled-in Termux prefix; a follow-up spike
(`spike/termux-exec-env`, run `44878997591`) confirmed the stock preload honors
the `TERMUX_APP__DATA_DIR` / `TERMUX__PREFIX` environment overrides and now
executes shebang scripts through `/system/bin/linker64` (exit 0, `shebang-ok`).
The gate is now closed: no custom prefix-built preload is required — the stock
preload + env override is sufficient for ADR-0002.

**Android wiring landed:** the `:runtime` Android library now provides the
environment builder (including the Termux data-dir/prefix overrides), the
linker-hop launcher (`/system/bin/linker64`), preload deployment checks, and a
real execution health check (bash + coreutil through the linker) used by the
bootstrap manager. The app depends on `:runtime`; wiring the provisioning UX and
the OpenCode controller is the remaining Milestone 4 work.

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

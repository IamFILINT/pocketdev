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
bootstrap manager. The dashboard now triggers provisioning (install/retry) and
renders the live state, which is seeded from the bootstrap journal on restart.
OpenCode Bionic is pinned (`v2.3.2`, ARM64, MIT, SHA-256 verified) and
`OpenCodeController` implements resumable acquisition plus start/stop of the
binary; the dashboard exposes install/retry and live agent state. Health/API
supervision and a real-device ARM64 run remain.

## Milestone 4 — OpenCode Bionic provisioning

- [x] Pin upstream release/commit + checksums in a manifest (`OpenCodePins`, v2.3.2)
- [x] Process launch/stop/health supervision (`OpenCodeServer`: loopback-only
      `opencode serve --hostname 127.0.0.1`, readiness parsed from the documented
      stdout lines, real `Starting`/`Listening`/`Exited`/`Failed` state, child
      reaped; ADR-0004)
- [x] API client + auth (`OpenCodeApiClient`: HTTP Basic with upstream's fixed
      `opencode` username and the captured per-run password; `/api/info`,
      session list/create/prompt; dependency-free JSON reader; JVM-tested against
      a real loopback `HttpServer`)
- [ ] Event plumbing (SSE stream → UI state) — tracked under Milestone 5
- [x] Deterministic controller/server/parser/client unit tests (`:core`, `:runtime`)

Honest scope: the supervision path is exercised on the CI JVM with a script
process and the client against a real HTTP server; the actual ARM64 Bionic binary
still cannot run in Actions (risk R1). Real-device execution remains the accepted
gap and is not claimed as validated here.

**Exit evidence:** PR #16, runs `44999755169` (`verify`, green) and
`44999755172` (`android-emulator`, green); merge commit `35e6716` on `main`.

## Milestone 5 — AI workspace

- [x] Streaming event UI, sessions, prompts, provider/model settings, permission prompts

**Exit evidence:** PR #36, verify `45237366899`/`45237378788` and
`android-emulator` `45237367091`/`45237378747` (all green). Permission execution
against a live ARM64 OpenCode server remains the accepted R1 device gap.

## Milestone 6 — Terminal and filesystem

- [x] File explorer with create/rename/copy/move/delete/search/preview and SAF import/export (PR #37)
- [x] Shared selected-project identity across Projects, Files, and Agent (PR #37)
- [x] PTY-backed terminal with ANSI/cursor/resize/scrollback/copy-paste/signals/multi-session (PR #38)

**Exit evidence:** PR #37 verify `45248176459`/`45248191102` and `android-emulator`
`45248176792`/`45248190987` (all green). PR #38 verify `45254925593`/`45254937737`
and `android-emulator` `45254925748`/`45254937817` (all green). Physical ARM64
device execution remains the accepted R1 gap.

## Milestone 7 — Code editor

- [x] Real editor component, syntax highlighting, multi-file tabs, external-change handling (PR #40)

**Exit evidence:** PR #40 verify `45964749210`/`45964755939`, `android-emulator`
`45964749127`/`45964755903`, and `native-runtime` `45964749085`/`45964756085`
(all green). Physical ARM64 device execution remains the accepted R1 gap.

## Milestone 8 — Package manager and Git

- [ ] Package search/install/remove UI; runtime inventory; Git status/diff/commit/branch

## Milestone 9 — Preview and development servers

- [ ] Server session management, port detection, localhost preview

## Milestone 10 — Security, performance, packaging

- [ ] Security review; size/memory measurement; signing in Actions; notices

## Milestone 11 — Release candidate

- [ ] Signed APK, checksums, draft GitHub Release

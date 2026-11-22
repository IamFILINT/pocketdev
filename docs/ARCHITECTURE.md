# PocketDev — Architecture

> Status: Milestone 3 plus Android runtime wiring (`:runtime`). Domain logic
> stays in `:core`; `:runtime` holds the Android-only environment, linker-hop
> process spawn and execution health checks.

## Module layout

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android application (Compose) | UI shell, navigation, screens, Android-specific wiring |
| `:core` | Pure Kotlin/JVM library | Domain logic with no Android dependencies: validation, path safety, project registry, state machines, redaction |
| `:runtime` | Android library | Bootstrap manager wiring: environment builder, linker-hop process spawn (`/system/bin/linker64`), preload deployment, execution health checks |

Keeping `:core` Android-free means its logic is testable on a plain JVM in CI, fast and
without an emulator.

Planned modules (later milestones):

| Module | Purpose |
| --- | --- |
| `:terminal` | PTY host and terminal session management |
| `:ai` | OpenCode process supervision + API client + event mapping |
| `:workspace` | File explorer operations, editor file synchronization, Git |

## Layer boundaries (master prompt §4.1)

- UI must not touch runtime internals directly.
- Runtime exposes a small API (state + commands) consumed by the app.
- Terminal sessions are owned by a dedicated subsystem.
- OpenCode lifecycle is owned by a dedicated controller.
- Every subsystem shares one project identity and one on-disk root
  (`Project` in `:core`).

## Current data flow (Milestone 1)

```
MainActivity
  └── PocketDevTheme (theme from AppContainer)
        └── PocketDevApp (NavHost + bottom bar)
              ├── DashboardScreen   <- ProjectsViewModel.uiState (count), DashboardViewModel.runtimeState
              ├── ProjectsScreen    <- ProjectsViewModel (ProjectStore in :core)
              └── SettingsScreen    <- SettingsViewModel (theme mode persisted; API key in memory only)
```

`AppContainer` (in `:app`) owns `RuntimePaths` (`:core`): `$PREFIX` at
`files/usr`, projects at `files/home/projects`, registry at
`files/state/projects.tsv`, and the theme preference.

## Key decisions

1. **Kotlin + Jetpack Compose.** No existing codebase constraint existed (fresh
   repository), so the modern first-party stack was chosen for maintainability and
   testability.
2. **Pure-Kotlin `:core`.** Enables fast JVM unit tests in CI without emulator cost.
3. **TSV project registry with atomic writes.** Dependency-free and honest for
   Milestone 1; replaced by a richer store when per-project session state is added.
4. **No simulated runtime/AI state.** The dashboard reports `NotInstalled` until a
   real runtime controller exists.
5. **GitHub Actions as the only build/test environment.** See `docs/CI_CD.md`.
6. **Bootstrap logic lives in `:core`.** Pinned metadata, checksum verification,
   safe extraction, resumable manager and structural health checks are Android-free
   so they are unit-tested on a plain JVM; execution is not claimed
   (`docs/ARM64_FEASIBILITY.md`).
7. **Runtime integration strategy decided in ADR-0001** (`docs/adr/`): a
   PocketDev-owned runtime manager provisions a pinned Termux bootstrap into
   app-private storage; no Termux app fork, no second APK. The prefix/package-name
   sub-problem is decided in ADR-0002 from measured emulator evidence: keep the
   PocketDev prefix, launch via `/system/bin/linker64`, and use the stock
   `termux-exec-ld-preload.so` from the pinned bootstrap for shebang/`exec`
   (`docs/adr/0002-…`); the runtime wires the `TERMUX_APP__DATA_DIR` / `TERMUX__PREFIX`
   environment overrides so the stock preload targets our prefix.

## Security boundaries

- `PathSafety` rejects absolute paths, drive letters, `..`, empty segments, and NUL.
- Project deletion only deletes directories provably inside `projectsRoot`.
- API keys are not persisted anywhere yet (settings screen states this explicitly).
- `SecretRedaction` provides log-safe redaction utilities for later use.

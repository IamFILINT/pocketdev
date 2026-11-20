# PocketDev — Architecture

> Status: Milestone 1 (Android foundation). Physical layout will evolve.

## Module layout

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android application (Compose) | UI shell, navigation, screens, Android-specific wiring |
| `:core` | Pure Kotlin/JVM library | Domain logic with no Android dependencies: validation, path safety, project registry, state machines, redaction |

Keeping `:core` Android-free means its logic is testable on a plain JVM in CI, fast and
without an emulator.

Planned modules (later milestones):

| Module | Purpose |
| --- | --- |
| `:runtime` | Termux-derived bootstrap manager, safe extraction, health checks |
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

`AppContainer` (in `:app`) owns `projectsRoot` (app-private `files/<app>/projects`),
the `ProjectStore` registry (`files/<app>/state/projects.tsv`), and the theme preference.

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
6. **Runtime integration strategy decided in ADR-0001** (`docs/adr/`): a
   PocketDev-owned runtime manager provisions a pinned Termux bootstrap into
   app-private storage; no Termux app fork, no second APK. The prefix/package-name
   sub-problem is explicitly deferred to ADR-0002, gated on an in-CI spike
   (`docs/TERMUX_AUDIT.md`, `docs/BOOTSTRAP_LAYOUT.md`).

## Security boundaries

- `PathSafety` rejects absolute paths, drive letters, `..`, empty segments, and NUL.
- Project deletion only deletes directories provably inside `projectsRoot`.
- API keys are not persisted anywhere yet (settings screen states this explicitly).
- `SecretRedaction` provides log-safe redaction utilities for later use.

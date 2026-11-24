# PocketDev — Architecture

> Status: Milestone 4. Domain logic stays in `:core`; `:runtime` holds the
> Android-only environment, linker-hop process spawn, execution health checks, and
> OpenCode Bionic provisioning + `serve` supervision. The app shell talks to both
> through small controller APIs.

## Module layout

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android application (Compose) | UI shell, navigation, screens, Android-specific wiring |
| `:core` | Pure Kotlin/JVM library | Domain logic with no Android dependencies: validation, path safety, project registry, state machines, redaction, agent pins/installer, serve-output parsing, JSON reader, OpenCode HTTP API client |
| `:runtime` | Android library | Bootstrap wiring (environment, linker-hop spawn, preload, execution health) and the OpenCode agent: provisioning, `serve` supervision, connection state |

Keeping `:core` Android-free means its logic is testable on a plain JVM in CI, fast and
without an emulator. The agent is a special case: its **binary** is Android-only, but
everything that can be pure — pin metadata, installer, output parsing, HTTP client — is
in `:core` and unit-tested.

Planned modules (later milestones):

| Module | Purpose |
| --- | --- |
| `:terminal` | PTY host and terminal session management |
| `:ai` | Event-stream mapping and provider/permission UI (the process controller and API client currently live in `:runtime`/`:core`) |
| `:workspace` | File explorer operations, editor file synchronization, Git |

The PTY host is currently implemented in the app (JNI/native under
`app/src/main/cpp` plus Kotlin session management under
`app/src/main/java/dev/pocketdev/terminal`) instead of a separate `:terminal`
module; the module split remains deferred. `TerminalViewModel` now requires
`RuntimeState.Ready`, asks `RuntimeController` for a `RuntimeLaunchPlan`, and passes
its executable, argv, environment, and working directory to `PtyNative`; the native
child `execve()`s `/system/bin/linker64` → `$PREFIX/bin/bash -i`.

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

## Current data flow (Milestone 4 — agent)

```
DashboardViewModel
  ├── runtime.state: StateFlow<RuntimeState>          <- RuntimeController (bootstrap journal)
  └── openCode.state: StateFlow<AgentState>           <- OpenCodeController (AgentInstaller)

OpenCodeController
  ├── provision()            -> :core AgentInstaller (resumable, SHA-256, atomic replace)
  ├── startServer(projectDir)-> :runtime OpenCodeServer
  │                             └── ProcessLauncher -> RuntimeLauncher -> /system/bin/linker64
  │                             └── OpenCodeServeOutputParser (:core)
  │                             └── StateFlow<OpenCodeServerState>
  ├── connection()           -> OpenCodeServerConnection? (url, port, username, password)
  └── newApiClient()         -> :core OpenCodeApiClient (Basic auth, /api/info, sessions)
```

The server state is authoritative: no client is handed out until the process is
actually `Listening`. The password never leaves the controller except into the
in-memory client.

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
8. **Agent process control is a controller, not a UI concern.** `OpenCodeController`
   owns provisioning and `serve` supervision; `OpenCodeServer` publishes a real state
   machine; `OpenCodeApiClient` is the only thing that speaks HTTP. The UI maps states,
   never starts processes directly. Readiness is parsed from upstream's documented
   output (`OpenCodeServeOutputParser`) and bound to loopback only (ADR-0004).
9. **Pins are data, not code.** `OpenCodePins` and `BootstrapPins` are validated value
   objects in `:core`; bumping an artifact is a reviewed metadata change verified by
   CI, never an implicit `latest` fetch.

## Security boundaries

- `PathSafety` rejects absolute paths, drive letters, `..`, empty segments, and NUL.
- Project deletion only deletes directories provably inside `projectsRoot`.
- API keys are not persisted anywhere yet (settings screen states this explicitly).
- `SecretRedaction` provides log-safe redaction utilities for later use.
- The agent password is per-run, held only in memory (`OpenCodeServerConnection` inside\n  the `StateFlow`), never logged or persisted; it rotates on every server start.\n- The agent binds `127.0.0.1` explicitly and the captured URL is validated as\n  loopback before a connection is accepted; no LAN/internet exposure.

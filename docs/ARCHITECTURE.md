# PocketDev — Architecture

> Status: Milestone 7.5 (editor reliability) complete. Domain logic stays in
> `:core`; `:runtime` holds the Android-only environment, linker-hop process spawn,
> execution health checks, and OpenCode Bionic provisioning + `serve` supervision.
> The app shell talks to both through small controller APIs.

## Module layout

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android application (Compose) | UI shell, navigation, screens, Android-specific wiring |
| `:core` | Pure Kotlin/JVM library | Domain logic with no Android dependencies: validation, path safety, project registry, state machines, redaction, agent pins/installer, serve-output parsing, JSON reader, OpenCode HTTP API client, editor text codec/buffer/search/syntax/formatting, draft serialization, and JVM performance baselines |
| `:runtime` | Android library | Bootstrap wiring (environment, linker-hop spawn, preload, execution health) and the OpenCode agent: provisioning, `serve` supervision, connection state |

Keeping `:core` Android-free means its logic is testable on a plain JVM in CI, fast and
without an emulator. The agent is a special case: its **binary** is Android-only, but
everything that can be pure — pin metadata, installer, output parsing, HTTP client — is
in `:core` and unit-tested.

Module split status (later milestones):

| Module | Current implementation | Deferred work |
| --- | --- | --- |
| `:terminal` | JNI/native PTY host under `app/src/main/cpp` plus Kotlin session management in `:app` | Dedicated module extraction |
| `:ai` | Process controller in `:runtime`; API client, parser, pins, and installer in `:core`; SSE/permission UI in `:app` (Milestone 5) | Dedicated module extraction |
| `:workspace` | File explorer and editor file synchronization in `:core`/`:app` (Milestones 6–7.5) | Git and dedicated module extraction |

As of M7.5, the PTY host is implemented in the app instead of a separate
`:terminal` module; the module split is deferred. `TerminalViewModel` requires
`RuntimeState.Ready`, asks `RuntimeController` for a `RuntimeLaunchPlan`, and passes
its executable, argv, environment, and working directory to `PtyNative`; the native
child `execve()`s `/system/bin/linker64` → `$PREFIX/bin/bash -i`.

`pty.cpp` prepares argv/env and the child-error pipe before `fork()`, restricts the
child to async-signal-safe setup calls, and reports setup failures to the parent.
The parent reaps child status and exposes normal exit, non-zero exit, signal
termination, and launch failure through `PtyExitInfo`. `TerminalSession` guards
listeners and lifecycle state with separate locks, calls listeners outside locks,
uses a poll-based reader that observes close requests, and makes `close()` idempotent.

## Layer boundaries (master prompt §4.1)

- UI must not touch runtime internals directly.
- Runtime exposes a small API (state + commands) consumed by the app.
- Terminal sessions are owned by a dedicated subsystem.
- OpenCode lifecycle is owned by a dedicated controller.
- Every subsystem shares one project identity and one on-disk root
  (`Project` in `:core`).

## Data flow (Milestone 1 — historical)

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

## Data flow (Milestone 4 — agent)

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

## Data flow (Milestone 7.5 — code editor)

```
FilesScreen
  └── onOpenInEditor(FileEntry) -> EditorViewModel.open(relativePath)
        └── workspaceId + rootPath + relativePath -> EditorDocumentId.key
        └── WorkspaceFiles.readTextFile / writeTextFile
              ├── TextFileCodec (strict UTF-8/BOM, mixed-newline fidelity, binary/NUL rejection)
              ├── FileSignature (content digest + size + timestamp; stale-write guard)
              ├── PathSafety.resolveWithinNoFollow (rejects symlink components)
              └── truncated > 1 MiB -> read-only snapshot + UI warning

EditorViewModel
  ├── tabs keyed by stable EditorDocumentId; project switch cannot retarget an open tab
  ├── EditorBuffer: text, incremental line map, undo/redo, markSaved(revision, text)
  ├── TextSearch / EditorFormatter / SyntaxHighlighter
  │     └── formatting is language-aware and preserves syntax-significant whitespace
  ├── external changes: dirty conflict or clean reload; no silent overwrite
  └── drafts: app-private RuntimePaths.drafts, debounced bytes, schema/version,
        hash+size match for auto-restore, explicit restore/keep/discard for conflicts
```

The editor owns only its in-memory buffers and snapshots; `WorkspaceFiles` remains the
single filesystem authority. Saves stage content and commit with
`Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` where the provider supports it, with a
fallback and temp-file cleanup; the last observed `FileSignature` is still required, so
an external change fails the save instead of being overwritten. Draft recovery never
auto-restores a discarded or changed disk file: it requires hash+size equality or an
explicit user choice. See `docs/PERFORMANCE_BASELINE.md` for the measured baseline and
`docs/RISK_REGISTER.md` for the remaining provider-specific atomicity limitation.

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
10. **Editor is native Compose + pure JVM core.** No WebView/CDN or heavy desktop
    editor dependency; editing, syntax, search, history, formatting, encoding, and
    external-change logic stay testable in `:core`. Files larger than 1 MiB are
    opened read-only from the first 1 MiB with an explicit warning.
11. **Reliability before optimization.** M7.5 binds tabs to stable workspace
    identity, clears dirty only on exact saved revision+text match, treats content
    digest as the primary external-change signal, and keeps drafts app-private and
    conflict-gated. Performance work starts from committed JVM/Android baselines;
    no file-size limit was reduced and no correctness assertion was weakened.

## Security boundaries

- `PathSafety` rejects absolute paths, drive letters, `..`, empty segments, and NUL;
  `resolveWithinNoFollow` additionally rejects existing symlink components in editor paths.
- Project deletion only deletes directories provably inside `projectsRoot`.
- API keys are stored encrypted with Android Keystore AES/GCM with randomized IV; only
  the `{env:POCKETDEV_API_KEY}` placeholder and redacted values reach logs/config output.
- `SecretRedaction` provides log-safe redaction utilities for later use.
- The agent password is per-run, held only in memory (`OpenCodeServerConnection` inside
  the `StateFlow`), never logged or persisted; it rotates on every server start.
- The agent binds `127.0.0.1` explicitly and the captured URL is validated as
  loopback before a connection is accepted; no LAN/internet exposure.
- Editor drafts are app-private versioned byte blobs. Unreadable/unknown versions are
  retained raw and reported; auto-restore requires disk hash+size equality, and conflicts
  require explicit `restoreDraft()` or `keepDiskDraft()`.
- Editor saves stage then commit with atomic move where possible; fallback cleanup and
  stale-signature rejection prevent partial or silent overwrites. Provider-specific
  atomicity remains an explicit limitation in `docs/RISK_REGISTER.md`.

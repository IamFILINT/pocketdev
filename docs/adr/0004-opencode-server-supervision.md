# ADR-0004 — OpenCode server supervision and API authentication

- Status: Accepted
- Date: 2026-11-22

## Context

Milestone 4 needs the agent to be usable from the PocketDev UI, not just installed
(master prompt §9.5). The pinned OpenCode Bionic build embeds the web UI, but
PocketDev must expose its own mobile-first workspace over a documented interface,
so the terminal TUI is not the integration path.

Upstream `opencode serve` (verified against `v2.3.2`,
`packages/cli/src/server-process.ts`) runs in the foreground and prints two plain
stdout lines once ready:

```
server listening on http://127.0.0.1:<port>
server password <base64url>
```

The port is ephemeral unless `--port` is supplied. Authentication is HTTP Basic
with a fixed username `opencode` (`ServerAuth.configLayer`) and the per-run
password; upstream also accepts `?auth_token=` and session cookies, which
PocketDev does not need.

## Decision

1. **Foreground `serve`, no `--service`/`--stdio`.** Default mode is foreground,
   prints both URL and password, and stays attached until killed. `--stdio` prints
   only `{url}` and `--service` requires a config file/registration, so neither
   fits an app-private child process.
2. **Bind loopback explicitly.** Launch `opencode serve --hostname 127.0.0.1`.
   The captured URL host is validated as loopback before a connection is accepted;
   a non-loopback URL fails closed. No LAN/internet exposure.
3. **Parse real readiness output, never fabricate.** `:core`
   `OpenCodeServeOutputParser` consumes stdout lines, tolerates log/ANSI noise, and
   completes only when both lines are seen. `:runtime` `OpenCodeServer` publishes
   real transitions: `Stopped`, `Starting`, `Listening`, `Failed`, `Exited`.
4. **Linker hop reused.** The agent is started through `/system/bin/linker64`
   (`RuntimeProcessLauncher` → `RuntimeLauncher`), same as every other runtime
   program (ADR-0002), with the project directory as cwd.
5. **Supervise and reap.** A reader thread drains output (so the child cannot block
   on a full pipe) and a waiter records the true exit code. `stop()` destroys the
   child and returns to `Stopped`.
6. **Dependency-free API client.** `:core` `OpenCodeApiClient` uses
   `HttpURLConnection` with Basic auth and a small JSON reader, so `:core` keeps no
   serialization plugin and behaves identically on JVM tests and Android. Scope:
   `/api/info` (health/readiness), session list/create, prompt. SSE event streaming
   is deliberately deferred to Milestone 5.

## Consequences

- The password exists only in memory and in the `StateFlow`; it is never logged or
  persisted. It changes on every server start.
- `OpenCodeController.connection()`/`newApiClient()` return null until the server
  is actually `Listening`, so callers cannot talk to a half-started agent.
- Real ARM64 execution still cannot run in GitHub Actions; CI validates the
  supervision state machine against a script process and the client against a real
  loopback HTTP server. The device-level proof remains an accepted gap (R1).
- The parser depends on upstream's printed wording. It is isolated in one class
  with unit tests and the wording is pinned by ADR-0003; if upstream changes it,
  only that class changes and tests fail loudly rather than silently hanging.

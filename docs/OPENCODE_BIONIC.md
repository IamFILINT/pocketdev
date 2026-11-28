# PocketDev — OpenCode Bionic integration

> Status: Milestone 4 complete; Milestones 5 and 6.5 add the SSE event UI and
> Keystore-backed credentials. This document records the pinned agent, provisioning,
> supervision, authentication, API access, and the remaining honest gaps. Decisions
> live in `docs/adr/0003-opencode-provisioning.md` and
> `docs/adr/0004-opencode-server-supervision.md`.

## 1. Pin

Source: <https://github.com/bd-loser/opencode-bionic> (MIT). Upstream:
<https://github.com/anomalyco/opencode>.

| Field | Value |
| --- | --- |
| Release tag | `v2.3.2` |
| Upstream version | `2.3.2` |
| Artifact | `opencode` (self-contained aarch64 binary, web UI embedded) |
| Size | 211,486,984 B |
| SHA-256 | `ea93b4c42b20af123220f2bb69c9810c3b735c387d23ce78b0145dbb4f1b5f63` |
| ABI | `arm64-v8a` only (upstream builds no x86_64) |
| Minimum Android | API 29 (Android 10), because of the patched Bun runtime |
| License | MIT (bundled `bun-termux` / `@androidtui` notices still owed before release) |

Pinned in `:core` as `OpenCodePins.aarch64` (validated on construction: tag, file
name, https URL, digest shape, positive size, API range, ABI, license).

## 2. Acquisition (fetch, do not embed)

- Downloaded on first run to `files/agent/opencode`, outside `$PREFIX`, so a runtime
  reinstall never removes it and agent state never lands in the rootfs.
- `AgentInstaller` resumes with HTTP Range, verifies **size and SHA-256 before
  replacing**, deletes a bad artifact, and `chmod`s the result executable.
- The 211 MB payload is deliberately not bundled in the APK (ADR-0003): it would
  bloat every download/update. The UI shows real progress from the installer state.

## 3. Launch

- Every launch goes through `/system/bin/linker64 <program> <args>` (ADR-0002, W^X
  first hop) with the runtime environment from `RuntimeEnvBuilder`.
- The project directory is the child's cwd, so the agent sees the user's workspace.
- ARM64 + API 29 are enforced fail-closed: non-aarch64 devices and pre-API-29 devices
  are refused before launch.

## 4. Supervision (`OpenCodeServer`)

- Command: `opencode serve --hostname 127.0.0.1` (foreground/default mode).
- One reader thread drains stdout (so the child cannot block on a full pipe); one
  waiter thread records the real exit code.
- State machine: `Stopped` → `Starting` → `Listening(connection)`; on failure
  `Failed(message)`; on exit `Exited(code)`. `stop()` destroys the child and returns
  to `Stopped`.
- A non-loopback URL is refused (fail closed); PocketDev never exposes the agent to
  the LAN or internet.

## 5. Readiness and authentication

Upstream `v2.3.2` prints, once ready:

```
server listening on http://127.0.0.1:<port>
server password <base64url>
```

`OpenCodeServeOutputParser` (in `:core`) consumes those lines incrementally, strips
ANSI/log noise, tolerates reordering, and completes only when both are seen. The port
is ephemeral unless `--port` is passed; the printed URL is authoritative.

Every API call uses HTTP Basic:

- username: `opencode` (fixed upstream)
- password: the captured per-run value

The password exists only in memory (`OpenCodeServerConnection` inside the
`StateFlow`) and is never logged or persisted. It rotates on every server start.

## 6. API client (`OpenCodeApiClient`)

`HttpURLConnection`-based, injectable `HttpTransport`, dependency-free JSON reader
(`:core`, no serialization plugin). All calls are blocking; callers dispatch off the
main thread.

| Method | Endpoint | Use |
| --- | --- | --- |
| `info()` | `GET /api/info` | Health/readiness probe (`ServerInfo`: version, pid, urls) |
| `listSessions(limit)` | `GET /api/session` | Session list for the workspace UI |
| `createSession(title)` | `POST /api/session` | New session |
| `prompt(id, text)` | `POST /api/session/{id}/prompt` | Send a message (raw response today) |

Error handling: non-2xx throws `OpenCodeApiException` with the status; malformed JSON
throws `Json.JsonException`.

**Milestone 5:** the SSE stream at `GET /api/event` is mapped to UI state; `prompt()`
continues to return the raw API body for direct clients.

## 7. Update procedure

1. Audit the new upstream release (version, size, SHA-256, ABI, minimum API, license).
2. Replace the metadata in `OpenCodePins` (tag, URL, size, digest, upstream version).
3. Update the pin table above and `docs/DEPENDENCY_INVENTORY.md`.
4. Run CI (`verify`, emulator, and the static ELF check in `native-runtime-ci.yml`).
5. Do not auto-upgrade in production; the pin is explicit and reviewed.

## 8. Gaps

- **No ARM64 Android environment in GitHub Actions** (risk R1): the Bionic binary is
  never executed in CI. Supervision and the client are tested against a script process
  and a real loopback HTTP server; device execution is the accepted gap.
- **Readiness is a text contract**, isolated in one parser with tests; upstream wording
  changes fail loudly rather than hanging (risk R14).
- **Event stream and tool-permission UI** are implemented (Milestone 5); provider-specific permission execution against a live ARM64 server remains unverified under R1.
- **Keystore-backed API-key storage** is implemented (Milestone 6.5); plaintext keys are not persisted, and real-device provider responses remain unverified under R1.
# ADR-0003 — OpenCode Bionic acquisition and provisioning

- Status: Accepted
- Date: 2026-11-22

## Context

Milestone 4 needs the AI coding engine (master prompt §2.3). Upstream OpenCode
ships glibc binaries only; the Bionic-specific fork publishes self-contained
Android aarch64 builds. The agent must run as a child of PocketDev in
app-private storage (no second APK, no PRoot), and the APK must stay installable
while the payload is large.

Audited artifact (2026-11-22): `bd-loser/opencode-bionic` release `v2.3.2`
(upstream OpenCode `2.3.2`), MIT, published with `SHA256SUMS`.

| Asset | Size | Notes |
| --- | --- | --- |
| `opencode` | 211,486,984 B | self-contained Bionic aarch64 binary, web UI embedded |
| `opencode_2.3.2_aarch64.deb` | 70,063,036 B | Termux apt packaging (not used) |

`SHA256` of `opencode`:
`ea93b4c42b20af123220f2bb69c9810c3b735c387d23ce78b0145dbb4f1b5f63`.

Build inputs pinned by upstream: `@androidtui` 0.9.7, `bun-termux`
`1.4.5-patched`, `bun-pty` `0.4.11` (embedded). Minimum Android 10 / API 29
because of the patched Bun runtime.

## Decision

1. **Fetch, do not embed.** The 211 MB binary is downloaded during first-run
   provisioning into `<dataDir>/files/agent/opencode` and verified against the
   pinned SHA-256. Embedding it would bloat every APK download/update and the
   Play/package installer path; the binary also exceeds practical embedded
   payload size.
2. **Pin the release, not the source.** Consume the upstream Bionic release
   artifact (`OpenCodePins`) instead of forking or rebuilding; record release
   tag, upstream version, size, SHA-256 and MIT license in `:core`.
3. **ARM64 only.** The upstream project does not build x86_64, so the agent
   cannot be exercised on the x86_64 CI emulator. Provisioning refuses
   non-aarch64 devices; execution proof requires a physical ARM64 device
   (known gap R1/R13).
4. **Resumable, fail-closed install.** Partial downloads resume with HTTP Range;
   size/checksum mismatch deletes the artifact and leaves any previously
   installed binary untouched. The binary is chmod-ed executable and started via
   `/system/bin/linker64` with the project directory as cwd (W^X first hop,
   ADR-0002).
5. **Agent data lives outside `$PREFIX`** (`files/agent`), so runtime reinstall
   never removes the agent, and agent state never lands in the runtime rootfs.

## Consequences

- First run requires ~212 MB download (plus bootstrap ~33 MB); the UI must show
  real progress and handle failures, and free-space checks are needed before
  download (follow-up on `:core`).
- The `.deb` is not used: PocketDev does not run apt for its own components.
- Updating the agent is a new pin: replace `OpenCodePins` metadata, re-verify the
  checksum, and keep the old binary until the new one verifies.
- Licensing: OpenCode Bionic is MIT; embedded `bun-termux`/`@androidtui` licenses
  must appear in the final `NOTICE` before release (LICENSE_INVENTORY follow-up).

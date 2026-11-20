# ADR-0001: Runtime integration strategy

- **Status:** Accepted (2026-11-20, Milestone 2)
- **Context:** prompt §8.2 ("Possible approaches include… Choose based on technical
  correctness, Android compatibility, licensing, and maintainability")
- **Inputs:** `docs/TERMUX_AUDIT.md`, `docs/LICENSE_INVENTORY.md`

## Decision

**PocketDev owns a runtime manager (`:runtime`, planned module) that provisions a
pinned Termux bootstrap into PocketDev-private storage at first run.** We do not fork
the Termux application, and we do not ship a second APK.

Concretely:

1. **Architecture = prompt option 3**: PocketDev-owned runtime manager —
   download (never bundle), checksum-verify, safely extract, health-check, and
   record state for a pinned bootstrap artifact (model already in
   `:core` as `BootstrapManifest`/`BootstrapArtifact`; state machine in
   `RuntimeState`/`RuntimeStateMachine`).
2. **Terminal reuse = prompt option 2, restricted to permissive files**: reuse
   `terminal-emulator` and `terminal-view` (Apache-2.0) for the PTY/session layer.
   No file from `termux-shared`'s GPLv3 subtree (`com/termux/shared/termux/*`) is
   copied. `termux-exec` (Apache-2.0) is the candidate for exec interception.
3. **Bootstrap source = upstream release archives**, pinned by release tag + SHA-256
   + size (see `BootstrapManifest`). Downloaded into app-private storage on first
   run; not present in the APK (avoids redistributing third-party binaries and keeps
   APK size sane — risk R5).
4. **The prefix/package-name strategy is explicitly deferred to ADR-0002** and must
   be decided by an in-CI feasibility spike, because stock Termux packages are
   compiled for `/data/data/com.termux/files/usr` and do not work under another app
   id without either runtime path rewriting or a rebuild (audit §C1).

## Options considered

### Option 1 — Fork/derivative of the Termux Android app

Rejected:

- Violates the product constraint of one integrated APK and no separate Termux
  dependency (prompt §4).
- Pulls the entire GPLv3 app codebase into the distribution and its maintenance
  burden (activity lifecycle, session service, plugin add-ons we do not need).

### Option 2 — Link `termux-shared` as a library and reuse app components

Rejected as a strategy, partially accepted as file-level reuse:

- `termux-shared` is MIT *by default*, but its `com/termux/shared/termux/*` subtree
  is GPLv3-only; linking it would make the licensing question for all of PocketDev
  "GPLv3 combined work" rather than a per-file choice.
- What PocketDev actually needs from that layer (terminal session handling) already
  exists as Apache-2.0 `terminal-emulator`/`terminal-view`.

### Option 3 — PocketDev-owned runtime manager (chosen)

- Keeps licensing modular: Apache-2.0 terminal stack, MIT/GPLv3-free core, our own
  Kotlin orchestration, third-party binaries downloaded (not redistributed).
- Matches the prompt's bootstrap manager design (§8.4) and the existing
  `RuntimeState` state machine.
- Cost: we must solve the prefix problem ourselves (ADR-0002) — that cost exists for
  every option except forking the whole app.

## Consequences

- **License posture:** PocketDev's own license stays undecided until ADR-0002 lands.
  With Apache-2.0 terminal files and a downloaded (not bundled) bootstrap, a
  permissive license for our code remains achievable; copying any GPLv3
  `termux-shared` file would end that. See `LICENSE_INVENTORY.md`.
- **targetSdk stays 36.** We rely on the `linker64` + `LD_PRELOAD` exec chain from
  `termux-exec` instead of Termux's targetSdk-28 escape hatch (audit §C2). This is
  the largest technical risk of the milestone and is gated on the Milestone 3 spike.
- **New module `:runtime`** (planned in `ARCHITECTURE.md`) owns download/verify/
  extract/health-check; UI consumes only `RuntimeState` (no UI changes needed —
  dashboard already renders the real states).
- **Package management** (`prompt §8.6`) is scoped by ADR-0002: installing packages
  from Termux's official mirrors under a different app id conflicts with upstream
  intent (audit §C4), so the package-repository strategy is a separate, explicit
  decision — not an automatic consequence of this ADR.

## Validation gates (do not skip)

1. **Milestone 3 spike (CI, x86_64 emulator):** extract a pinned bootstrap into
   app-private storage of a targetSdk-36 app; run `bash`, coreutils, and one
   compiled-in-prefix tool (e.g. Python) via the linker64 + `LD_PRELOAD` chain;
   record exactly which path-relocation mechanisms held. Outcome feeds ADR-0002.
2. **ARM64 feasibility:** `arm64-spike.yml` verdict recorded honestly in
   `docs/ARM64_FEASIBILITY.md` (prompt §5.4) — does not block ADR-0001 but blocks
   any claim of end-to-end Bionic validation.

## References

- `docs/TERMUX_AUDIT.md` — component licenses, fixed-prefix and W^X constraints.
- Prompt §8 (Termux-derived runtime), §5.4 (ARM64 validation limits).
- termux-packages wiki: Termux execution environment; Termux and Android 10.
- termux-packages#24407 — planned dynamic path-prefix rewriting.

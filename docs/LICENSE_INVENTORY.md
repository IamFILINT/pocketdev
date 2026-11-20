# PocketDev — License Inventory

> Working inventory. Verify every item against the exact pinned version before any
> public release. Not legal advice.

## PocketDev's own code

License not yet chosen. Decision deferred until the license implications of bundling
Termux components (GPLv3) and OpenCode Bionic are analyzed. Do not label the app with
a license until that review is complete.

## Current dependencies (Milestone 1)

| Component | License | Obligations |
| --- | --- | --- |
| AndroidX / Jetpack (core, lifecycle, activity, navigation, Compose, Material 3) | Apache-2.0 | Include license notice in release attributions |
| Kotlin standard library / compiler | Apache-2.0 | Include notice |
| JUnit 4 | Eclipse Public License 1.0 | Test-only; not redistributed in APK |
| Gradle / AGP | Apache-2.0 | Build-time only |

## Planned components (audited in `docs/TERMUX_AUDIT.md`, Milestone 2)

| Component | License (stated by upstream) | PocketDev posture |
| --- | --- | --- |
| Termux app (`:app` module) | GPLv3-only | **Not reused** — no app fork (ADR-0001) |
| `termux-shared` default | MIT | Not linked; only copied if a file is proven necessary |
| `termux-shared` → `com/termux/shared/termux/*` | **GPLv3-only** (exceptions: `TermuxConstants.java`, `TermuxPropertyConstants.java` = MIT) | **Excluded** — copying would make PocketDev a GPLv3 combined work |
| `termux-shared` → `file/filesystem/*` | GPLv2 + Classpath exception | Excluded unless required |
| `terminal-emulator`, `terminal-view` | Apache-2.0 | **Approved for reuse** (ADR-0001) with attribution |
| `termux-exec` | Apache-2.0 (verify at pinned commit) | Candidate for the exec/LD_PRELOAD chain; vendor only with header preservation |
| Termux bootstrap archives (release assets) | Built from individually licensed packages | **Downloaded at first run, never bundled in the APK**; pin tag + SHA-256; not redistributed by PocketDev |
| Individual Termux packages (installed on demand) | Per-package | Installed by the user, never silently (prompt §8.6); repository strategy deferred to ADR-0002 |
| OpenCode Bionic | MIT (per upstream README) | Audit bundled third-party components independently |
| Upstream OpenCode | MIT (per upstream) | Verify at pinned commit |
| Bun Termux | MIT (verify) | Verify at pinned commit |
| OpenTUI / AndroidTUI | Verify upstream | Verify at pinned commit |
| Bionic PTY component | Verify upstream | Verify at pinned commit |

## Distribution implications

- **No GPLv3 file is planned for reuse**, so PocketDev's own code can remain under a
  permissive license if desired; the choice stays open until ADR-0002 (Milestone 3)
  confirms the runtime strategy. Copying any GPLv3 `termux-shared` file reverses
  this — treat such a copy as a license decision requiring explicit approval.
- Bootstrap binaries are fetched from upstream at runtime and stored in app-private
  storage; PocketDev does not redistribute them in the APK. If the distribution
  model ever changes (bundling), re-run this review — bundled third-party binaries
  carry their own source-availability obligations.
- Using Termux's package mirrors from a non-`com.termux` app conflicts with upstream
  intent (audit §C4) — no repository is configured until ADR-0002 decides this.
- Maintain `THIRD_PARTY_NOTICES.md` generated from this inventory at release time,
  including Apache-2.0 notices for the terminal-emulator/terminal-view files.

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

## Planned components (not yet integrated)

| Component | License (stated by upstream) | Notes |
| --- | --- | --- |
| Termux app | GPLv3-only with listed exceptions | File-level audit required before reuse |
| `termux-shared` | Separate license with file-level exceptions | Audit required |
| Termux bootstrap / packages | Per-package licenses | Inventory each default-installed package |
| OpenCode Bionic | MIT (per upstream README) | Audit bundled third-party components independently |
| Upstream OpenCode | MIT (per upstream) | Verify at pinned commit |
| Bun Termux | MIT (verify) | Verify at pinned commit |
| OpenTUI / AndroidTUI | Verify upstream | Verify at pinned commit |
| Bionic PTY component | Verify upstream | Verify at pinned commit |

## Distribution implications

- If GPLv3 Termux application code is reused/combined, distribution obligations may
  extend to PocketDev's corresponding source. Analyze the exact combination model
  (derivative vs. separate process) with qualified counsel before release.
- Maintain `THIRD_PARTY_NOTICES.md` generated from this inventory at release time.

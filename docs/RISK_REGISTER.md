# PocketDev — Risk Register

| # | Risk | Impact | Likelihood | Mitigation | Status |
| --- | --- | --- | --- | --- | --- |
| R1 | No reliable ARM64 Android environment in GitHub Actions | Bionic runtime never validated end-to-end | High | Feasibility spike (Milestone 2); document coverage gaps honestly; static ELF checks otherwise | Open |
| R2 | Device storage nearly full (~4.5 GB free) | Cannot hold large artifacts locally | Medium | Never build locally; keep only source on device; clean project downloads | Mitigated by policy |
| R3 | FUSE storage quirks (`safe.directory`, symlink semantics) | Git/runtime extraction oddities | Medium | `safe.directory` configured; test extraction on app-private ext4 storage | Partially mitigated |
| R4 | Termux GPLv3 + file-level exceptions complicate licensing | Distribution/legal exposure | Medium | File-level license inventory before redistribution; keep components modular | Open |
| R5 | OpenCode Bionic artifact size / APK size | Installability, update bandwidth | Medium | Measure in CI; hybrid provisioning evaluation (prompt §9.3) | Open |
| R6 | Android background process limits kill runtime/agent | Broken sessions | High | Foreground service only when justified; graceful recovery | Open |
| R7 | AGP/Kotlin/Gradle version drift breaks CI | Build failures | Low | Pinned versions + wrapper checksum; Renovate-style manual review | Mitigated |
| R8 | Emulator tests slow/flaky in CI | Noisy gate, wasted minutes | Medium | Small high-signal matrix; `concurrency` cancellation; diagnostics on failure | Open |
| R9 | Agent misuses filesystem/commands | Data loss | Medium | Path safety in `:core`; permission model (prompt §10.2); confirmations | Partially mitigated |
| R10 | Secret leakage into logs | Credential compromise | Low | `SecretRedaction`; never persist keys yet; no secrets in source | Partially mitigated |

# PocketDev — Risk Register

| # | Risk | Impact | Likelihood | Mitigation | Status |
| --- | --- | --- | --- | --- | --- |
| R1 | No reliable ARM64 Android environment in GitHub Actions | Bionic runtime never validated end-to-end | High | **Measured (Milestone 2, `docs/ARM64_FEASIBILITY.md`):** x86_64 runners reject ARM64 guests (emulator 37.3.10 arch check); `ubuntu-24.04-arm` has no emulator package and no `/dev/kvm`. ARM64 builds are cross-compiled on `x86_64` (native `ubuntu-24.04-arm` builds fail at `aapt2`, x86_64-only); runtime tests run on `x86_64` API 30; ABI-agnostic design + static ELF checks; ARM64 device proof deferred | Open — accepted gap |
| R2 | Device storage nearly full (~4.5 GB free) | Cannot hold large artifacts locally | Medium | Never build locally; keep only source on device; clean project downloads | Mitigated by policy |
| R3 | FUSE storage quirks (`safe.directory`, symlink semantics) | Git/runtime extraction oddities | Medium | `safe.directory` configured; test extraction on app-private ext4 storage | Partially mitigated |
| R4 | Termux GPLv3 + file-level exceptions complicate licensing | Distribution/legal exposure | Medium | File-level license inventory before redistribution; keep components modular | Open |
| R5 | OpenCode Bionic artifact size / APK size | Installability, update bandwidth | Medium | Measure in CI; hybrid provisioning evaluation (prompt §9.3) | Open |
| R6 | Android background process limits kill runtime/agent | Broken sessions | High | Foreground service only when justified; graceful recovery | Open |
| R7 | AGP/Kotlin/Gradle version drift breaks CI | Build failures | Low | Pinned versions + wrapper checksum; Renovate-style manual review | Mitigated |
| R8 | Emulator tests slow/flaky in CI | Noisy gate, wasted minutes | Medium | Small high-signal matrix; `concurrency` cancellation; diagnostics on failure | Open |
| R9 | Agent misuses filesystem/commands | Data loss | Medium | Path safety in `:core`; permission model (prompt §10.2); confirmations | Partially mitigated |
| R10 | Secret leakage into logs | Credential compromise | Low | `SecretRedaction`; never persist keys yet; no secrets in source | Partially mitigated |
| R11 | Android 10+ W^X: targetSdk 36 app cannot `execve()` files in app data | Bootstrap binaries unrunnable | High | `linker64` first-hop + `termux-exec` `LD_PRELOAD` chain (audit §C2); ADR-0002 spike must prove it before any runtime is executed (M3 bootstrap itself is structural only) | Open — spike gated |
| R12 | Stock Termux packages hardcode `/data/data/com.termux` prefix | Runtime breaks under `dev.pocketdev` data dir | High | ADR-0002 (post-M3 spike): runtime path rewriting vs. rebuilt packages (audit §C1) | Open — spike gated |
| R13 | No entitled package repository for a non-`com.termux` app id | `apt install` unavailable or upstream-hostile | Medium | No repository configured until ADR-0002; upstream intent respected (audit §C4) | Open |

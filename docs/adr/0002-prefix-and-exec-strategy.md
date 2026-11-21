# ADR-0002 — Runtime prefix relocation and exec strategy

- **Status:** Accepted (2026-11-21), based on measured CI evidence.
- **Supersedes:** the "deferred" decision in ADR-0001 §prefix.
- **Inputs:** `docs/TERMUX_AUDIT.md` §C1/§C2, `docs/BOOTSTRAP_LAYOUT.md`,
  `docs/ARM64_FEASIBILITY.md`, spike run
  [44876316131](https://github.com/IamFILINT/pocketdev/actions/runs/44876316131).

## Context

The audited Termux bootstrap is built for `/data/data/com.termux/files/usr`.
PocketDev must use `/data/data/dev.pocketdev/files/usr` (audit §C1: no shared
prefix, no hard-coded `com.termux` paths). Two questions had to be answered with
real on-device evidence, not assumptions:

1. **R12 — prefix relocation:** do relocated binaries link and run?
2. **R11 — W^X:** can a `targetSdk 36` app execute files in app-private storage?

The M3 extraction logic already rewrites the bootstrap's 20 absolute
`SYMLINKS.txt` targets to relative links inside the prefix (found during the
spike: extraction initially failed on
`/data/data/com.termux/files/usr/share/termux-keyring/…`).

## Measured evidence (x86_64 API 30 emulator, real pinned bootstrap)

| Check | Result |
| --- | --- |
| Download + SHA-256 + safe extract + health | `Ready`, extraction of 3 779 entries |
| Direct `execve` of `$PREFIX/bin/echo` | **blocked** — `IOException` (W^X confirmed) |
| `/system/bin/linker64 $PREFIX/bin/bash -c 'echo spike-ok'` | **exit 0**, correct output |
| `/system/bin/linker64 $PREFIX/bin/ls -1 $PREFIX/bin` | exit 0 |
| Shebang script exec, no preload | exit 126 — `bad interpreter: Permission denied` |
| Shebang script exec, stock `libtermux-exec-ld-preload.so` | exit 126 — same |
| `apt` / `dpkg` / `pkg` present | yes (package operations not yet exercised) |

Why stock `termux-exec` (2.5.0) does not help: its binary contains the compiled
prefix `/data/data/com.termux/files/usr` and only rewrites execs of files under
`/data/data/com.termux/files`; it also reads `TERMUX__PREFIX`. Our prefix is not
recognized, so shebang execs still reach the kernel as app-data executables.

Relocation itself works: ELF `RUNPATH` is the Termux prefix, but
`LD_LIBRARY_PATH=$PREFIX/lib` takes precedence, and `linker64` loads the
relocated binary correctly.

## Decision

**Keep the PocketDev prefix, launch every binary through the system linker, and
build a prefix-targeted exec preload from upstream `termux-exec` instead of
rewriting package binaries.**

1. **Prefix:** `$PREFIX = /data/data/dev.pocketdev/files/usr` (unchanged layout).
2. **First hop:** every process starts as
   `/system/bin/linker64 $PREFIX/bin/<program>` with the environment from
   `docs/BOOTSTRAP_LAYOUT.md` (`PREFIX`, `HOME`, `PATH`, `TMPDIR`,
   `LD_LIBRARY_PATH`, `TERM`, `LANG`). Proven by the spike.
3. **Exec hook:** vendor `termux-exec-package` at pinned release 2.5.0
   (commit `2cd0ba6b907565484130851f9617523c98df8615`, Apache-2.0) and build it
   with the upstream-supported knobs `TERMUX_APP__PACKAGE_NAME=dev.pocketdev`
   (hence our prefix). The built small `.so` files ship in the APK (our own code
   artifact, **not** a downloaded bootstrap payload) and are copied into
   `$PREFIX/lib` at bootstrap time; `LD_PRELOAD` is set for child processes only.
4. **No binary rewriting.** Text-patching ELF files or hard-coded paths in
   packages is rejected: unverifiable, breaks signatures, and would silently
   corrupt upstream artifacts.
5. **Package manager stays disabled** until the rebuilt preload passes a shebang
   exec test in CI and the repository policy (audit §C4) is decided.

## Rejected alternatives

- **Rename the app to `com.termux`** — collides with Termux, forbidden by the
  prompt and ADR-0001.
- **Bind mount / symlink the Termux prefix** — needs root; excluded.
- **Patch binaries at install time** — see decision 4.
- **Only support scripts invoked as `bash script`** — arbitrary package scripts
  (`#!/usr/bin/env …`) and `exec` calls would still fail, so the runtime would be
  misleadingly "installed".

## Consequences

- `:core` stays Android-free; the Android wiring (`:runtime`: downloader,
  environment builder, linker-hop launcher, preload deployment) is the next
  implementation step.
- The rebuilt preload must be validated by extending `RuntimeSpikeTest` with a
  shebang exec assertion before runtime provisioning is wired into the UI.
- R11 is mitigated for the proven first hop; R12 is resolved for direct binary
  launches and **open** for shebang/`exec` until the rebuilt preload lands.
- ARM64 execution remains unverified in CI (`docs/ARM64_FEASIBILITY.md`); all
  evidence here is x86_64 API 30 and is labelled as such.

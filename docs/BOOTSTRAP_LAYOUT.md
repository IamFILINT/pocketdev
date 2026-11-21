# PocketDev — Bootstrap & Storage Layout Design

> Milestone 2 design (prompt §8.3–§8.5). Implemented in `:core` in Milestone 3
> (`RuntimePaths`, `BootstrapArchive`, `BootstrapHealth`, `BootstrapManager`) and
> wired into `AppContainer`; the Android downloader/exec wiring lands with the
> `:runtime` module. Paths are app-private and never on shared/external storage.

## 1. Directory layout

`<dataDir>` = `/data/data/dev.pocketdev` (legacy path form only; adoptable storage
and secondary users are rejected at bootstrap time — audit §C5).

```text
<dataDir>/
  files/                        # $TERMUX__ROOTFS — the runtime rootfs
    usr/                        # $PREFIX  (bin lib etc include share var tmp)
      bin/ lib/ etc/ var/ tmp/  #   populated by the pinned bootstrap archive
    home/                       # $HOME
      projects/                 #   workspace root (project directories)
      .config/                  #   user configuration
      .cache/                   #   user-level cache
    state/                      # PocketDev registry (projects.tsv, runtime state)
                                #   — kept from Milestone 1; outside $PREFIX so a
                                #   prefix wipe/update never touches project data
  cache/                        # Android cacheDir: partial downloads (resumable);
                                #   system may reclaim it under storage pressure
  code_cache/                   # (Android-managed, not used by us)
```

Mapping to the prompt's illustrative tree (§8.3): `runtime/usr` → `files/usr`
(Termux's own `$ROOTFS` naming), `home/projects` → `files/home/projects`,
`state/` → `files/state`, `downloads/` → `cache/` (temporarily; completed archives
are deleted after extraction), `logs/` → `files/state/logs/`, `backups/` → reserved
(never implement silent overwrite of a healthy runtime — §8.4).

Invariants:

1. **`$PREFIX` is exactly `<dataDir>/files/usr`.** No trailing-slash variance, no
   symlinks in the chain — path-rewrite rules depend on it (audit §C1).
2. **Project data lives outside `$PREFIX`** (`files/state`, `files/home/projects`),
   so runtime reinstall/update can never delete user projects. Project deletion in
   `:core` (`PathSafety`) keeps proving targets are inside `projectsRoot`.
3. **Only the bootstrap manager writes under `$PREFIX`.** The UI never mutates the
   rootfs directly.
4. Migrated in Milestone 3: `files/projects` → `files/home/projects`;
   `files/state` was already canonical. No released users existed, so no registry
   rewrite was needed.

## 2. Bootstrap manager (prompt §8.4 → `:runtime`)

Ordered, resumable, idempotent steps. Each step records progress in
`files/state/runtime.json` so a killed process resumes instead of restarting:

1. **Check** device ABI (`Build.SUPPORTED_ABIS`) and API level; reject unsupported
   combos (`BootstrapManifest.artifactFor` returns `null` → honest `Failed(CHECK)`).
2. **Check** free storage ≥ 4× archive size (archive ~29 MB + extracted rootfs).
3. **Already initialized?** If `runtime.json` says `READY` with a healthy checksum
   re-verification, stop (never overwrite a healthy runtime silently).
4. **Download** the pinned archive into `cache/` with byte-range resume
   (`RuntimeState.Downloading`).
5. **Verify** SHA-256 against the pin **before** touching `$PREFIX`
   (`RuntimeState.Verifying`). Mismatch → delete and fail closed.
6. **Extract** into `files/usr.staging/` (staging dir, same filesystem):
   - reject zip entries with `..`, absolute paths, or NUL (zip-slip);
   - recreate `SYMLINKS.txt` targets only when both ends stay inside `$PREFIX`
     (symlink abuse defence);
   - chmod 0700 for `bin/`, `libexec/`, `lib/apt/apt-helper`, `lib/apt/methods/`
     (mirrors upstream `TermuxInstaller`);
   - apply the ADR-0002 path-relocation rules here, in one auditable place.
7. **Atomic swap**: `rename(staging → usr)`; keep the previous `usr` as
   `usr.prev` until health checks pass, then delete it (update safety).
8. **Environment** (§8.5) — set from the real layout, never guessed:

   | Variable | Value |
   | --- | --- |
   | `PREFIX` | `<dataDir>/files/usr` |
   | `HOME` | `<dataDir>/files/home` |
   | `PATH` | `$PREFIX/bin:$PREFIX/bin/applets:/system/bin` |
   | `TMPDIR` | `$PREFIX/tmp` |
   | `LD_LIBRARY_PATH` | `$PREFIX/lib` (only while ADR-0002 requires it) |
   | `LD_PRELOAD` | `$PREFIX/lib/libtermux-exec.so` (children only) |
   | `TERM` | inherited (`xterm-256color` default) |
   | `LANG` | `en_US.UTF-8` (as provided by the bootstrap) |

   First process spawn from Java goes through
   `/system/bin/linker64 $PREFIX/bin/…` (W^X workaround, audit §C2); every
   subsequent spawn relies on the `LD_PRELOAD` hook.

9. **Package metadata init** — deferred: no repository is configured until
   ADR-0002 decides the package-source strategy (audit §C4). Shipping a broken
   `apt` pointing at mirrors we are not entitled to use would be dishonest.
10. **Health checks** — spawn `$PREFIX/bin/bash -lc 'echo ok'` and one coreutil
    through the real chain; require expected output (`RuntimeState.Ready` only after
    all pass).
11. **Record** bootstrap version/tag + checksums + timestamp in
    `files/state/runtime.json`.
12. **Mark complete** only after 10 passes; any failure →
    `RuntimeState.Failed(stage, message)` with the stage from
    `RuntimeState.Stage` (CHECK…UPDATE), which the dashboard already renders.

## 3. Update policy

- Updates are explicit user actions (`RuntimeState.Updating`), never automatic.
- New archive is downloaded and verified beside the old runtime; swap only after
  the new rootfs passes health checks; rollback = rename `usr.prev` back.
- Project data (`files/state`, `files/home/projects`) is never part of an update.

## 4. Resolved by ADR-0002 (2026-11-21)

- **Stock binaries under our prefix:** proven via `linker64` + `LD_LIBRARY_PATH`
  (`bash`, `ls` exit 0 on the API 30 emulator); direct `execve` is blocked by
  W^X. Shebang exec needs a prefix-built `termux-exec` preload — decided.
- **Relocation rules:** no binary rewriting; absolute `SYMLINKS.txt` targets are
  rewritten to relative links at extraction (implemented in `BootstrapArchive`).
- **Still open:** `apt install` / repository policy (audit §C4) remains deferred
  until the rebuilt preload is verified in CI.

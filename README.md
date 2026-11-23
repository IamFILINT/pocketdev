# PocketDev

**A complete, AI-powered development workstation in your pocket.**

PocketDev is a native Android application that lets you develop, run, inspect, and
version-control real software projects directly on an Android device — with a real
terminal, an integrated Bionic runtime, a code editor, Git, package management, and an
AI coding agent (OpenCode Bionic).

> Status: Milestone 4 — the pinned OpenCode Bionic agent can be provisioned and is
> supervised as a real child process. `opencode serve` is launched loopback-only,
> its URL and per-run password are captured from the process output, and a typed
> API client talks to it. The runtime bootstrap (`:core` + `:runtime`) is pinned,
> checksum-verified, safely extracted, resumable, and its execution is health-checked
> through the linker hop. The UI can install the runtime and agent and render live
> state. Remaining for M4/M5: the SSE event stream into UI state (M5) and an
> end-to-end run on a physical ARM64 device (accepted CI gap).

[![verify](https://github.com/IamFILINT/pocketdev/actions/workflows/verify.yml/badge.svg)](https://github.com/IamFILINT/pocketdev/actions/workflows/verify.yml)
[![android-emulator](https://github.com/IamFILINT/pocketdev/actions/workflows/android-emulator.yml/badge.svg)](https://github.com/IamFILINT/pocketdev/actions/workflows/android-emulator.yml)

## Progress

| Milestone | State |
| --- | --- |
| 0 — Discovery and CI baseline | Done |
| 1 — Android foundation | Done |
| 2 — Runtime architecture and ARM64 feasibility | Done (`docs/ARM64_FEASIBILITY.md`) |
| 3 — Runtime bootstrap | Done (`:core` logic + `:runtime` wiring) |
| 4 — OpenCode Bionic provisioning | Done: pin, verified installer, loopback supervision, API client (ADR-0003/0004) |
| 5 — AI workspace | Done: SSE events → UI, sessions, prompts, provider/model settings, permission prompts |

## Principles

- **One APK**, no root, no PRoot/Chroot, no separate Termux app, no remote computer.
- **GitHub Actions is the build & test machine.** The phone is the lightweight coding
  and control surface. Builds/tests never run on the phone.
- **Local-first.** Core workspace works without a cloud account; AI providers may need
  user-supplied API keys.
- **Pin everything that runs.** Bootstrap and agent artifacts are fetched on first run
  and verified against pinned SHA-256 digests; never `latest`.

## What works today

- Runtime provisioning: resumable download, checksum-gated extraction, journaled
  rollback, real execution health check (`bash`, coreutils) through
  `/system/bin/linker64`.
- Agent provisioning: pinned OpenCode Bionic `v2.3.2` (aarch64, API 29+), resumable
  download, size + SHA-256 verification, atomic replace.
- Agent supervision: `opencode serve --hostname 127.0.0.1`, real
  `Starting`/`Listening`/`Failed`/`Exited` state, stdout drained, child reaped.
- Agent API: HTTP Basic auth (username `opencode` + captured per-run password),
  `/api/info`, session list/create, prompt.

See [`docs/OPENCODE_BIONIC.md`](docs/OPENCODE_BIONIC.md) for the integration contract
and [`docs/TESTING.md`](docs/TESTING.md) for exactly what CI proves (and does not).

## Status

See [`docs/IMPLEMENTATION_ROADMAP.md`](docs/IMPLEMENTATION_ROADMAP.md) for the live
roadmap, [`docs/RISK_REGISTER.md`](docs/RISK_REGISTER.md) for open risks and
`SESSION_NOTES.md` for working notes. [`docs/PROJECT_AUDIT.md`](docs/PROJECT_AUDIT.md)
is a historical Milestone 0 snapshot. [`docs/CI_CD.md`](docs/CI_CD.md) describes the
build/test/release pipeline.

## License

Not chosen yet. The inventory (`docs/LICENSE_INVENTORY.md`) shows no GPLv3
component is required by the decided strategy (ADR-0001), but the final license
decision is still open.
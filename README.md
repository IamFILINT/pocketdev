# PocketDev

**A complete, AI-powered development workstation in your pocket.**

PocketDev is a native Android application that lets you develop, run, inspect, and
version-control real software projects directly on an Android device — with a real
terminal, an integrated Bionic runtime, a code editor, Git, package management, and
an AI coding agent (OpenCode Bionic).

> Status: Milestone 3 + Android runtime wiring — bootstrap logic is pinned,
> checksum-verified, safely extracted and resumable; the new `:runtime` module
> adds the execution environment, linker-hop launcher and real execution health
> checks (proven by the ADR-0002 spike). The dashboard can install the runtime and
> renders live bootstrap state; the OpenCode controller is not implemented yet, so
> the app is not yet usable.

[![verify](https://github.com/IamFILINT/pocketdev/actions/workflows/verify.yml/badge.svg)](https://github.com/IamFILINT/pocketdev/actions/workflows/verify.yml)
[![android-emulator](https://github.com/IamFILINT/pocketdev/actions/workflows/android-emulator.yml/badge.svg)](https://github.com/IamFILINT/pocketdev/actions/workflows/android-emulator.yml)

## Progress

| Milestone | State |
| --- | --- |
| 0 — Discovery and CI baseline | Done |
| 1 — Android foundation | Done |
| 2 — Runtime architecture and ARM64 feasibility | Done (`docs/ARM64_FEASIBILITY.md`) |
| 3 — Runtime bootstrap | Done in `:core`; `:runtime` wiring landed |
| 4 — OpenCode Bionic provisioning | In progress (runtime wiring landed; controller next) |

## Principles

- **One APK**, no root, no PRoot/Chroot, no separate Termux app, no remote computer.
- **GitHub Actions is the build & test machine.** The phone is the lightweight coding
  and control surface. Builds/tests never run on the phone.
- **Local-first.** Core workspace works without a cloud account; AI providers may need
  user-supplied API keys.

## Status

See [`docs/IMPLEMENTATION_ROADMAP.md`](docs/IMPLEMENTATION_ROADMAP.md) for the live
roadmap, [`docs/RISK_REGISTER.md`](docs/RISK_REGISTER.md) for open risks and
`SESSION_NOTES.md` for working notes. [`docs/PROJECT_AUDIT.md`](docs/PROJECT_AUDIT.md)
is a historical Milestone 0 snapshot.

## License

Not chosen yet. The inventory (`docs/LICENSE_INVENTORY.md`) shows no GPLv3
component is required by the decided strategy (ADR-0001), but the final license
decision is still open.
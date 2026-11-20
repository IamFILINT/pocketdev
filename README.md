# PocketDev

**A complete, AI-powered development workstation in your pocket.**

PocketDev is a native Android application that lets you develop, run, inspect, and
version-control real software projects directly on an Android device — with a real
terminal, an integrated Bionic runtime, a code editor, Git, package management, and
an AI coding agent (OpenCode Bionic).

> Status: Milestone 1 — Android foundation (Compose shell, `:core` domain module,
> CI build/test/lint, emulator launch + navigation tests). Not yet usable.

## Principles

- **One APK**, no root, no PRoot/Chroot, no separate Termux app, no remote computer.
- **GitHub Actions is the build & test machine.** The phone is the lightweight coding
  and control surface. Builds/tests never run on the phone.
- **Local-first.** Core workspace works without a cloud account; AI providers may need
  user-supplied API keys.

## Status

See [`docs/PROJECT_AUDIT.md`](docs/PROJECT_AUDIT.md) for the current audit and
`SESSION_NOTES.md` for working notes.

## License

To be determined after the license inventory (see `docs/LICENSE_INVENTORY.md`, pending).
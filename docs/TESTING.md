# PocketDev — Testing

> Maintained by the coding agent. The rule: **every build, test, lint, and analysis
> run happens on GitHub Actions; never on the Android phone** (master prompt §5, §21).

## Layers

| Layer | Where | What it proves | What it does **not** prove |
| --- | --- | --- | --- |
| JVM unit tests (`:core`, `:runtime`) | `verify.yml`, `ubuntu-latest`, JDK 17 | Pure logic: pins, checksums, archive safety, journal/resume, path safety, state machines, serve-output parsing, JSON reading, API client auth/encoding, supervision state transitions | Real Android/Bionic behavior |
| Android lint + `assembleDebug` | `verify.yml`, `ubuntu-latest` | Compiles `:app`/`:runtime`/`:core`, lint gates the debug build | Runtime correctness on device |
| Instrumentation (x86_64 API 30) | `android-emulator.yml`, KVM, `ReactiveCircus/android-emulator-runner` (pinned) | App launch, navigation, Compose UI basics | Any ARM64/Bionic behavior |
| Static native-artifact checks | planned `native-runtime-ci.yml` | Pinned artifact identity (size, SHA-256, ELF machine/class), honest static validation of the aarch64 agent/bootstrap on an x86_64 runner | Execution |
| ARM64 device run | not in CI | — | The accepted gap (risk R1) |

## What runs on a normal PR

`verify.yml` (required gate):

```
./gradlew --no-daemon --stacktrace lintDebug testDebugUnitTest assembleDebug
```

Artifacts: unit-test reports, lint report, debug APK.

`android-emulator.yml`: boots an x86_64 API 30 AVD and runs
`connectedDebugAndroidTest` (currently navigation tests).

## What is deliberately *not* claimed

- **ARM64/Bionic execution is not tested in CI.** GitHub-hosted runners cannot build
  or boot an ARM64 Android emulator with the stock toolchain
  (`docs/ARM64_FEASIBILITY.md`, runs `44862773422`, `44866937289`).
- **The OpenCode server is not run in CI.** `OpenCodeServerTest` drives the *same*
  supervision logic against `/bin/sh` scripts, and `OpenCodeApiClientTest` drives the
  *same* client against a real loopback `com.sun.net.httpserver.HttpServer`. Neither
  proves the aarch64 binary boots on a device.
- **Instrumentation tests run on x86_64**, so they say nothing about the runtime ABI.

## Test inventory (current)

| Area | Test file(s) |
| --- | --- |
| Pins / artifact metadata | `core/.../agent/OpenCodePinsTest.kt` |
| Verified installer (checksum/resume/fail-closed) | `core/.../agent/AgentInstallerTest.kt` |
| Serve-output parser | `core/.../agent/OpenCodeServeOutputParserTest.kt` |
| JSON reader | `core/.../json/JsonTest.kt` |
| API client (auth, body, path escaping, real loopback server) | `core/.../agent/OpenCodeApiClientTest.kt` |
| Bootstrap (checksums, archive, journal, manager, health) | `core/.../runtime/*Test.kt` |
| Runtime env / preload / launcher / ABI | `runtime/.../*Test.kt` |
| Server supervision state machine | `runtime/.../OpenCodeServerTest.kt` |
| Compose navigation / runtime spike (opt-in) | `app/src/androidTest/...` |

## Running tests

Do not run them on the phone. Push a branch or open a PR; use `gh` to watch:

```bash
gh run list --branch <branch>
gh run view <run-id>
gh run view <run-id> --log-failed
```

A milestone is not complete until the relevant workflow has actually run on
GitHub-hosted infrastructure and passed (master prompt §5.8).

## CI style notes (from Termux)

PocketDev's pipeline follows patterns used by Termux's own CI: fast JVM unit tests
separate from APK assembly, a pinned Gradle-wrapper validation, versioned artifact
names with `sha256sums`, least-privilege `permissions`, and static validation of
native payloads on x86_64 runners rather than pretending an emulator proves ARM64.
See `docs/CI_CD.md`.
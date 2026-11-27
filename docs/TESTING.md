# PocketDev — Testing

> The binding policy behind this document: `docs/TESTING_POLICY.md` (mandatory testing & continuous verification).

> Maintained by the coding agent. The rule: **every build, test, lint, and analysis
> run happens on GitHub Actions; never on the Android phone** (master prompt §5, §21).

## Layers

| Layer | Where | What it proves | What it does **not** prove |
| --- | --- | --- | --- |
| JVM unit tests (`:core`, `:runtime`, `:app`) | `verify.yml`, `ubuntu-latest`, JDK 17 | Pure logic: pins, checksums, archive safety, journal/resume, path safety, state machines, launch-plan argv/env/cwd, agent model wire shape, redacted runtime config/credential env, serve-output parsing, ANSI terminal buffer, PTY session state/exit decoding, JSON reading, API client auth/encoding, supervision state transitions | Real Android/Bionic behavior |
| Android lint + `assembleDebug` | `verify.yml`, `ubuntu-latest` | Compiles `:app`/`:runtime`/`:core`, lint gates the debug build | Runtime correctness on device |
| Instrumentation (x86_64 API 29 and 36) | `android-emulator.yml`, KVM, `ReactiveCircus/android-emulator-runner` (pinned) | App launch, navigation, Compose UI basics, real Android loopback HTTP, provider/model selection, Keystore-backed API-key status/save/clear | Bionic PTY sessions and any ARM64 behavior |
| Opt-in Bionic execution (x86_64 API 30) | `runtime-spike.yml`, manual dispatch | Provisioning, linker64 execution, termux-exec behavior, real `$PREFIX/bin/bash -i` PTY session | ARM64 execution; required-gate enforcement |
| Static native-artifact checks | `native-runtime-ci.yml`, x86_64 runner | Pinned artifact identity (size, SHA-256, ELF machine/class), honest static validation of the aarch64 agent/bootstrap | Execution |
| ARM64 device run | not in CI | — | The accepted gap (risk R1) |

## What runs on a normal PR

`verify.yml` (required gate):

```
./gradlew --no-daemon --stacktrace lintDebug testDebugUnitTest assembleDebug
```

Artifacts: unit-test reports, lint report, debug APK.

`android-emulator.yml`: boots x86_64 API 29 and API 36 AVDs and runs
`connectedDebugAndroidTest` (navigation, Files, loopback HTTP, provider/model
selection, Keystore-backed API-key persistence, and workspace selected-model flows),
excluding the opt-in PTY classes that run only in `runtime-spike.yml`.

`runtime-spike.yml` is manual, not required: it provisions the pinned x86_64
bootstrap on an API 30 emulator, runs `RuntimeSpikeTest`, and runs
`TerminalViewModelTest` and `PtyReliabilityTest` with `bionicPty=true` to prove the
real linker-hop Bionic bash PTY path, exit-status reporting, launch-failure
propagation, and repeated/concurrent session lifecycle behavior. The PTY tests are
gated because they download the full bootstrap and prove x86_64 execution only.

## What is deliberately *not* claimed

- **ARM64/Bionic execution is not tested in CI.** GitHub-hosted runners cannot build
  or boot an ARM64 Android emulator with the stock toolchain
  (`docs/ARM64_FEASIBILITY.md`, runs `44862773422`, `44866937289`).
- **The OpenCode server is not run in CI.** `OpenCodeServerTest` drives the *same*
  supervision logic against `/bin/sh` scripts, and `OpenCodeApiClientTest` drives the
  *same* client against a real loopback `com.sun.net.httpserver.HttpServer`. Neither
  proves the aarch64 binary boots on a device.
- **Instrumentation tests run on x86_64 API 29 and API 36**, so they say nothing about the runtime ABI.
- **The OpenCode env-auth handshake is not proven on a device.** Tests prove the
  `OPENCODE_CONFIG_CONTENT` placeholder, `POCKETDEV_API_KEY` env injection, redacted
  logging, and session/model wire shapes; they do not prove a real provider response.

## Test inventory (current)

| Area | Test file(s) |
| --- | --- |
| Pins / artifact metadata | `core/.../agent/OpenCodePinsTest.kt` |
| Verified installer (checksum/resume/fail-closed) | `core/.../agent/AgentInstallerTest.kt` |
| Serve-output parser | `core/.../agent/OpenCodeServeOutputParserTest.kt` |
| JSON reader | `core/.../json/JsonTest.kt` |
| API client (auth, body, path escaping, real loopback server) | `core/.../agent/OpenCodeApiClientTest.kt` |
| Agent model selection and session/model endpoints | `core/.../settings/AgentSettingsTest.kt`, `core/.../agent/OpenCodeApiClientTest.kt` |
| API client on Android cleartext loopback | `app/src/androidTest/.../LoopbackHttpTest.kt` |
| Bootstrap (checksums, archive, journal, manager, health) | `core/.../runtime/*Test.kt` |
| Runtime env / preload / launcher / launch plan / ABI / redacted agent config | `runtime/.../*Test.kt`, `runtime/.../RuntimeAgentConfigTest.kt` |
| Server supervision state machine | `runtime/.../OpenCodeServerTest.kt` |
| ANSI terminal buffer | `core/.../terminal/AnsiBufferTest.kt` |
| Editor text codec, buffer, search, syntax, formatting | `core/.../editor/TextFileCodecTest.kt`, `EditorBufferTest.kt`, `TextSearchTest.kt`, `SyntaxHighlighterTest.kt`, `EditorFormatterTest.kt` |
| Editor workspace IO (BOM/newline round-trip, stale signature, binary/large files) | `core/.../workspace/WorkspaceFilesEditorTest.kt` |
| PTY session state and exit decoding | `app/src/test/.../terminal/TerminalSessionTest.kt` |
| Project-context binding (agent, terminal, files) | `app/src/androidTest/.../AgentWorkspaceViewModelTest.kt`, `FilesViewModelTest.kt`, `TerminalViewModelTest.kt` |
| Provider/model settings and Keystore API-key status/save/clear | `app/src/androidTest/.../SettingsViewModelTest.kt` |
| Workspace selected-model propagation and active-session switching | `app/src/androidTest/.../AgentWorkspaceViewModelTest.kt` |
| Filesystem/SAF IO dispatcher, cancellation, progress, explicit failure | `core/.../workspace/WorkspaceFilesTest.kt`, `app/src/androidTest/.../FilesViewModelTest.kt` |
| Bionic terminal sessions (opt-in) | `app/src/androidTest/.../TerminalViewModelTest.kt` |
| PTY reliability, exit status, launch failure, stress (opt-in) | `app/src/androidTest/.../PtyReliabilityTest.kt` |
| Navigation | `app/src/androidTest/.../NavigationTest.kt` |
| Editor ViewModel (tabs, save/dirty, external-change conflict, search/replace, format) | `app/src/androidTest/.../EditorViewModelTest.kt` |
| Editor UI and Files → editor integration | `app/src/androidTest/.../EditorUiTest.kt` |
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
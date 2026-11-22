# PocketDev — Test Coverage Matrix

Maintained per the mandatory testing policy (section 10). Every row points at a real file and a real test. Status reflects the last verified GitHub Actions run, not intent. `Verified` = the test exists and passed in CI on the current `main`. `Pending` = planned/missing; see `docs/TEST_AUDIT.md`.

## Core (`:core`)

| Component | Expected behavior | Test type | Test location | Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| BootstrapPins | validated metadata, https, digest shape | unit | `core/.../runtime/BootstrapPinsTest.kt` | verify | Verified |
| BootstrapArtifact | validation, supports() | unit | `core/.../runtime/BootstrapArtifactTest.kt` | verify | Verified |
| BootstrapChecksums | digest verification | unit | `core/.../runtime/BootstrapChecksumsTest.kt` | verify | Verified |
| BootstrapArchive | zip-slip/symlink/traversal/caps | unit | `core/.../runtime/BootstrapArchiveTest.kt` | verify | Verified |
| BootstrapJournal | resumable state | unit | `core/.../runtime/BootstrapJournalTest.kt` | verify | Verified |
| BootstrapManager | install/resume/rollback | unit | `core/.../runtime/BootstrapManagerTest.kt` | verify | Verified |
| BootstrapHealth | structural checks | unit | `core/.../runtime/BootstrapHealthTest.kt` | verify | Verified |
| HttpBootstrapDownloader | HTTPS-only, ranges | unit | `core/.../runtime/HttpBootstrapDownloaderTest.kt` | verify | Verified |
| RuntimePaths | layout, ensure dirs | unit | `core/.../runtime/RuntimePathsTest.kt` | verify | Verified |
| RuntimeState machine | transitions | unit | `core/.../runtime/RuntimeStateMachineTest.kt` | verify | Verified |
| OpenCodePins | validated metadata | unit | `core/.../agent/OpenCodePinsTest.kt` | verify | Verified |
| AgentInstaller | checksum/resume/fail-closed | unit | `core/.../agent/AgentInstallerTest.kt` | verify | Verified |
| OpenCodeServeOutputParser | readiness lines, ANSI, loopback | unit | `core/.../agent/OpenCodeServeOutputParserTest.kt` | verify | Verified |
| Json | parse/escapes/numbers | unit | `core/.../json/JsonTest.kt` | verify | Verified |
| OpenCodeApiClient | auth, body, path escape, real server | unit | `core/.../agent/OpenCodeApiClientTest.kt` | verify | Verified |
| PathSafety | traversal/NUL/segments | unit | `core/.../workspace/PathSafetyTest.kt` | verify | Verified |
| ProjectStore | CRUD/registry | unit | `core/.../workspace/ProjectStoreTest.kt` | verify | Verified |
| ProjectNameValidator | name rules | unit | `core/.../validation/ProjectNameValidatorTest.kt` | verify | Verified |
| SecretRedaction | redaction | unit | `core/.../security/SecretRedactionTest.kt` | verify | Verified |
| TaskState machine | transitions | unit | `core/.../ai/TaskStateMachineTest.kt` | verify | Verified |
| OpenCodeServeOutput (data) | URL/password model | unit | (covered by parser test) | verify | Verified |
| Project (data) | model | unit | (covered by ProjectStore test) | verify | Verified |
| RuntimeState (data) | model | unit | (covered by state-machine test) | verify | Verified |
| HttpAgentDownloader | delegator | unit | (covered transitively by HttpBootstrapDownloaderTest) | verify | Verified |

## Runtime (`:runtime`)

| Component | Expected behavior | Test type | Test location | Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| RuntimeController.abiFor | ABI selection | unit | `runtime/.../RuntimeControllerAbiTest.kt` | verify | Verified |
| RuntimeEnvBuilder | env overrides | unit | `runtime/.../RuntimeEnvBuilderTest.kt` | verify | Verified |
| RuntimeLauncher | linker-hop spawn | unit | `runtime/.../RuntimeLauncherTest.kt` | verify | Verified |
| RuntimePreloadDeployer | preload deploy | unit | `runtime/.../RuntimePreloadDeployerTest.kt` | verify | Verified |
| OpenCodeServer | supervision state machine | unit | `runtime/.../OpenCodeServerTest.kt` | verify | Verified |
| ProcessLauncher | interface | unit | (trivial; covered by OpenCodeServerTest) | verify | Verified |
| OpenCodeController | provision/start/stop | unit | missing (Android-bound) | - | Pending |
| RuntimeExecutionHealthCheck | real Bionic exec | integration | missing (device-bound) | - | Pending |
| RuntimeController (full) | provisioning orchestration | unit | missing (Android-bound) | - | Pending |

## App (`:app`)

| Component | Expected behavior | Test type | Test location | Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| Navigation | launch + nav | instrumentation | `app/src/androidTest/.../NavigationTest.kt` | android-emulator | Verified |
| Runtime spike | Bionic exec on x86_64 | instrumentation | `app/src/androidTest/.../RuntimeSpikeTest.kt` | runtime-spike | Verified (manual) |
| DashboardViewModel | provisioning guards | instrumentation | `app/src/androidTest/.../DashboardViewModelTest.kt` | android-emulator | Verified (PR #22) |
| ProjectsViewModel | create/delete/validate | instrumentation | `app/src/androidTest/.../ProjectsViewModelTest.kt` | android-emulator | Verified (PR #19) |
| SettingsViewModel | theme/api key draft | instrumentation | `app/src/androidTest/.../SettingsViewModelTest.kt` | android-emulator | Verified (PR #22) |
| AppContainer | object graph | unit | missing (Context-bound) | - | Pending |
| Compose screens | rendering | instrumentation | missing | - | Pending |

## Native payloads (static, x86_64 runner)

| Artifact | Expected | Test type | Location | Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| OpenCode Bionic opencode (aarch64) | size, SHA-256, ELF64 AArch64 | static | `native-runtime-ci.yml` | native-runtime-ci | Verified (run 45006056315) |
| Termux bootstrap-aarch64.zip | size, SHA-256, zip integrity | static | `native-runtime-ci.yml` | native-runtime-ci | Verified (run 45006056315) |

## Known unverified (honest)

- ARM64 device execution of the Bionic runtime (risk R1) — no runner available.
- OpenCode server end-to-end on a device — supervision/client tested against scripts/loopback server only.
- App ViewModels and Compose screens — depend on Android Context; not unit-tested yet.
- `OpenCodeController` and `RuntimeController` orchestration — Android-bound; covered indirectly by `:core`/`:runtime` unit tests and manual device runs.
EOF
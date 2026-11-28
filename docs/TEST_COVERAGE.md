# PocketDev — Test Coverage Matrix

Maintained per the mandatory testing policy (section 10). Every row points at a real file and a real test. Status reflects the last verified GitHub Actions run, not intent. `Verified` = the test exists and passed in CI on the current `main` or the current PR head. `Pending` = planned/missing; see `docs/TEST_AUDIT.md`.

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
| AgentSettings | non-secret provider/model validation and `modelSelection` | unit | `core/.../settings/AgentSettingsTest.kt` | verify | Verified (PR #39) |
| AgentInstaller | checksum/resume/fail-closed | unit | `core/.../agent/AgentInstallerTest.kt` | verify | Verified |
| OpenCodeServeOutputParser | readiness lines, ANSI, loopback | unit | `core/.../agent/OpenCodeServeOutputParserTest.kt` | verify | Verified |
| Json | parse/escapes/numbers | unit | `core/.../json/JsonTest.kt` | verify | Verified |
| OpenCodeApiClient | auth, body, path escape, real server | unit | `core/.../agent/OpenCodeApiClientTest.kt` | verify | Verified (PR #39) |
| AgentModelSelection + model endpoints | create-session `model` object and `POST /api/session/{id}/model` | unit | `core/.../agent/OpenCodeApiClientTest.kt` | verify | Verified (PR #39) |
| OpenCodeApiClient.replyPermission | POST decision to session permission endpoint | unit | `core/.../agent/OpenCodeApiClientTest.kt` | verify | Verified (PR #36) |
| PermissionRequest + PermissionDecision | parse `permission.asked`, reply enum values | unit | `core/.../agent/PermissionRequestTest.kt` | verify | Verified (PR #36) |
| ServerSentEvent + parser | SSE parsing edge cases | unit | `core/.../agent/ServerSentEventTest.kt` | verify | Verified (PR #24) |
| ServerSentEvent.type | SSE event name / JSON payload fallback | unit | `core/.../agent/ServerSentEventTest.kt` | verify | Verified (PR #36) |
| OpenCodeApiClient.streamEvents | streaming delivery + early stop | unit | `core/.../agent/OpenCodeApiClientTest.kt` | verify | Verified (PR #24) |
| OpenCodeSessionApi | seam implemented by client, faked in VM tests | unit | (interface; client verified above) | verify | Verified (PR #32) |
| PathSafety | traversal/NUL/segments, `resolveWithin`, `resolveWithinNoFollow` symlink file/directory rejection | unit | `core/.../workspace/PathSafetyTest.kt` | verify | Verified (run 46076579464) |
| WorkspaceFiles | list/create/rename/copy/move/delete/search/preview, symlink escape, recursive progress, cancellation, partial failure | unit | `core/.../workspace/WorkspaceFilesTest.kt` | verify | Verified (run 45353953532) |
| FileEntry | kind detection and hidden flag | unit | `core/.../workspace/WorkspaceFilesTest.kt` | verify | Verified (PR #37) |
| TextFileCodec | strict UTF-8/BOM decode, LF/CRLF/CR normalization and byte-for-byte mixed-newline fidelity, binary/NUL rejection, truncation flag | unit | `core/.../editor/TextFileCodecTest.kt` | verify | Verified (run 46076579464) |
| EditorBuffer | replace, incremental line map, undo/redo, exact `markSaved(revision, text)` dirty guard, line/column helpers | unit | `core/.../editor/EditorBufferTest.kt` | verify | Verified (run 46076579464) |
| TextSearch | find all/next, replace all, ignore-case, whole-word, supplementary Unicode offsets | unit | `core/.../editor/TextSearchTest.kt` | verify | Verified (run 46076579464) |
| SyntaxHighlighter | Kotlin/JSON/Python keywords, comments, strings, numbers, annotations, punctuation, token bounds | unit | `core/.../editor/SyntaxHighlighterTest.kt` | verify | Verified (run 46076579464) |
| EditorFormatter | language-aware whitespace, tabs, newline normalization; Markdown hard breaks and string/comment preservation | unit | `core/.../editor/EditorFormatterTest.kt` | verify | Verified (run 46076579464) |
| EditorDraftStore | versioned round-trip, stable hashed names, oversize rejection, unreadable raw-byte retention | unit | `core/.../editor/EditorDraftStoreTest.kt` | verify | Verified (run 46076579464) |
| EditorPerformanceBaselineTest | deterministic 10 KiB–1 MiB JVM fixtures, nine operations, p50/p95 records, correctness assertions | unit/benchmark | `core/.../editor/EditorPerformanceBaselineTest.kt` | verify | Verified (run 46076579464) |
| WorkspaceFiles editor text IO | encoding/newline round-trip, content-signature stale guard, same-size/timestamp replacement detection, atomic staging/commit failure cleanup, serialized writes, symlink refusal, binary/large files | unit | `core/.../workspace/WorkspaceFilesEditorTest.kt` | verify | Verified (run 46076579464) |
| AnsiBuffer | ANSI SGR, cursor, erase, scrollback, resize, OSC, plain text | unit | `core/.../terminal/AnsiBufferTest.kt` | verify | Verified (PR #38) |
| ProjectStore | CRUD/registry | unit | `core/.../workspace/ProjectStoreTest.kt` | verify | Verified |
| ProjectNameValidator | name rules | unit | `core/.../validation/ProjectNameValidatorTest.kt` | verify | Verified |
| SecretRedaction | redaction | unit | `core/.../security/SecretRedactionTest.kt` | verify | Verified |
| TaskState machine | transitions | unit | `core/.../ai/TaskStateMachineTest.kt` | verify | Verified |
| TaskStateMapper | SSE event -> TaskState, JSON payload event type | unit | `core/.../ai/TaskStateMapperTest.kt` | verify | Verified (PR #36) |
| OpenCodeServeOutput (data) | URL/password model | unit | (covered by parser test) | verify | Verified |
| Project (data) | model | unit | (covered by ProjectStore test) | verify | Verified |
| RuntimeState (data) | model | unit | (covered by state-machine test) | verify | Verified |
| HttpAgentDownloader | delegator | unit | (covered transitively by HttpBootstrapDownloaderTest) | verify | Verified |

## Runtime (`:runtime`)

| Component | Expected behavior | Test type | Test location | Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| RuntimeController.abiFor | ABI selection | unit | `runtime/.../RuntimeControllerAbiTest.kt` | verify | Verified |
| AgentRuntime | seam implemented by OpenCodeController, faked in workspace tests | unit | (interface; controller covered indirectly) | verify | Verified (PR #39) |
| RuntimeAgentConfig | selected model -> `OPENCODE_CONFIG_CONTENT`, `{env:POCKETDEV_API_KEY}` placeholder, credential env | unit | `runtime/.../RuntimeAgentConfigTest.kt` | verify | Verified (PR #39) |
| RuntimeEnvBuilder | env overrides and agent config/credential injection | unit | `runtime/.../RuntimeEnvBuilderTest.kt` | verify | Verified (PR #39) |
| RuntimeLauncher | linker-hop spawn and redacted launch-plan reporting | unit | `runtime/.../RuntimeLauncherTest.kt` | verify | Verified (PR #39) |
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
| Runtime spike | Bionic exec on x86_64 | instrumentation | `app/src/androidTest/.../RuntimeSpikeTest.kt` | runtime-spike | Verified (runs 45329524820, 45336872176, 45347183919) |
| DashboardViewModel | provisioning guards | instrumentation | `app/src/androidTest/.../DashboardViewModelTest.kt` | android-emulator | Verified (PR #22) |
| ProjectsViewModel | create/delete/validate | instrumentation | `app/src/androidTest/.../ProjectsViewModelTest.kt` | android-emulator | Verified (PR #19) |
| SettingsViewModel | theme/API key status, keystore save/clear, blank rejection | instrumentation | `app/src/androidTest/.../SettingsViewModelTest.kt` | android-emulator | Verified (PR #39) |
| SettingsViewModel provider/model | persistence + invalid rejection | instrumentation | `app/src/androidTest/.../SettingsViewModelTest.kt` | android-emulator | Verified (PR #39) |
| SettingsScreen provider/model and key controls | fields rendered, secure key status/save/clear | instrumentation | `app/src/androidTest/.../NavigationTest.kt` | android-emulator | Verified (PR #39) |
| AgentSessionViewModel | event mapping | instrumentation | `app/src/androidTest/.../AgentSessionViewModelTest.kt` | android-emulator | Verified (PR #28) |
| AgentSessionViewModel streaming | real API stream -> TaskState, error, stop | instrumentation | `app/src/androidTest/.../AgentSessionViewModelTest.kt` | android-emulator | Verified (PR #32) |
| AgentSessionViewModel permission | pending request exposed, reply sent, `permission.replied` clears | instrumentation | `app/src/androidTest/.../AgentSessionViewModelTest.kt` | android-emulator | Verified (PR #36) |
| AgentWorkspaceViewModel | install, server attach/detach, sessions | instrumentation | `app/src/androidTest/.../AgentWorkspaceViewModelTest.kt` | android-emulator | Verified (run 45341647148) |
| AgentWorkspaceViewModel project context | server stays bound until restart; mismatch after selection change | instrumentation | `app/src/androidTest/.../AgentWorkspaceViewModelTest.kt` | android-emulator | Verified (run 45341647148) |
| AgentWorkspaceViewModel permission | expose pending request and forward reply decision | instrumentation | `app/src/androidTest/.../AgentWorkspaceViewModelTest.kt` | android-emulator | Verified (PR #36) |
| AgentWorkspaceViewModel selected model | create-session `model` object, prompting uses selected model, settings change switches active session | instrumentation | `app/src/androidTest/.../AgentWorkspaceViewModelTest.kt` | android-emulator | Verified (PR #39) |
| AgentScreen + nav | Agent tab renders task/server cards | instrumentation | `app/src/androidTest/.../NavigationTest.kt` | android-emulator | Verified (PR #39) |
| FilesViewModel | selected-project file ops, search, preview | instrumentation | `app/src/androidTest/.../FilesViewModelTest.kt` | android-emulator | Verified (runs 45353945512, 45353953646) |
| FilesViewModel async filesystem/SAF | IO-dispatcher operations, progress/cancel state, explicit failure and partial failure | instrumentation | `app/src/androidTest/.../FilesViewModelTest.kt` | android-emulator | Verified (runs 45353945512, 45353953646) |
| FilesViewModel displayed-root stability | external project switch does not retarget active file operations | instrumentation | `app/src/androidTest/.../FilesViewModelTest.kt` | android-emulator | Verified (run 45341647148) |
| FilesScreen + nav | Files tab renders explorer controls | instrumentation | `app/src/androidTest/.../NavigationTest.kt` | android-emulator | Verified (PR #37) |
| EditorViewModel | stable workspace-bound tabs, late save does not clear newer dirty state, content-digest conflicts, same-size replacement conflict, clean reload, format undo step, Unicode/selection bounds, draft recovery/restore/keep/discard/unreadable/app-private storage | instrumentation | `app/src/androidTest/.../EditorViewModelTest.kt` | android-emulator | Verified (run 46076579398) |
| EditorScreen + nav | Files → editor, syntax editor UI, line/cursor status, editor route | instrumentation | `app/src/androidTest/.../EditorUiTest.kt`, `NavigationTest.kt` | android-emulator | Verified (run 46076579398) |
| EditorInputUiTest | IME composition survives syntax updates, Unicode/Persian input and selection bounds, 100 KiB recomposition/frame baseline records | instrumentation | `app/src/androidTest/.../EditorInputUiTest.kt` | android-emulator (API 29 and 36) | Verified (run 46076579398) |
| TerminalSession state | exit decoding, input normalization, listener races, idempotent close, project context | unit | `app/src/test/.../terminal/TerminalSessionTest.kt` | verify | Verified (runs 45336260899, 45341646980) |
| TerminalViewModel + native PTY | session start, command output, cwd, resize, multi-session, shutdown | instrumentation (opt-in) | `app/src/androidTest/.../TerminalViewModelTest.kt` | runtime-spike | Verified (runs 45336872176, 45347183919) |
| TerminalViewModel project context | sessions retain launch root; new sessions use selected root | instrumentation (opt-in) | `app/src/androidTest/.../TerminalViewModelTest.kt` | runtime-spike | Verified (run 45347183919) |
| PtyReliabilityTest | normal/non-zero/signal exit, launch failure, rapid lifecycle, concurrent listeners | instrumentation (opt-in) | `app/src/androidTest/.../PtyReliabilityTest.kt` | runtime-spike | Verified (run 45336872176) |
| TerminalScreen + nav | Terminal tab renders terminal controls | instrumentation | `app/src/androidTest/.../NavigationTest.kt` | android-emulator | Verified (PR #38) |
| AppContainer shared project selection | agent/explorer share one root | instrumentation | `app/src/androidTest/.../FilesViewModelTest.kt` | android-emulator | Verified (runs 45336260899, 45341647148) |
| AppContainer | object graph | unit | missing (Context-bound) | - | Pending |
| Compose screens | rendering | instrumentation | `app/src/androidTest/.../NavigationTest.kt` | android-emulator | Partial (navigation/Files/Terminal smoke) |

## Native payloads (static, x86_64 runner)

| Artifact | Expected | Test type | Location | Workflow | Status |
| --- | --- | --- | --- | --- | --- |
| OpenCode Bionic opencode (aarch64) | size, SHA-256, ELF64 AArch64 | static | `native-runtime-ci.yml` | native-runtime-ci | Verified (run 45006056315) |
| Termux bootstrap-aarch64.zip | size, SHA-256, zip integrity | static | `native-runtime-ci.yml` | native-runtime-ci | Verified (run 45006056315) |

## Known unverified (honest)

- ARM64 device execution of the Bionic runtime (risk R1) — no runner available.
- OpenCode server end-to-end on a device — supervision/client tested against scripts/loopback server only.
- Remaining App ViewModels and Compose screens — depend on Android Context; only the new editor flows are instrumented in this milestone.
- `OpenCodeController` and `RuntimeController` orchestration — Android-bound; covered indirectly by `:core`/`:runtime` unit tests and manual device runs.
- Real OpenCode env-auth handshake on ARM64 with a saved provider key — config/env and redaction contracts are proven on x86_64, not a device provider response (risk R1).
- Custom OpenAI-compatible provider endpoints — current UI has no `package`/`baseURL` fields; only provider/model/key are covered.

# PocketDev — Master Engineering Prompt

> **Document type:** Product specification, architecture brief, implementation contract, and autonomous coding-agent instructions  
> **Version:** 1.1  
> **Prepared:** 2026-11-20  
> **Target agent:** OpenCode Bionic running inside Termux on an Android phone  
> **Primary repository:** The existing PocketDev Git repository (discover its actual remote; do not invent one)

---

# 0. READ THIS FIRST — OPERATING CONTRACT

You are the principal engineer responsible for turning the existing PocketDev repository into a real Android development workstation.

Read this entire document before changing files. Treat the requirements below as the project contract. Do not silently weaken, reinterpret, or remove a requirement. When a requirement conflicts with the existing codebase or a verified platform limitation, document the conflict and propose the smallest technically correct adjustment.

## 0.1 The most important infrastructure decision: GitHub Actions is the build and test machine

**All compilation, packaging, automated testing, static analysis, emulator execution, native compilation, and release builds MUST run on GitHub-hosted GitHub Actions runners. They must not run on the Android phone.**

The phone has limited CPU, RAM, cooling, storage, and battery capacity. It is the control surface and lightweight coding workstation, not the build server.

### Allowed on the Android phone

- Run OpenCode Bionic and interact with this coding agent.
- Inspect repository files and source code.
- Edit and create source files, configuration, documentation, and workflow YAML.
- Use Git for status, diff, branch, commit, fetch, pull, and push.
- Use GitHub CLI/API or the GitHub website to dispatch workflows and inspect run status/logs.
- Download CI artifacts such as APKs, reports, mappings, and test logs.
- Install a CI-produced APK only when the owner explicitly asks for a manual product demonstration. This is not a substitute for CI testing.

### Forbidden on the Android phone

Do **not** run any of the following locally:

- Gradle builds or tests (`gradle`, `./gradlew`, `assemble`, `bundle`, `test`, `connectedAndroidTest`, `connectedCheck`).
- Android SDK, Android NDK, CMake, Ninja, native compilation, Rust compilation, Zig compilation, or cross-compilation for the product.
- Android Emulator, AVD creation, instrumentation tests, Robolectric test suites, or automated UI tests.
- OpenCode Bionic source builds or Termux package builds.
- APK/AAB packaging, signing, release assembly, lint, Detekt, static-analysis pipelines, or benchmark suites.
- Any local fallback build/test after a GitHub Actions failure.

You may run lightweight repository operations and non-build inspection commands. Do not install large development toolchains on the phone.

### Required remote development loop

1. Inspect and edit source on the phone using OpenCode.
2. Run only lightweight source/repository inspection locally.
3. Commit the changes on a feature branch.
4. Push the branch to GitHub.
5. GitHub Actions performs all required builds, checks, and tests.
6. Retrieve the workflow result and relevant logs/artifacts.
7. Diagnose failures from CI logs, edit the source, and repeat.
8. Merge only after all required checks pass.

If GitHub authentication, Actions permissions, runner capacity, or repository configuration prevents this loop, **stop and report the blocker**. Never silently switch to local builds or tests.

### Important distinction

OpenCode itself runs on the phone to edit the checked-out working tree. GitHub Actions is the only execution environment for PocketDev's build/test pipeline. GitHub Actions is CI/CD, not a persistent interactive IDE; do not pretend that a workflow runner is a continuously available development desktop.

## 0.2 Work autonomously, but never fake completion

- Inspect before modifying.
- Preserve existing working code.
- Implement real features, not mock screens.
- Do not report a test as passed unless a successful GitHub Actions run proves it.
- Do not claim the Bionic runtime works on Android merely because it compiles on Ubuntu.
- Do not claim emulator coverage of ARM64/Bionic unless an ARM64 Android environment actually executed the test.
- Record every important decision and unresolved risk in the repository documentation.

---

# 1. PRODUCT DEFINITION

## 1.1 What PocketDev is

PocketDev is a **complete, AI-powered development workstation for Android**. Its purpose is to let a user develop, run, inspect, and version-control real software projects directly on an Android device.

PocketDev is not merely:

- a code editor;
- a terminal emulator;
- a graphical wrapper around Termux;
- a chat interface for an AI model;
- a cloud IDE that requires a remote development machine.

It is one integrated Android application that combines a mobile-first IDE, a Termux-derived native userspace, a real terminal, package management, Git, project management, and an AI coding agent.

**Product positioning:**

> PocketDev — A complete AI-powered development workstation in your pocket.

## 1.2 Core user experience

The intended end-to-end user journey is:

1. The user installs one PocketDev APK.
2. PocketDev initializes its integrated runtime in application-private storage.
3. The user creates or opens a project.
4. The user asks the AI agent to create an application or perform a development task.
5. The agent inspects the project and runtime.
6. When a required tool is missing, the agent explains why and requests permission before installing it.
7. The agent creates or modifies real project files.
8. The user inspects the changes in PocketDev's editor and file explorer.
9. The user runs commands in PocketDev's real terminal.
10. The user starts a compatible development server and opens a local preview.
11. The user reviews changes and commits them through Git.

All application runtime activity happens on the Android device. **The development of PocketDev itself is built and automatically tested by GitHub Actions, not by the phone.**

## 1.3 Target audience

- Developers who want to code from an Android phone or tablet.
- Termux and Linux-on-Android users.
- Students learning software development.
- Developers who need a portable emergency or travel workstation.
- AI-assisted coding users who want a local project workspace.

---

# 2. FIXED PRODUCT DECISIONS

These decisions are fixed unless the product owner explicitly changes them.

## 2.1 Distribution

- One product.
- One unified APK distribution channel.
- Independent distribution through GitHub Releases and the official website.
- Google Play is not a distribution target.
- Do not create separate Play and Full editions.
- Do not create a separate Termux application dependency.
- Do not require SSH to another computer or server.

## 2.2 Runtime and execution model

- Integrate a Termux-derived Android/Bionic userspace into PocketDev.
- The runtime is managed by PocketDev and lives in PocketDev's application-private storage.
- No root requirement.
- No PRoot, Chroot, or glibc-based Linux distribution as the primary runtime.
- Native Android/Bionic execution is the intended execution model.
- The runtime must not be a second Android application.

## 2.3 AI engine

The selected AI coding engine is **OpenCode Bionic**:

- Primary reference: https://github.com/bd-loser/opencode-bionic
- Upstream OpenCode: https://github.com/anomalyco/opencode
- Bun Bionic runtime: https://github.com/bd-loser/bun-termux
- AndroidTUI/OpenTUI work: https://github.com/bd-loser/opentui

Do not substitute upstream Linux/glibc OpenCode binaries for the Bionic build.
Do not assume a standard desktop Bun binary will run on Android.
Do not run the Bionic OpenCode source build on the phone. Any build or rebuild of this dependency must happen in GitHub Actions.

## 2.4 Local-first product behavior

- The core development workspace works locally on the Android device.
- A PocketDev cloud account is not required for basic IDE/runtime functionality.
- AI providers may require user-supplied API credentials.
- Optional cloud services must not become mandatory dependencies for local development.

## 2.5 Initial platform target

Treat **Android ARM64 (`arm64-v8a` / AArch64), Android 10 / API 29 or later** as the initial target unless repository audit or verified upstream changes establish a different compatible baseline.

The current OpenCode Bionic project documents native Bionic AArch64 support and Android 10/API 29 as its minimum because of its patched Bun runtime. Re-check the current upstream README and release metadata before pinning any version.

Do not generate multiple ABI-split APKs. The product owner wants one APK. If one universal APK cannot safely contain all required artifacts, document the technical trade-off and preserve one installable APK as the release deliverable.

---

# 3. REPOSITORY AUDIT — FIRST TASK

Before changing application code, inspect the actual repository and its Git history.

Use lightweight commands only, for example:

```bash
pwd
git status --short --branch
git remote -v
git log -8 --oneline --decorate
find . -maxdepth 3 -type f | sort | head -400
```

Do not run a build or test command during the audit.

Determine:

- Whether the current directory is the PocketDev repository.
- The real Git remote and default branch.
- Whether there are uncommitted changes.
- Whether the repository already contains an Android project.
- The app language and UI framework.
- Gradle wrapper and Android Gradle Plugin versions, if present.
- `minSdk`, `targetSdk`, and `compileSdk` values, if present.
- Existing package/application ID.
- Existing navigation, theme, assets, and screens.
- Existing runtime or Termux integration work.
- Existing OpenCode integration work.
- Existing GitHub Actions workflows.
- Existing tests and documentation.
- Existing signing/release configuration (never reveal secrets).
- Licensing and third-party dependency declarations.

Create or update these files based on what actually exists:

```text
docs/
  PROJECT_AUDIT.md
  ARCHITECTURE.md
  IMPLEMENTATION_ROADMAP.md
  CI_CD.md
  DEPENDENCY_INVENTORY.md
  LICENSE_INVENTORY.md
  RISK_REGISTER.md
```

Do not overwrite a useful existing document with a generic template. Update it carefully.

At the end of the audit, report:

1. Current repository state.
2. Current architecture.
3. What already works (only verified by source inspection or existing CI evidence).
4. Missing components.
5. Major compatibility risks.
6. The first implementation milestone.
7. Whether GitHub Actions can currently be used.

Then proceed incrementally. Do not stop after writing documents.

---

# 4. SYSTEM ARCHITECTURE

Use modular architecture. Avoid a monolithic Activity, service, or runtime controller.

The following is a logical architecture. Inspect the codebase and choose appropriate physical Gradle modules/packages.

```text
PocketDev.apk
│
├── Android Application
│   ├── Navigation and app state
│   ├── Dashboard and project picker
│   ├── Settings and onboarding
│   ├── Permission and lifecycle management
│   └── Error reporting
│
├── Workspace Layer
│   ├── Project manager
│   ├── File explorer
│   ├── Code editor
│   ├── Git interface
│   └── Local preview interface
│
├── AI Layer
│   ├── OpenCode process/controller
│   ├── OpenCode API client
│   ├── Session manager
│   ├── Event stream processor
│   ├── Permission bridge
│   └── Tool/activity presentation
│
├── Runtime Layer
│   ├── Termux-derived bootstrap
│   ├── Bionic userspace
│   ├── Shell and utilities
│   ├── APT/package manager
│   ├── Runtime environment
│   ├── Native libraries
│   └── Process supervision
│
├── Terminal Layer
│   ├── PTY host
│   ├── Terminal emulator/view
│   ├── Session manager
│   └── Input/output handling
│
└── Integration Layer
    ├── Runtime bridge
    ├── Project filesystem bridge
    ├── Process bridge
    ├── Android API bridge
    └── Security/permission boundary
```

## 4.1 Architectural boundaries

- Android UI code must not directly manipulate low-level runtime internals.
- Runtime code must expose a small, documented API to the application.
- Terminal sessions must be managed by a dedicated subsystem.
- OpenCode lifecycle and API access must be managed by a dedicated component.
- All subsystems must share a single project/workspace identity and consistent filesystem paths.
- Do not maintain duplicate project copies for the editor, agent, terminal, and Git interface.
- Avoid hidden global state where explicit dependency injection or scoped services are practical.

---

# 5. GITHUB ACTIONS — REQUIRED CI/CD ARCHITECTURE

This section is mandatory. A product milestone is not complete until the relevant GitHub Actions workflows exist and pass.

## 5.1 GitHub Actions is the canonical execution environment

GitHub Actions must be the source of truth for:

- Android compilation.
- Unit tests.
- Instrumentation/UI tests.
- Lint and static analysis.
- Dependency checks.
- Native cross-compilation.
- OpenCode Bionic packaging or artifact acquisition/verification.
- Runtime artifact preparation.
- APK assembly.
- Release signing.
- Checksums and release publication.

No local build/test workflow may be added as a required or fallback procedure.

The phone may initiate or monitor Actions runs, but it must not perform the build/test workload.

## 5.2 Workflows to create or improve

Inspect existing workflows first. Reuse or improve them instead of creating duplicate pipelines.

At minimum, implement these logical workflows (the exact filenames may follow existing repository conventions):

### A. `verify.yml` — required pull-request gate

Triggers:

- `pull_request` to the default branch.
- `push` to the default branch and active development branches.
- `workflow_dispatch` for manual execution.

Jobs should include, as applicable:

1. Repository and configuration validation.
2. Formatting checks.
3. Android lint.
4. Unit tests.
5. Kotlin/static analysis (only if the project uses Kotlin and the relevant tool is configured).
6. Dependency/license inventory validation.
7. Debug APK compilation.
8. Upload of reports and debug APK as workflow artifacts.

Use the Gradle wrapper committed to the repository. Validate its distribution URL and checksum. Never download an arbitrary Gradle version from an untrusted location.

### B. `android-emulator-tests.yml` — Android integration/UI tests

Use GitHub-hosted Linux runners and a maintained Android Emulator GitHub Action, such as:

- https://github.com/ReactiveCircus/android-emulator-runner

Before implementation, verify the action's current maintenance status, inputs, latest supported version, and immutable commit SHA.

The workflow should:

- Install the required Android SDK platform/build-tools/system image.
- Enable KVM where the selected runner supports it.
- Create a headless emulator.
- Run the project's instrumentation tests (for example `connectedDebugAndroidTest` or the correct task discovered from the project).
- Collect logcat, test reports, screenshots, and failure artifacts.
- Always upload diagnostics on failure.

Start with a practical x86_64 emulator for UI and Android framework tests. Do not claim that an x86_64 emulator validates the ARM64 Bionic runtime.

### C. `native-runtime-ci.yml` — native/runtime pipeline

This workflow handles native artifacts and runtime verification.

- Use the Android NDK, Clang, Rust, Zig, or other toolchains only on GitHub-hosted runners when required.
- Pin toolchain versions.
- Cross-compile only for explicitly supported ABIs.
- Verify ELF architecture, Android API compatibility, linker dependencies, and checksums.
- Never execute an Android/Bionic binary as though a normal Ubuntu/glibc runner were an Android device.
- Prefer consuming an upstream OpenCode Bionic release artifact when the project does not need to patch/rebuild OpenCode itself.
- If PocketDev maintains patches or a fork, build them in Actions using the upstream project's documented pipeline and pinned source refs.

### D. `release.yml` — signed APK and GitHub Release

Trigger only on an explicitly approved release tag or manual release dispatch with safeguards.

The workflow must:

- Run all required checks before publishing.
- Build the release APK on GitHub Actions.
- Sign it using secrets stored in GitHub repository/environment settings.
- Never commit a keystore, passwords, API keys, signing keys, or tokens.
- Generate SHA-256 checksums.
- Generate release notes and include known limitations.
- Upload the single intended APK to GitHub Releases.
- Upload license notices/attributions and relevant SBOM/dependency reports.

Do not publish a release when required checks fail.

### E. Optional scheduled checks

Add scheduled jobs only when they provide value, such as dependency update checks or upstream OpenCode Bionic version monitoring. Scheduled jobs must not automatically upgrade production runtime dependencies without a reviewable PR and passing tests.

## 5.3 Build/test matrix

Design the matrix from the actual app's `minSdk`, `targetSdk`, Gradle/AGP compatibility, and runtime requirements.

At minimum consider:

- The minimum supported Android API level.
- A current supported Android API level.
- Android UI/framework tests on x86_64 emulator.
- ARM64 native artifact validation.
- ARM64 Android runtime integration, if a compatible emulator/runner is available.

Avoid an unnecessarily huge matrix that wastes Actions time. Use a small, high-signal matrix first.

## 5.4 ARM64 Bionic validation — critical limitation

A normal Ubuntu runner is a Linux environment, not an Android phone. A native Bionic ELF binary is not fully validated merely by compiling it or checking that it is AArch64.

Create an initial CI feasibility spike to determine whether the current GitHub-hosted ARM64 Linux runner and available Android emulator images can execute an ARM64 Android emulator with the required acceleration.

The spike must determine:

- Whether a compatible ARM64 AVD/system image is available.
- Whether the selected GitHub-hosted runner provides the needed virtualization/KVM capability.
- Whether the emulator boots reliably in headless Actions execution.
- Whether the app can install and run in that emulator.
- Whether the Bionic OpenCode binary can execute under the Android system image.
- Whether required Android/Termux filesystem and linker behavior is available.

If the ARM64 Android emulator route is unavailable or unreliable:

1. Keep x86_64 emulator tests for UI/framework behavior.
2. Keep ARM64 cross-compilation and static ELF/dependency checks in Actions.
3. Do not pretend these checks prove end-to-end Bionic operation.
4. Record the missing device-level validation as an explicit release risk.
5. Do not fall back to building/testing on the owner's phone.
6. Propose an optional later cloud-device-testing integration only if it can be triggered and reported from the Actions workflow. Do not add a paid external service without approval.

## 5.5 Action security and reproducibility

- Pin third-party Actions to full immutable commit SHAs, not floating tags, after verifying the official repository and release.
- Add a comment with the corresponding version/tag next to each SHA pin.
- Use least-privilege `permissions` at workflow/job level.
- Use `contents: read` by default.
- Grant `contents: write` only to the release-publishing job when required.
- Use `concurrency` to cancel obsolete builds on the same branch/PR where appropriate.
- Set reasonable job timeouts.
- Do not expose repository secrets to untrusted fork pull requests.
- Do not use `pull_request_target` to check out and execute untrusted PR code.
- Do not interpolate untrusted PR text directly into shell commands.
- Avoid broad write permissions.
- Use dependency caches for Gradle and other suitable package managers.
- Use lockfiles and dependency verification where supported.
- Keep build logs free of credentials and API keys.
- Preserve test reports even when a job fails.

## 5.6 Secrets and signing

Document required secrets without putting values in source files.

Potential release secrets:

- `ANDROID_KEYSTORE_BASE64` or an equivalent secure keystore file mechanism.
- `ANDROID_KEYSTORE_PASSWORD`.
- `ANDROID_KEY_ALIAS`.
- `ANDROID_KEY_PASSWORD`.

Use a GitHub Environment for releases when useful, with approval protection if available.

Use GitHub's automatically provided `GITHUB_TOKEN` with narrow permissions for normal workflow operations. Do not ask the user to paste personal access tokens into source code or chat logs.

## 5.7 Artifacts and reports

For each CI run, retain appropriate artifacts:

- Debug APK for test/demo use.
- Release APK only in the protected release workflow.
- Unit-test reports.
- Android lint reports.
- Instrumentation test reports.
- Logcat and emulator diagnostics on failure.
- Native artifact checksums and metadata.
- Dependency and license reports.
- Build provenance/version metadata.

Do not upload secrets, signing material, user data, or private project content unrelated to the build.

## 5.8 CI is mandatory, not advisory

Protect the default branch with required checks where repository settings allow it.

A feature is not considered complete until:

- The relevant workflow exists.
- The workflow has actually run on GitHub-hosted infrastructure.
- The run succeeded.
- Its logs and test reports are available.
- Any platform coverage limitations are documented.

A local source inspection is not a substitute for a successful CI run.

---

# 6. MOBILE-BASED CODING AGENT WORKFLOW

OpenCode Bionic is running on the Android phone. Use it as a lightweight coding agent, not as a build server.

## 6.1 Git workflow

- Work on a feature branch, not directly on the protected default branch.
- Inspect `git status` before every major change.
- Never overwrite or discard user changes without explicit approval.
- Make small, reviewable commits.
- Push changes to GitHub to trigger CI.
- Never push secrets, keystores, API keys, `.env` files, or generated build output.

## 6.2 CI interaction

Check whether GitHub CLI (`gh`) is already available and authenticated. Do not install a full build toolchain.

If `gh` is available, use it for lightweight operations such as:

- Listing workflow runs.
- Viewing run status.
- Viewing job logs.
- Downloading workflow artifacts.
- Dispatching approved workflows.

If GitHub CLI is unavailable, use the GitHub web interface or the GitHub REST API with a securely configured credential. Do not print tokens.

After every meaningful push:

1. Identify the workflow run for the pushed commit.
2. Wait/poll for its completion when requested; do not claim completion while it is running.
3. Retrieve failed job names and relevant logs.
4. Group failures by root cause.
5. Fix the source/configuration.
6. Push a new commit.
7. Confirm the new run passed.

Never diagnose a failure solely from an incomplete log excerpt when the full job log is available.

## 6.3 No local fallback

If Actions cannot run because of missing permissions, exhausted quota, a disabled workflow, or GitHub service issues, report that exact blocker and stop the build/test portion. Do not run the task locally as a workaround.

---

# 7. ANDROID APPLICATION FOUNDATION

Build a reliable native Android application using the existing project's stack. Do not change frameworks solely because another stack is fashionable.

Requirements:

- Correct Android lifecycle handling.
- Navigation and state restoration.
- Responsive mobile-first layout.
- Runtime initialization and health checks.
- Clear permission flows.
- Error handling and diagnostics.
- Persistent app preferences.
- Large-screen and landscape support where practical.
- External keyboard usability.
- Accessibility labels, scalable text, and touch targets.

Avoid keeping heavy processes alive unnecessarily. Android may terminate background processes; the application must recover gracefully.

Use foreground services only when they are justified and comply with current Android requirements. Do not assume a background process can run forever.

---

# 8. TERMUX-DERIVED RUNTIME

Primary references:

- Termux app: https://github.com/termux/termux-app
- Termux packages: https://github.com/termux/termux-packages
- Termux website: https://termux.dev/
- Termux app license: https://github.com/termux/termux-app/blob/master/LICENSE.md
- Termux shared library license details: https://github.com/termux/termux-app/blob/master/termux-shared/LICENSE.md

## 8.1 Runtime goals

The integrated runtime should provide:

- Android/Bionic-native executable support.
- A functional shell.
- Unix userland utilities.
- Environment variables and standard directory layout.
- Package management compatible with the chosen Termux-derived base.
- Native shared-library loading.
- Process creation and termination.
- Signals.
- PTY support.
- Persistent home/project storage.
- Basic networking and localhost development servers.

Do not build a fake Linux layer that only simulates commands.

## 8.2 Integration strategy

Investigate the Termux source and packaging model before selecting what to reuse.

Possible approaches include:

1. A focused fork/derivative of the Termux Android application components.
2. Reuse of compatible Termux shared components and package/bootstrap assets.
3. A PocketDev-owned runtime manager that installs a compatible Termux bootstrap into PocketDev-private storage.

Choose based on technical correctness, Android compatibility, licensing, and maintainability.

Do not copy the full Termux app blindly. Do not copy source code without preserving required notices and license obligations.

The Termux app repository currently states GPLv3-only for the app, with specific exceptions; `termux-shared` has its own file-level licensing exceptions. Termux packages each have their own licenses. Perform a file/package-level license inventory before distribution.

## 8.3 Runtime layout

Propose a stable application-private directory layout. For example:

```text
<app-private-data>/
  runtime/
    usr/
      bin/
      etc/
      lib/
      include/
      share/
      var/
  home/
    projects/
    .config/
    .cache/
  state/
  logs/
  downloads/
  backups/
```

This is illustrative only. Preserve the exact prefix and filesystem conventions required by the selected Termux bootstrap and packages. Do not break hard-coded `$PREFIX` assumptions.

## 8.4 Bootstrap manager

Implement a resumable, idempotent bootstrap process:

1. Check device ABI and Android API level.
2. Check free storage.
3. Verify whether runtime is already initialized.
4. Download or unpack the pinned bootstrap artifact.
5. Verify cryptographic checksum/signature.
6. Safely extract files, preventing path traversal and symlink abuse.
7. Set required executable permissions.
8. Configure runtime environment variables.
9. Initialize package metadata/repository configuration.
10. Run lightweight health checks.
11. Store runtime version and bootstrap state.
12. Mark initialization complete only after all required checks pass.

The bootstrap must be resumable after process death, app restart, or network interruption.

Do not silently overwrite a healthy existing runtime during an update.

## 8.5 Environment

Configure the environment variables required by the actual Termux-derived implementation, including where appropriate:

- `PREFIX`
- `HOME`
- `PATH`
- `TMPDIR`
- `LD_LIBRARY_PATH` (only as correctly required by the runtime; do not set blindly)
- `TERM`
- `LANG`

Read upstream source for exact Termux-specific variables and paths. Never guess them.

## 8.6 Package management

The user should be able to install tools as needed, for example:

- Python
- Node.js
- Git
- Clang
- CMake
- Make
- Rust
- Go
- PHP
- Ruby
- OpenSSH
- Vim/Nano
- Other compatible Termux packages

Do not install every development package on first launch. Keep the initial runtime lightweight.

Package installation must use trusted repositories, validate repository metadata/signatures, expose progress/errors, and never silently install arbitrary software at the agent's discretion.

The AI agent may recommend a package and request approval before installation.

---

# 9. OPENCODE BIONIC INTEGRATION

Primary references:

- https://github.com/bd-loser/opencode-bionic
- https://github.com/bd-loser/opencode-bionic/blob/main/docs/ARCHITECTURE.md
- https://github.com/bd-loser/opencode-bionic/blob/main/docs/BUILDING.md
- https://github.com/bd-loser/bun-termux
- https://github.com/bd-loser/opentui
- https://opencode.ai/docs/
- OpenCode API reference: https://opencode.ai/docs/api
- OpenCode SDK overview: https://opencode.ai/docs/build/sdk/

## 9.1 Understand the Bionic build

The current OpenCode Bionic project documents that upstream OpenCode's terminal UI depends on native OpenTUI components. Upstream desktop Linux binaries target glibc and are not directly compatible with Android's Bionic linker.

The Bionic project assembles a compatible stack that includes:

- A Bun runtime patched for Android/Bionic.
- OpenTUI native components rebuilt for Android ARM64 and published under AndroidTUI package names.
- A Bionic PTY component.
- A pinned upstream OpenCode source version, patch/configuration delta, and release pipeline.
- A self-contained Bionic executable and/or Termux package release artifacts.

The Bionic repository's documented pipeline fetches a pinned upstream source revision, applies its patch/configuration delta, compiles on a CI runner/container, and publishes binary/package artifacts with checksums.

Do not assume the version numbers in this document remain current. Read the upstream `versions.json`, latest release assets, and architecture documentation when implementing.

## 9.2 Prefer verified upstream artifacts unless a fork is genuinely required

First determine whether PocketDev can consume an existing OpenCode Bionic stable release artifact.

Preferred order:

1. Pin an exact known-good OpenCode Bionic release/tag or source commit.
2. Download its specific ARM64 artifact in GitHub Actions.
3. Verify SHA-256 or the upstream-provided signed/checksum metadata.
4. Record upstream version, commit, artifact URL, checksum, and license in a generated manifest.
5. Package or provision the artifact using the PocketDev runtime strategy.

Only maintain a PocketDev fork or custom Bionic patch series when a demonstrated integration requirement cannot be met by the upstream artifact.

If a fork is necessary, preserve the upstream delta model where practical: pinned source revisions, small patches, reproducible CI build, and checksum-verified releases.

## 9.3 One APK does not mean all optional packages must be embedded

The deliverable must remain one installable PocketDev APK and one app identity. It is acceptable to evaluate whether large runtime components should be:

- Embedded/compressed in the APK and extracted on first launch; or
- Fetched as authenticated/checksummed runtime payloads during first-run provisioning.

Do not decide this casually. Compare:

- Download size of the APK.
- Compressed and extracted size.
- Android package installer limits/behavior.
- First-launch network requirements.
- Offline installation expectations.
- Update size and bandwidth.
- Integrity and rollback behavior.
- GitHub Releases availability and rate limits.

The current OpenCode Bionic README reports a substantial self-contained binary; the stable Web UI adds approximately 25 MB to the binary according to its documentation. Treat size as a first-class architectural issue.

Do not split PocketDev into a separate Termux APK and a PocketDev APK. Downloading a runtime payload into PocketDev's private data directory is not a second Android application.

## 9.4 Pinning and updates

- Pin the exact OpenCode Bionic release and upstream commit.
- Pin the compatible Bun Termux, AndroidTUI/OpenTUI, and PTY dependencies through upstream metadata.
- Store the pin manifest in the repository.
- Do not fetch an unpinned `latest` binary in production.
- Do not automatically upgrade the runtime without compatibility tests.
- Support a controlled update and rollback strategy.
- Verify checksums before installing or replacing an executable.

## 9.5 Integrating OpenCode with the graphical PocketDev UI

PocketDev must provide its own native/mobile-first AI workspace. OpenCode's terminal UI is not the PocketDev product UI.

Investigate and compare:

- `opencode serve` plus the official HTTP API/client.
- `@opencode/sdk` embedded-host model, if compatible with the chosen Bionic runtime and application process architecture.
- A controlled local process interface.

Prefer a documented, supported OpenCode interface over parsing terminal text or scraping the TUI.

For a server-based integration:

- Launch the OpenCode server as a supervised child process.
- Bind only to `127.0.0.1` unless a clear, reviewed requirement justifies otherwise.
- Select an available local port safely.
- Use the authentication mechanism supported by the pinned OpenCode version.
- Do not expose the server to the LAN or internet by default.
- Keep credentials out of logs and crash reports.
- Monitor process health and restart only under a controlled policy.
- Use the official API schema/client for the pinned version.

The OpenCode SDK documentation currently describes hosting OpenCode in-process without opening an HTTP listener, but that does not automatically prove it is the best option for a native Android/Kotlin application. Verify compatibility with the Bionic Bun runtime before choosing it.

## 9.6 AI feature requirements

The UI must support:

- Multiple sessions.
- Session history.
- Provider/model selection.
- User-supplied API keys.
- Streaming responses/events.
- Tool execution visibility.
- File change visibility.
- Permission prompts.
- Task status.
- Error states and retry.
- Cancellation.
- Session restoration where supported.

Map real OpenCode API events to UI state. Do not invent fake progress.

---

# 10. AI WORKSPACE AND PERMISSION MODEL

The AI workspace is a core product area, not a cosmetic chat screen.

## 10.1 Agent capabilities

Within the current project workspace, the agent should be able to:

- Read and search project files.
- Create and edit files.
- Inspect project structure.
- Execute approved shell commands.
- Recommend/install missing packages with consent.
- Run project-specific commands through the runtime.
- Inspect build/test output from the user's projects.
- Work with Git.
- Start/stop compatible development servers.

These are PocketDev runtime capabilities. They do not change the rule that **PocketDev's own development builds and tests run in GitHub Actions**.

## 10.2 Permission levels

Use OpenCode's own permission system and supplement it with PocketDev UI controls where necessary.

Require explicit confirmation for sensitive actions such as:

- Installing system packages.
- Deleting or overwriting substantial data.
- Accessing files outside the selected project.
- Git push or force push.
- Destructive Git operations.
- Opening network listeners beyond localhost.
- Running commands with broad filesystem/network side effects.
- Modifying runtime/system configuration.

Never silently grant the agent unrestricted access to all app data or the entire device.

## 10.3 Task state model

Represent actual events using states such as:

```text
IDLE
INITIALIZING
THINKING
TOOL_RUNNING
WAITING_FOR_PERMISSION
RUNNING
COMPLETED
FAILED
CANCELLED
```

Only show a state when supported by actual OpenCode/session events.

---

# 11. CODE EDITOR

Implement a real editor connected to the same project filesystem used by the terminal and AI agent.

Required capabilities:

- Syntax highlighting.
- Multiple open files/tabs.
- Line numbers.
- Search and replace.
- Undo/redo.
- Save and dirty-state indication.
- File navigation.
- Basic formatting integration where practical.
- Keyboard toolbar and external keyboard shortcuts.
- Large-file handling.
- Correct encoding/newline handling.
- Recovery from external file modifications.
- Clear conflict handling when AI changes a file open in the editor.

Investigate maintained editor technologies such as Monaco, CodeMirror, Android-native editors, or another actively maintained component.

Do not embed a heavy desktop editor without measuring memory, startup, and mobile usability.

Do not create a plain text input and label it an IDE editor.

---

# 12. FILE EXPLORER AND WORKSPACE MODEL

Provide:

- Project-root navigation.
- File/folder creation.
- Rename, copy, move, and delete.
- Search.
- Hidden-file visibility.
- File type recognition and previews.
- Import/export through Android's Storage Access Framework where appropriate.
- Safe handling of external storage access.

Every project should have a consistent identity containing:

- Project root path.
- Runtime context.
- Open editor files.
- Terminal sessions.
- Git repository/status.
- AI sessions.
- Development server sessions.

All subsystems must refer to the same on-disk files. Do not create isolated copies for the editor, agent, or terminal.

Validate all paths. Prevent path traversal and unsafe symlink handling.

---

# 13. TERMINAL AND PTY

The terminal must be interactive and backed by a real PTY. Do not simulate it with a text box and an output label.

Required capabilities:

- PTY allocation.
- Shell process lifecycle.
- Interactive input/output.
- ANSI escape sequence support.
- Cursor movement and terminal resize.
- Scrollback.
- Copy/paste.
- External keyboard shortcuts.
- Multiple sessions where memory permits.
- Process termination and signal handling.
- Session cleanup and recovery.

Investigate Termux terminal components and the Bionic-compatible PTY shipped with OpenCode Bionic. Do not assume OpenCode's PTY component automatically solves all PocketDev terminal requirements; verify API and license compatibility.

---

# 14. PACKAGE MANAGER EXPERIENCE

Provide a package manager interface around the integrated runtime's actual package manager.

Capabilities:

- Search packages.
- Show package metadata and version.
- Install/update/remove with explicit user consent.
- Show progress and logs.
- Detect installed tools.
- Maintain a runtime inventory.
- Recover safely from interrupted package transactions.

The AI agent may inspect the inventory and recommend missing tools. It must not silently install arbitrary system packages.

Do not hardcode a list that claims every upstream package is supported. Determine availability from the configured repository and device ABI.

---

# 15. GIT INTEGRATION

Provide a graphical Git workflow using the Git executable in the integrated runtime or a verified compatible Git library.

Required operations:

- Initialize repository.
- Clone.
- Fetch.
- Pull.
- Status.
- Diff.
- Stage/unstage.
- Commit.
- Branch create/switch/delete.
- Merge/rebase where supported.
- Remote management.
- Push.
- SSH key configuration where applicable.

Secure credentials. Do not write tokens to plaintext logs or project files.

The AI agent may use Git, but destructive actions and remote pushes must follow permission policy.

---

# 16. DEVELOPMENT SERVER AND PREVIEW

Support common frameworks only where they are compatible with the Android runtime and the selected packages.

Potential examples:

- Vite.
- Node.js HTTP servers.
- Python HTTP servers.
- PHP development server.
- Other compatible local development servers.

The user should be able to:

1. Start a server.
2. See its output and state.
3. Detect the listening port.
4. Open a preview inside PocketDev.
5. Stop the server.

Prefer localhost. Do not expose development servers to the local network by default.

Maintain a tested compatibility matrix. Do not claim that every desktop framework works on Android.

---

# 17. SECURITY

Security is a core requirement.

Implement and document:

- Android app sandbox compliance.
- Secure API key storage, using Android Keystore where appropriate.
- Runtime artifact checksum/signature verification.
- Package repository signature verification.
- Safe archive extraction.
- Path validation and traversal prevention.
- Symlink handling rules.
- Shell-command permission controls.
- Secure temporary files.
- Git credential protection.
- Log redaction.
- Safe local-server binding/authentication.
- Least-privilege Android permissions.
- Dependency vulnerability review where available.

Never execute a downloaded binary before verifying its provenance and integrity.

Never log API keys, passwords, signing material, tokens, or private user source code unnecessarily.

---

# 18. PERFORMANCE AND RESOURCE MANAGEMENT

The target device is a smartphone. Design for constrained hardware even though the product's own builds/tests run in GitHub Actions.

Optimize:

- RAM usage.
- CPU usage.
- Battery use.
- Startup latency.
- Runtime disk footprint.
- Number of active processes.
- Large project indexing.
- Terminal scrollback memory.
- Editor large-file behavior.
- AI event streaming.

Use lazy initialization and bounded caches where appropriate.

Do not keep unused heavyweight components running.

Provide runtime diagnostics such as free storage, runtime version, package inventory, active processes, and health checks.

---

# 19. ONE-APK PACKAGING STRATEGY

The release deliverable is one installable PocketDev APK.

Investigate and document the packaging strategy for:

- Android application code.
- Termux-derived bootstrap/runtime assets.
- OpenCode Bionic executable.
- Required Bionic native libraries.
- Default tools/utilities.
- Optional package payloads.

Evaluate:

1. Embedded runtime assets extracted to app-private storage.
2. Compressed bootstrap assets included in the APK.
3. Checksum-verified runtime downloads during first-run setup.
4. Hybrid provisioning with a minimal APK and large optional components downloaded on demand.

Keep the product as one Android app. Runtime downloads must not require installation of another app.

Do not use Android dynamic-feature APK splits as a way to create multiple user-installed applications.

Measure actual APK size and extracted storage usage in GitHub Actions. Add size regression reporting/thresholds where practical.

---

# 20. LICENSING AND ATTRIBUTION

Before redistributing any component, inspect its actual license and required notices.

At minimum audit:

- Termux app source.
- Termux shared libraries (including file-level exceptions).
- Termux bootstrap and package payloads.
- Every package installed by default.
- OpenCode Bionic repository and its bundled components.
- Upstream OpenCode.
- Bun Termux.
- OpenTUI/AndroidTUI.
- PTY implementation.
- Android libraries and editor component.

Important known starting points:

- Termux app repository currently declares GPLv3-only with exceptions.
- `termux-shared` has a different license structure and specific file-level exceptions.
- Termux packages have individual licenses; do not assume every package is GPL.
- OpenCode Bionic README currently states MIT for the project/upstream OpenCode, but audit all bundled and redistributed third-party components independently.

Generate and maintain:

- `LICENSE_INVENTORY.md`
- `THIRD_PARTY_NOTICES.md` or equivalent
- SBOM/dependency report where practical

Do not infer that the entire PocketDev application must have one license merely because it integrates a GPL component. Equally, do not assume a proprietary UI can automatically be combined with GPL code without obligations. Analyze the exact copied/linked/combined components and distribution model. Flag unclear questions for qualified legal review.

---

# 21. TEST PLAN — RUN ONLY IN GITHUB ACTIONS

Every automated test in this section must execute on GitHub Actions, never on the Android phone.

## 21.1 Static and source-level checks

- Formatting.
- Android lint.
- Kotlin/Java static analysis as applicable.
- Dependency resolution/verification.
- License inventory validation.
- Manifest and permission checks.
- APK content inspection.
- Duplicate native library checks.

## 21.2 Unit tests

Test isolated components:

- Runtime state machine.
- Bootstrap progress/resume logic.
- Environment configuration.
- Path validation.
- Project/workspace identity.
- Process supervision logic.
- OpenCode API client and event mapping.
- Permission policy.
- Package inventory parsing.
- Git state parsing.
- UI state reducers/view models.

Use fakes/mocks for unit tests only. Do not confuse a mocked OpenCode or runtime test with a real integration test.

## 21.3 Android emulator tests

Run instrumentation tests on GitHub-hosted Android emulators for:

- Application launch.
- Navigation.
- Onboarding.
- Permission UI.
- Project creation.
- File explorer interactions.
- Editor opening/saving.
- Terminal screen lifecycle.
- Runtime bootstrap UI state.
- Error and retry UI.
- Accessibility basics.

Collect emulator logs and screenshots on failures.

## 21.4 Runtime tests

Where an actual Android ARM64 emulator is supported on GitHub-hosted infrastructure, test:

- Runtime bootstrap.
- Shell startup.
- Environment variables.
- Bionic executable execution.
- Shared-library loading.
- Package manager operation.
- PTY allocation.
- Signals/process termination.
- Localhost networking.
- OpenCode Bionic startup and shutdown.

If the required ARM64 Android environment cannot be provisioned in GitHub Actions, clearly separate:

- Tests that did run (cross-compile/static checks/UI emulator tests).
- Tests that did not run (true ARM64 Android/Bionic integration).

Do not label the latter as passed.

## 21.5 End-to-end acceptance scenario

The target acceptance workflow is:

1. Install the CI-produced PocketDev APK in an Android emulator running in GitHub Actions.
2. Complete first-run setup.
3. Initialize the runtime.
4. Create a project.
5. Open an AI workspace.
6. Connect a test provider/mock endpoint appropriate for CI (never expose a production key).
7. Ask the agent to create a small sample project.
8. Verify that actual files are created in the project directory.
9. Open a file in the editor.
10. Verify an edit/save cycle.
11. Run a compatible command through the terminal.
12. Start a local development server if supported by the CI runtime.
13. Verify the preview or localhost response.
14. Initialize Git and create a commit.
15. Verify the repository status.

Where AI-provider integration requires a real external API, split deterministic integration tests from an optional protected smoke test. Never make a pull-request workflow depend on a paid API key.

---

# 22. IMPLEMENTATION ROADMAP

Develop in small, testable milestones. Each milestone must have a GitHub Actions workflow run as evidence.

## Milestone 0 — Audit and CI foundation

- Inspect repository.
- Document current state.
- Verify GitHub Actions access.
- Create or repair baseline CI.
- Establish pull-request required checks.
- Prove that a simple Android build/test runs on GitHub-hosted runners.

**Exit criteria:** GitHub Actions builds the existing app and runs its currently available tests. No local build is used.

## Milestone 1 — Android foundation

- Stabilize app startup/navigation.
- Establish UI architecture and state management.
- Add dashboard, projects view, settings shell.
- Add error handling and lifecycle basics.

**Exit criteria:** CI build, unit tests, lint, and emulator launch/navigation tests pass.

## Milestone 2 — Runtime architecture and feasibility

- Audit Termux components.
- Select runtime integration strategy.
- Produce license inventory.
- Design bootstrap/storage layout.
- Add runtime metadata model and UI states.
- Create ARM64 Android emulator feasibility spike in Actions.

**Exit criteria:** Architecture decision record exists; CI validates the relevant non-device-specific code; ARM64 device-test feasibility is honestly reported.

## Milestone 3 — Runtime bootstrap

- Add pinned bootstrap asset/source metadata.
- Implement checksum verification.
- Implement safe extraction.
- Implement resumable initialization.
- Implement runtime health checks.

**Exit criteria:** Automated bootstrap tests pass in Actions. Real Bionic execution is only marked passed if tested in a real Android ARM64 environment in Actions.

## Milestone 4 — OpenCode Bionic provisioning and process control

- Pin a Bionic release/commit.
- Verify and package/provision the binary.
- Implement process launch/stop/health management.
- Integrate the documented OpenCode API/server or verified SDK approach.
- Add session/event plumbing.

**Exit criteria:** CI tests deterministic controller/API behavior. Actual binary execution is only marked passed when run in a valid ARM64 Android environment.

## Milestone 5 — AI workspace

- Build the native/mobile-first AI workspace.
- Add streaming event UI.
- Add session management.
- Add provider/model settings.
- Add permission prompts.

**Exit criteria:** UI and state tests pass in Actions; a protected end-to-end integration test validates real engine connectivity where infrastructure permits.

## Milestone 6 — Terminal and filesystem

- Implement PTY-backed terminal.
- Implement project file explorer.
- Connect editor, terminal, and agent to the same workspace.

**Exit criteria:** Unit and emulator tests pass; PTY/Bionic execution remains explicitly gated on valid ARM64 Android CI.

## Milestone 7 — Code editor

- Add real editor component.
- Add syntax highlighting and multi-file tabs.
- Add save/reload/external modification handling.

**Exit criteria:** Editor UI, persistence, and file synchronization tests pass in Actions.

## Milestone 8 — Package manager and Git

- Add package search/install/update/remove UI.
- Add runtime inventory.
- Add Git status/diff/commit/branch/remote operations.

**Exit criteria:** Unit and emulator tests pass; real package/Git binary tests require a validated Android runtime in Actions.

## Milestone 9 — Preview and development servers

- Add process/session management for development servers.
- Add port detection.
- Add local preview.

**Exit criteria:** Localhost/server integration tests pass in the configured Android CI environment.

## Milestone 10 — Security, performance, and packaging

- Complete security review.
- Measure memory/storage/initial APK size.
- Validate release signing in Actions.
- Verify artifact checksums.
- Verify third-party notices.

**Exit criteria:** Release-candidate CI succeeds and known platform limitations are documented.

## Milestone 11 — Release candidate

- Build one signed APK.
- Produce checksums and release notes.
- Publish to a draft GitHub Release first.
- Review artifact contents and notices.

**Exit criteria:** A reproducible, signed, installable single APK is available from GitHub Releases and the release workflow is green.

---

# 23. DOCUMENTATION TO MAINTAIN

Maintain these documents as the implementation evolves:

```text
docs/
  PROJECT_AUDIT.md
  ARCHITECTURE.md
  IMPLEMENTATION_ROADMAP.md
  CI_CD.md
  ANDROID_COMPATIBILITY.md
  TERMUX_RUNTIME.md
  OPENCODE_BIONIC.md
  AI_INTEGRATION.md
  TERMINAL_AND_PTY.md
  WORKSPACE_FILESYSTEM.md
  PACKAGE_MANAGEMENT.md
  SECURITY.md
  LICENSE_INVENTORY.md
  THIRD_PARTY_NOTICES.md
  TESTING.md
  RELEASE.md
  RISK_REGISTER.md
```

Also maintain:

- A machine-readable runtime pin manifest.
- Dependency lockfiles where supported.
- GitHub Actions workflow documentation.
- A compatibility table for Android versions, ABIs, runtime dependencies, and tested features.

Never describe a planned or mocked feature as implemented.

---

# 24. ENGINEERING RULES

1. Inspect before modifying.
2. Preserve existing working code.
3. Use modular boundaries.
4. Pin critical dependencies.
5. Prefer upstream-compatible integration.
6. Do not create unnecessary forks.
7. Do not rely on glibc binaries for the Bionic runtime.
8. Do not require root.
9. Do not require PRoot/Chroot as the primary runtime.
10. Do not require a separately installed Termux app.
11. Do not require a remote computer or SSH connection.
12. Keep one PocketDev APK as the user-facing product.
13. Do not run PocketDev builds/tests on the phone.
14. Use GitHub Actions for every build/test/analysis/package operation.
15. Do not silently downgrade a requirement when CI is difficult.
16. Do not claim ARM64/Bionic compatibility without actual evidence.
17. Do not fake terminal, editor, package manager, or AI functionality.
18. Do not silently install packages or execute destructive commands.
19. Keep secrets out of source code and logs.
20. Record technical decisions and unresolved risks.
21. Add tests for important behavior.
22. Verify every release artifact.
23. Report failures honestly.
24. Never use a local build as a fallback.

---

# 25. FIRST RESPONSE REQUIRED FROM THE AGENT

After reading this document, do not immediately generate a large amount of code.

First respond with a concise execution plan containing:

1. Confirmation that you understand the GitHub-Actions-only build/test rule.
2. Current repository path, branch, and remote (if discoverable).
3. A summary of the existing application architecture.
4. Existing GitHub Actions workflows and their current role.
5. Whether GitHub authentication and Actions access appear available.
6. The most important architectural unknowns.
7. The first milestone you will implement.

Then perform the repository audit and implement Milestone 0.

Do not ask the user questions whose answers can be discovered from the repository, upstream documentation, or GitHub Actions. Ask only when a decision requires product-owner approval, credentials, a paid service, a destructive change, or a genuine product trade-off.

---

# 26. FINAL PRODUCT ACCEPTANCE STATEMENT

PocketDev is successful when a user can install one APK on a supported Android ARM64 device and use it as an integrated development workstation:

- The runtime initializes inside PocketDev.
- The user can create and manage projects.
- A real editor can read and modify project files.
- A real terminal can execute commands through a PTY.
- The package manager can provision compatible tools.
- OpenCode Bionic can operate as the coding agent.
- The agent can make controlled changes to project files.
- Git can manage the project.
- Compatible development servers can run locally and be previewed.
- The experience does not require a separate Termux app, root, PRoot, a remote computer, or SSH setup.

The PocketDev application itself must be compiled, analyzed, tested, packaged, signed, and released through GitHub Actions. The Android phone is the lightweight coding/control device, not the build server.

**Build the actual workstation. Do not build a mockup of the workstation.**

---

# 27. PRIMARY TECHNICAL REFERENCES

Use these as starting points, but re-check their current state and version before implementation.

## PocketDev runtime foundations

- Termux app: https://github.com/termux/termux-app
- Termux packages: https://github.com/termux/termux-packages
- Termux website: https://termux.dev/
- Termux app license: https://github.com/termux/termux-app/blob/master/LICENSE.md
- Termux shared license: https://github.com/termux/termux-app/blob/master/termux-shared/LICENSE.md

## OpenCode Bionic

- OpenCode Bionic: https://github.com/bd-loser/opencode-bionic
- Bionic architecture: https://github.com/bd-loser/opencode-bionic/blob/main/docs/ARCHITECTURE.md
- Bionic build notes: https://github.com/bd-loser/opencode-bionic/blob/main/docs/BUILDING.md
- Bionic versions/pins: https://github.com/bd-loser/opencode-bionic/blob/main/versions.json
- Bun Termux: https://github.com/bd-loser/bun-termux
- OpenTUI Android fork: https://github.com/bd-loser/opentui
- Upstream OpenCode: https://github.com/anomalyco/opencode
- OpenCode documentation: https://opencode.ai/docs/
- OpenCode API: https://opencode.ai/docs/api
- OpenCode SDK: https://opencode.ai/docs/build/sdk/

## GitHub Actions and Android CI

- GitHub-hosted runner specifications: https://docs.github.com/en/actions/reference/runners/github-hosted-runners
- GitHub Actions billing: https://docs.github.com/en/actions/concepts/billing-and-usage
- GitHub Actions security hardening: https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
- Android emulator runner: https://github.com/ReactiveCircus/android-emulator-runner
- Android emulator hardware acceleration: https://developer.android.com/studio/run/emulator-acceleration

---

**End of master prompt.**

# PocketDev — Dependency Inventory

> Generated/maintained manually from `gradle/libs.versions.toml`. Update when pins change.

## Build toolchain

| Component | Version | Notes |
| --- | --- | --- |
| Gradle wrapper | 9.3.1 | Distribution checksum pinned in `gradle-wrapper.properties` |
| Android Gradle Plugin | 9.1.1 | Required minimum for the 2026.11 AndroidX line; `android.newDsl=false` and `android.builtInKotlin=false` in `gradle.properties` keep the AGP 8.x DSL and external Kotlin Android plugin |
| Kotlin | 2.4.21 | Includes Compose compiler plugin |
| JDK (CI) | Temurin 17 | Toolchain for all modules |
| compileSdk | 37 | `platforms;android-37.0` (minor-versioned package; no bare `android-37`) |
| targetSdk | 36 | Android 16; runtime behavior opt-in unchanged |
| minSdk | 29 | Android 10 (per master prompt §2.5) |
| Build tools (CI) | 36.0.0, 37.0.0 | Installed by `verify.yml` |

## Runtime dependencies (`:app`)

| Artifact | Version | License (verify before release) |
| --- | --- | --- |
| androidx.core:core-ktx | 1.19.1 | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.11.1 | Apache-2.0 |
| androidx.lifecycle:lifecycle-runtime-compose | 2.11.1 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.11.1 | Apache-2.0 |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 |
| androidx.navigation:navigation-compose | 2.10.2 | Apache-2.0 |
| androidx.compose:compose-bom | 2026.11.01 | Apache-2.0 |
| androidx.compose.ui:ui (+graphics, tooling-preview) | BOM | Apache-2.0 |
| androidx.compose.material3:material3 | BOM | Apache-2.0 |
| androidx.compose.material:material-icons-core | BOM | Apache-2.0 |

## Test dependencies

| Artifact | Version | Scope |
| --- | --- | --- |
| junit:junit | 4.13.2 | `:core` and `:app` unit tests |

## CI actions (pinned to commit SHAs)

| Action | Tag | SHA |
| --- | --- | --- |
| actions/checkout | v5 | `fbc6f399…` |
| gradle/actions/wrapper-validation + setup-gradle | v5 | `07231958…` |
| actions/setup-java | v5 | `b6effb05…` |
| android-actions/setup-android | v3 | `9fc6c4e9…` |
| actions/upload-artifact | v5 | `330a01c4…` |

## Pinned but not linked (runtime)

- Termux bootstrap archive `bootstrap-2026.11.15-r1+apt.android-7` /
  `bootstrap-aarch64.zip` — downloaded at first run, SHA-256 pinned in
  `BootstrapPins`, never bundled in the APK.
- `terminal-emulator` / `terminal-view` — approved for reuse (ADR-0001) but no
  code copied yet.

## Not yet included (planned)

- `:runtime` Android downloader/exec wiring (after ADR-0002)
- OpenCode Bionic binary + Bun Termux + AndroidTUI/OpenTUI (Milestone 4)
- A real code editor component (Milestone 7)

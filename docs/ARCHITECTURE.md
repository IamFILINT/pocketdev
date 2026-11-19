# Android Application Foundation

## Stack Decision
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (declarative, modern, no XML)
- **Architecture:** MVVM with a single `MainActivity` hosting a NavHost
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35

## Module Structure
Single `:app` module for now. Will split into feature modules as the app grows:
- `:app` — Main application, navigation, theme
- `:core-ui` — Shared Compose components, theme
- `:core-data` — Repositories, data sources
- `:feature-dashboard` — Dashboard screen
- `:feature-projects` — Project list/management
- `:feature-settings` — Settings screen

## Screens (initial shell)
1. **Dashboard** — Overview of runtime status, quick actions
2. **Projects** — List of projects, create/open
3. **Settings** — App preferences, runtime config, about

## Navigation
- Bottom navigation bar with 3 tabs: Dashboard, Projects, Settings
- NavHost manages back stack

## Runtime Bootstrap
No runtime integration yet. The bootstrap manager will be added in Milestone 3.
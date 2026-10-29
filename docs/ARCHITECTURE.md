# Vortexa architecture

## Module graph

```
:app
 ├── :core:common      pure Kotlin helpers, DataStore settings, Hilt modules
 ├── :core:ui          Material 3 theme + shared composables (no feature logic)
 ├── :core:database    Room entities, DAOs, schemas
 └── :feature:*       downloader · browser · video · music · vault · settings
```

Rules that keep the graph acyclic:

- `:core:*` never depends on `:feature:*`.
- `:core:ui` may depend on `:core:common`; it never touches the database.
- A feature depends on `:core:ui` + `:core:common`, and on `:core:database` only
  if it really reads/writes tables (today only `:feature:downloader`).
- `:app` is the only module that knows routes and the bottom bar.

## Layers inside a feature

`Composable` → `ViewModel` (Hilt, `StateFlow<UiState>`) → `Repository`/DAO.
The UI never touches OkHttp, Room or Media3 directly; when those arrive they sit
behind a repository interface so the ViewModel stays testable on the JVM.

## Data

- Settings: DataStore (`vortexa_settings`), exposed as `Flow<UserPreferences>`.
- Downloads: Room `vortexa.db`, schema exported to
  `core/database/schemas` so every migration is reviewable.
- Secrets (site logins, proxy credentials) will use the Android Keystore, never
  DataStore in plain text.

## Threading

Repository work runs on injected dispatchers; ViewModels expose immutable state
and never block the main thread. `SharingStarted.WhileSubscribed(5_000)` keeps
flows alive through configuration changes without leaking work.

## Design system

Material 3 with explicit container colours (no default purple fallback), plus
`LocalVortexaExtraColors` for success/warning/download accents that Material 3
does not model. Wallpaper colours are opt-in via `ThemeMode.SYSTEM` + the
"Material You" switch.

## Build

Single version catalog (`gradle/libs.versions.toml`), AGP 9 built-in Kotlin with
a pinned KGP, JDK 17 everywhere. Release builds enable R8 and resource
shrinking; ProGuard rules live in `app/proguard-rules.pro`.

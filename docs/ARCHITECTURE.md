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

## Download engine

`feature/downloader/engine` is transport + disk only:

- `DownloadEngine.probe()` issues `Range: bytes=0-0` to learn the length,
  validators and whether ranges are honoured.
- `DownloadEngine.download()` plans ranges, pre-allocates the target file and
  writes each range at its absolute offset through a `FileChannel`. Ranges run
  in parallel coroutines; progress is sampled every 200 ms and emitted as a
  cold `Flow<DownloadEvent>`.
- Failures leave the partial file in place (never truncate), so a later run can
  resume. Retries use exponential backoff per range.
- If the server ignores `Range`, the engine falls back to a single connection.

Segment arithmetic and resume rules also exist in `rust/download-core`, tested
with the same vectors. Transport stays in Kotlin so HTTP/2, proxies and TLS
configuration are not re-implemented across a JNI boundary.

## Queue and persistence

`DownloadManager` (singleton, `:feature:downloader`) owns an application-scoped
`CoroutineScope` and one `Job` per download, so navigation never cancels a
transfer. Room is the single source of truth: the UI only renders what the DAO
emits. Progress is written at most once per second — frequent enough for a
crash to lose at most a second of work, rare enough not to hammer SQLite.

Resume state is a compact string per download:

```
0-524287:524288;524288-1048575:0;...
```

`SegmentCodec` encodes and decodes it without third-party JSON, drops corrupt
entries instead of throwing, and `MIGRATION_1_2` adds the column for databases
created by v0.2.0.

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

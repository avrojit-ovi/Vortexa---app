# Vortexa — Android download manager, ad-free browser, video & music player

[![Android CI](https://github.com/avrojit-ovi/Vortexa---app/actions/workflows/android.yml/badge.svg)](https://github.com/avrojit-ovi/Vortexa---app/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/avrojit-ovi/Vortexa---app?display_name=tag)](https://github.com/avrojit-ovi/Vortexa---app/releases/latest)
[![License](https://img.shields.io/badge/license-proprietary-lightgrey)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208)-3ddc84)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7f52ff)](https://kotlinlang.org)

**Vortexa** is a single Android app that replaces four: a **segmented download
manager**, an **ad-blocking browser**, a **gesture-driven video player** and an
**audiophile music player** with equaliser — plus a private vault that ties them
together. Built from scratch in Kotlin and Jetpack Compose (Material 3), with a
Rust core for the hot paths.

> **Status: early build (v0.2.0).** The foundation, theme, settings and the
> download engine are working and tested; the browser and both players are the
> next phases. Screens that are not implemented say so — they never show a fake UI.

## Download

Grab the signed APK from the [latest release](https://github.com/avrojit-ovi/Vortexa---app/releases/latest):

```bash
adb install vortexa-0.2.0-release.apk
```

`minSdk 26 (Android 8.0)` · `arm64-v8a`, `armeabi-v7a`, `x86_64` · no Play Services required.

## Why people look for an app like this

| What you want | What Vortexa does |
|---|---|
| A download manager that survives bad networks | Up to 32 parallel byte ranges, resume that validates ETag/Last-Modified before trusting a partial file, exponential-backoff retries, single-connection fallback when a server ignores `Range` |
| A browser without ads | Planned: own EasyList/uBlock-style filter engine on WebView, per-site allow list, media sniffer (phase 5) |
| A video player with real controls | Planned: Media3/ExoPlayer with HW/HW+/SW switch, gestures, subtitle styling (phase 6) |
| A music player with a serious EQ | Planned: graphic + parametric EQ via `AudioEffect`/`DynamicsProcessing`, later a native DSP (phase 7) |
| One app instead of four | Shared library, unified search, global mini-player, private vault (phase 8) |

## What works today (v0.2.0)

- **Multi-module Kotlin/Compose app** — 10 Gradle modules, Hilt, Room, DataStore, type-safe version catalog.
- **Material 3 theme** — light, dark, true-black **AMOLED**, and wallpaper colours (Material You).
- **Five-tab shell** — Downloads · Browser · Video · Music · More, with SlidingContent transitions.
- **Settings that actually work** — theme mode and dynamic colour persist in DataStore and repaint the app instantly.
- **Working download screen** — add a link, watch live progress and speed, pause/resume/remove. The queue lives in an application-scoped coroutine owner, so changing screens never cancels a transfer.
- **Segmented download engine** — range probing, parallel ranged download, progress flow with live speed, retry, checksum (SHA-256/MD5/SHA-1).
- **Crash-safe resume** — per-range offsets (`start-end:downloaded;…`) are persisted in Room every second, and a migration carries v1 databases forward.
- **Rust core** — range planning, resume decisions and streaming hashes, 21 unit tests, same test vectors as the Kotlin planner.
- **English + বাংলা** localisation, edge-to-edge, adaptive icon, RTL-ready.
- **29 JVM tests + 21 Rust tests**, Android Lint clean (0 errors).

Download engine tests run against a **real local HTTP server** (sockets, `206 Partial Content`, ETag, 500-then-200), not mocks:

```bash
./gradlew :feature:downloader:testDebugUnitTest   # 16 tests
cd rust/download-core && cargo test               # 21 tests
```

## Build from source

Requirements: JDK 17, Android SDK platform 37.

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew check testDebugUnitTest   # lint + unit tests
./gradlew :app:assembleRelease      # release APK (needs a keystore)
```

## Project layout

```
:app                  navigation shell, theme wiring, application
:core:common          formatters, filename sanitiser, DataStore settings
:core:ui              Material 3 design system + shared composables
:core:database        Room entities, DAOs, exported schemas
:feature:downloader   download engine + downloads screen
:feature:browser      (phase 5)
:feature:video        (phase 6)
:feature:music        (phase 7)
:feature:vault        (phase 8)
:feature:settings     working settings screen
rust/download-core    native core: ranges, resume validation, checksums
```

More: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md)

## Renaming / rebranding

Identity lives in one file — `gradle.properties`:

```properties
vortexa.appName=Vortexa
vortexa.appId=com.vortexa.app
vortexa.versionCode=2
vortexa.versionName=0.2.0
```

## Signing

`app/build.gradle.kts` reads an optional `keystore.properties` in the project
root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Both
`keystore.properties` and `.secret/` are git-ignored.

For CI releases set the repository variable `SIGNING_ENABLED=true` and these
secrets: `VORTEXA_KEYSTORE_BASE64`, `VORTEXA_STORE_PASSWORD`,
`VORTEXA_KEY_ALIAS`, `VORTEXA_KEY_PASSWORD`. A tag push (`v*`) then builds and
attaches a signed APK and AAB; without the variable the release job is skipped
instead of failing.

> Back up the keystore. Without it you cannot ship an update over an installed build.

## Distribution

GitHub Releases is the supported channel. **F-Droid cannot host Vortexa while
the licence is proprietary** — F-Droid only accepts free/open-source licences.

## Deliberate limits

Vortexa does **not** implement YouTube ad bypassing, background play of YouTube
content, Premium unlocking, or YouTube downloading. Those break YouTube's Terms
of Service and the anti-circumvention rules around protected content. The
optional Online Video module (phase 9) instead offers the official embed player,
a pluggable extractor interface for lawful sources and community APIs such as
SponsorBlock. It ships disabled by default.

## Licence

Proprietary, all rights reserved — see [LICENSE](LICENSE). Third-party
components keep their own licences (Apache-2.0, MIT, LGPL-3.0).

---

**Keywords:** android download manager · idm alternative android · 1dm alternative ·
adblock browser android · mx player alternative · poweramp alternative ·
jetpack compose app · kotlin download manager · segmented download · resume download ·
media3 exoplayer · android music player equalizer · torrent client android (planned)

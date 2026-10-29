# Vortexa

**Vortexa** is a single Android app that merges a download manager, an ad-filtering
browser, a full-featured video player and an audiophile music player — built
clean-room from public feature descriptions.

- Package: `com.vortexa.app`
- Min SDK 26 (Android 8.0) · target/compile SDK 37
- Kotlin 2.4.21 · Jetpack Compose (Material 3) · AGP 9.4.1 · Gradle 9.6.1
- License: **proprietary, all rights reserved** (see `LICENSE`)

## What works today (Phase 1)

| Area | State |
|---|---|
| Multi-module Gradle build (10 modules) | ✅ builds |
| Material 3 theme: light / dark / AMOLED / Material You | ✅ |
| 5-tab navigation (Downloads, Browser, Video, Music, More) | ✅ |
| Settings: theme mode + dynamic colour, persisted in DataStore | ✅ |
| Room database + `downloads` table and DAO | ✅ (schema exported) |
| Hilt dependency injection across all modules | ✅ |
| English + Bangla strings | ✅ |
| Unit tests (formatters, filename sanitizer, download status) | ✅ |
| Download engine, browser, players | ⏳ phases 2–7 |

Unimplemented screens show an explicit "Phase N" badge instead of a fake UI.

See [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md) for the full plan and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for module boundaries.

## Build

Requirements: JDK 17, Android SDK with platform 37.

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew check testDebugUnitTest   # lint + unit tests
./gradlew :app:assembleRelease      # release APK (needs a keystore, see below)
```

The debug build installs alongside release builds because its application id is
`com.vortexa.app.debug`.

## Renaming the app

Everything is derived from `gradle.properties`:

```properties
vortexa.appName=Vortexa
vortexa.appId=com.vortexa.app
vortexa.versionCode=1
vortexa.versionName=0.1.0-phase1
```

Change those four values and the label, application id and `@string/app_name`
follow. Kotlin namespaces under `com.vortexa.*` and the app icon must be renamed
separately if the package changes.

## Signing

`app/build.gradle.kts` reads an optional `keystore.properties` in the project
root:

```properties
storeFile=.secret/vortexa-release.jks
storePassword=...
keyAlias=vortexa
keyPassword=...
```

Both `keystore.properties` and `.secret/` are git-ignored. For CI, add the
GitHub Actions secrets used by `.github/workflows/android.yml`:
`VORTEXA_KEYSTORE_BASE64` (`base64 -w0 .secret/vortexa-release.jks`),
`VORTEXA_STORE_PASSWORD`, `VORTEXA_KEY_ALIAS`, `VORTEXA_KEY_PASSWORD`.

> Keep the keystore backed up. Without it you cannot ship an update over an
> already-installed build.

## Distribution

GitHub Releases (`.github/workflows/android.yml`, tag `v*`) is the supported
channel. **F-Droid cannot host Vortexa while the licence is proprietary** —
F-Droid only accepts free/open-source licences. If that matters, switch the
licence first (Apache-2.0 is the least surprising choice for this stack).

## Deliberate limits

Vortexa does **not** implement YouTube ad bypassing, background play of YouTube
content, Premium unlocking, or YouTube downloading. Those break YouTube's Terms
of Service and the anti-circumvention rules around protected content. The
optional Online Video module (phase 9) will instead offer the official embed
player, a pluggable extractor interface for lawful sources, and community APIs
such as SponsorBlock. It ships disabled by default and can be removed at build
time.

## Third-party licences

Room (Apache-2.0), OkHttp (Apache-2.0), Media3 (Apache-2.0), Hilt (Apache-2.0),
libtorrent via jlibtorrent (MIT) and FFmpeg (LGPL-3.0 build only) will be listed
in the in-app "Open source licences" screen once those phases land. No GPL build
of FFmpeg is used.

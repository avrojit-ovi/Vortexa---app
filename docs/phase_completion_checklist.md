# Phase completion checklist (Vortexa)

Scope:
- Cover every phase listed across FEATURES.md, docs/FEATURE_MATRIX.md, and
  docs/STILL_TO_DO_PLAN.md.
- Close only what can be finished as code/tests/docs in this workspace.
- Anything that depends on a real device, a native library build, or a later
  manual pass gets marked clearly and left for the next step.

Legend:
- ✅ done
- 🚧 active / partially present
- ⏳ planned backlog
- ⛔ intentionally not built
- ⚠️ needs device/native/playwright verification, not closable here

---

## 1. App shell
- [x] Multi-module Kotlin/Compose app: `:app`, `:core:common`, `:core:ui`,
      `:core:database`, `:feature:*`
- [x] Hilt DI, type-safe version catalog (`gradle/libs.versions.toml`)
- [x] 5-tab shell: Downloads · Browser · Video · Music · More
- [x] Theme: light / dark / AMOLED, dynamic color, Bangla + English
- [x] Settings screen (theme + dynamic color), persistence, live repaint
- [x] CI-ready structure: build + tests + lint contrast exists
- [x] Signed release pipeline exists in docs/README
Status: done

---

## 2. Download engine
- [x] Segmented download engine, 1–32 parts, absolute-offset FileChannel writes
- [x] Range probe, validators, unsafe-resume restart
- [x] Parallel ranges, single-connection fallback, exponential backoff retry
- [x] Streaming SHA-256 / MD5 / SHA-1
- [x] Rust core for segment/resume/checksum, with matching test vectors
- [x] Room resumed-state string format and codec
- [ ] SD-card / SAF custom folder (planned)
- [ ] Custom destination picker (planned)
- [ ] Expired-link refresh / mirror URLs (planned)
Status: done on the core; SD-card/custom folder/mirror are explicitly planned

---

## 3. Downloads UI & service
- [x] Downloads list, add link, live speed, pause/resume/remove
- [x] Application-scoped queue that survives screen changes
- [x] `dataSync` foreground service + aggregate notification + Pause all
- [x] Room migration v1→v2 for existing installs
- [x] Categories / batch links / speed cap / schedule / proxy path exist in code
- [ ] 아무것도 externel verification of on-device foreground behavior here
Status: done in code; live on-device behavior needs a device pass

---

## 4. Torrent + adaptive download
- [ ] HLS/DASH download path + FFmpeg file merge
- [ ] Torrent engine via native lib (jlibtorrent/LGPL-class dependency)
- [ ] Per-protocol engine selection + unified queue
- [x] Placeholder/chip language exists for “phase 4” in browser strings
Status: planned backlog; not implemented in code yet

---

## 5. Browser
- [x] Multi-tab WebView, max 8 tabs, one live WebView, per-tab state
- [x] Own adblock engine (EasyList/uBlock-style parser)
- [x] Media sniffer + download hook
- [ ] Incognito mode as a first-class setting/UI path
- [ ] Bookmarks as persistent feature UI
- [ ] History as persistent feature UI
- [x] Browser feature module and strings exist; browser strings note HLS/DASH
      open in player and whole-playlist save arrives in phase 4
Status: core browser is present; incognito/bookmarks/history still planned
        in FEATURES.md

---

## 6. Video / player
- [x] Library of downloaded media (files on disk, not a second table)
- [x] Media3 ExoPlayer in MediaSessionService: background play, notification,
      headset keys
- [x] Resume where you stopped per file, with credits guard
- [x] Speed presets, fit/fill, ±10s, PiP
- [x] HW/SW decoder switch (AUTO = hardware with software fallback)
- [x] Kids Lock with PIN
- [x] Gestures: brightness / volume / seek
- [x] Subtitle formats, size/background styling, delay
- [x] Sync lyrics from sidecar LRC files
- [ ] syllable-level highlight for synced lyrics (optional polish)
Status: implemented; full on-device pass still recommended before release

---

## 7. Music
- [x] Neutral online layer: Music tab can play a direct link (file/HLS/DASH)
- [x] Optional extractor plugins via DexClassLoader, API v1 contract
- [x] Off by default, plugin install/remove/reload, isolated failure handling
- [ ] Queue for online streams
- [ ] Mini-player for online streams
- [ ] EQ for online streams
- [ ] Media3 gapless / crossfade
- [ ] EQ/DSP via AudioEffect/DynamicsProcessing, later C++/Oboe
- [ ] Library scan, tags, lyrics, widgets, Android Auto
- [ ] Hi-res / USB DAC
Status: playback foundation exists, EQ/stream queue/mini-player/library/widget
        Android Auto/hi-res are planned backlog

---

## 8. Online video / music module (opt-in)
- [x] Settings toggle + Bangla caveat for online sources
- [ ] YouTube extractor shipped directly from this repo (⛔ not built on purpose)
- [ ] Online Video module as a separate opt-in feature (phase 9)
Status: opt-in toggle exists; direct YouTube extraction intentionally not built

---

## 9. Vault + unified search + mini-player
- [x] Private vault with real encryption (Keystore-backed)
- [x] Vault screen and strings
- [ ] Unified search across downloads + music + more
- [ ] Global mini-player surface shared across features
Status: vault exists; unified search + shared mini-player are backlog

---

## 10. Polish & release
- [ ] Device test pass
- [ ] Baseline Profile
- [ ] Localisation sweep
- [ ] `v1.0.0` APK + checksums on GitHub Releases
Status: planned

---

## Cross-cutting non-phase items
- [x] Multi-module scaffold keeps `:core:*` independent from `:feature:*`
- [x] `:core:ui` depends on `:core:common`, never touches the database
- [x] Settings: DataStore, exposed as Flow
- [x] Downloads: Room with exported schema under `core/database/schemas`
- [x] Secrets: Keystore intent noted for site logins and proxy credentials
- [ ] Real Keystore-backed secret flow for proxy password is wired in code but
      still needs a device pass to confirm end-to-end

---

## Verification gates before claiming “all done”

### 1) on-disk verification (this workspace can do it)
- [ ] Clean build compiles from `./gradlew assemble`
- [ ] Unit tests pass from `./gradlew test`
- [ ] Android Lint is clean where required (`./gradlew lint`)
- [ ] New/changed docs reflect current phase status and do not claim features
      that are still planned
- [ ] `docs/STILL_TO_DO_PLAN.md` matches FEATURES.md and FEATURE_MATRIX.md
      after any changes

### 2) device/native/web verification (not closable here unless remote runtime
    is available)
- [ ] Foreground service + notification behavior on a real Android device
- [ ] Video player resume/PIP/decoder switch/Kids Lock on device
- [ ] Vault encryption/decryption round trip on device
- [ ] Native download-core artifacts build and load correctly when needed
- [ ] Browser flow on a live URL if any web verification is required
- [ ] any temp DIR or perm path permission checks for downloads on device

---

## Current blocker map (what can be closed now vs later)

Closeable now:
- compile / unit-test / lint fixes
- doc alignment
- small in-module gaps that do not require a device

Defer to next step:
- phase 4 HLS/DASH + FFmpeg merge
- phase 5 incognito / bookmarks / history
- phase 7 stream queue / mini-player / EQ / library / widgets / Android Auto /
  hi-res
- phase 8 unified search + shared mini-player
- phase 10 device test pass, Baseline Profile, localisation sweep, release APK +
  checksums

Do not close as done here:
- anything that depends on a real Android device
- anything that depends on a native library build not verifiable here
- anything that depends on end-to-end web interaction unless a browser runtime
  is attached

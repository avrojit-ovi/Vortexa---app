# Features

Status values: `done` (on-device verified + tests + reachable in UI) · `implemented`
(code + tests, not yet run on a device) · `in progress` · `planned` · `blocked`.

Risk values live in [LEGAL_RISK.md](LEGAL_RISK.md); this file only labels them.

Distribution: GitHub Releases + F-Droid only.

## 1. App shell · done
| Feature | Status | Risk |
|---|---|---|
| Multi-module architecture (`core/*`, `feature/*`), Hilt, Material 3, 5-tab shell | done | LOW |
| Theme modes (system/light/dark/AMOLED), dynamic colour, Bangla + English | done | LOW |
| Settings screen, CI (build + tests + lint), signed release pipeline | done | LOW |

## 2. Download engine · done
| Feature | Status | Risk |
|---|---|---|
| Segmented engine (1–32 parts), absolute-offset writes, per-part resume in Room | done | LOW |
| Validator-checked resume (ETag/Last-Modified/size), restart when unsafe | done | LOW |
| Parallel ranges, single-connection fallback, exponential backoff retry | done | LOW |
| Streaming SHA-256/MD5/SHA-1 | done | LOW |
| Rust download core (segment planning, resume rules, checksums) | done (host `cargo test`) | LOW |
| Rust JNI `.so` inside the APK | blocked — NDK download stalls in this environment | LOW |

## 3. Downloads UI & service · done
| Feature | Status | Risk |
|---|---|---|
| Downloads list, add link, live speed, pause/resume/remove | done | LOW |
| Application-scoped queue (survives screen changes) | done | LOW |
| `dataSync` foreground service + aggregate notification + Pause all | done | LOW |
| Room v1→v2 migration (existing installs keep their data) | done | LOW |
| Speed limiter: one byte budget per download shared by every range, read from settings | implemented + 6 unit tests | LOW |
| Daily schedule: queued downloads only start inside the window (cross-midnight supported), checked every minute | implemented + 5 unit tests | LOW |
| Proxy/auth, categories, SD-card (SAF), batch import | planned | LOW |

## 4. Torrent + adaptive download · planned
| Feature | Status | Risk |
|---|---|---|
| Torrent engine (libtorrent/jlibtorrent, LGPL) | planned | HIGH |
| HLS/DASH download, FFmpeg (LGPL build) merge | planned | MED |
| Per-protocol engine selection + unified queue | planned | MED |

## 5. Browser · active
| Feature | Status | Risk |
|---|---|---|
| Multi-tab WebView (per-tab history via `saveState`, cap 8), URL/search bar, back/forward, tab sheet | implemented + 9 unit tests | LOW |
| Own adblock engine (EasyList/uBlock subset: `||`, `@@`, wildcards, anchors) + bundled rules, hooked into WebView requests, shield toggle persisted | implemented + 8 unit tests | MED |
| Media sniffer + download hook: media requests recognised by URL/MIME, direct files go to the download queue, HLS/DASH open in the player | implemented + 5 unit tests | MED |
| Incognito, bookmarks, history | planned | LOW |

**Deliberate limits.** Rules are matched by URL only: cosmetics (`##`), scriptlets,
regex rules and `$` options are counted and skipped rather than half-applied, so
the engine never claims coverage it does not have. Third-party cookies are off and
mixed content is never allowed — the two cheapest real-world tracker paths a
WebView can close without breaking sites.

## 6. Player · implemented
| Feature | Status | Risk |
|---|---|---|
| Library of downloaded media (files on disk) | implemented | LOW |
| Media3 player in a `MediaSessionService`, background audio + headset keys | implemented | LOW |
| Resume position per file (credits guard) | implemented + unit tests | LOW |
| Speed presets, fit/fill, ±10 s seek, picture-in-picture | implemented | LOW |
| Sidecar subtitles (`movie.srt`, `movie.en.srt`, `movie.bn.ass`) + CC on/off | implemented + 5 unit tests | LOW |
| Gestures: horizontal swipe seeks, left half = brightness, right half = volume, with a HUD | implemented + 8 unit tests | LOW |
| Subtitles drawn by Compose: three sizes, background on/off, +0…10 s delay (positive only — a negative shift needs the whole file) | implemented + 7 unit tests | LOW |
| Kids Lock: hides controls and gestures behind a 4-digit PIN, blocks back, subtitles keep showing | implemented + 3 unit tests | LOW |
| Decoder switch (hardware / software) — selector picks software first when asked; takes effect on the next video, because Media3 cannot re-init codecs on a live player | implemented | MED |

## 7. Music — active
| Feature | Status | Risk |
|---|---|---|
| Local library: scans the download folder for audio, reads title/artist/album/duration | implemented (no storage permission needed) | LOW |
| Local playback with the whole folder as the queue, through the shared session | implemented | LOW |
| Online search → play through the same session | implemented | MED |
| Lazy queue for online results, next on end | implemented | MED |
| Sleep timer (15/30/60 min) | implemented | LOW |
| Real EQ: platform `AudioEffect`, 5 logical bands, presets, persisted | implemented + unit tests | LOW |
| Synced lyrics from a sidecar `.lrc` next to the track, shown in the player | implemented + 8 unit tests | LOW |
| Gapless between queue items: local playback already hands ExoPlayer one playlist, so formats that carry gapless metadata play without a gap | implemented | LOW |
| Replay gain (needs tag parsing + a custom audio processor), crossfade (needs a custom sink), home-screen widget, Android Auto | planned | LOW |

## 8. Online video / music module — active
| Feature | Status | Risk |
|---|---|---|
| Layer (a) primary: NewPipeExtractor (`v0.24.8`, JitPack) → stream URL → Media3 | implemented, not device-verified | HIGH |
| Search in the Music tab (album/artist/duration/thumbnail), results become the play queue | implemented | HIGH |
| Layer (b) fallback: official YouTube IFrame embed in a WebView (`EmbedPlayerScreen`) | implemented, wired to open automatically when (a) throws | MED |
| Automatic switch (a)→(b): a failed resolve hands the item's `fallbackUrl` to the shell; no crash | implemented | HIGH |
| Layer (c): SponsorBlock (`sponsor.ajay.app`) — segments + "Skip sponsor" chip while inside one | implemented (client, parser, UI) | LOW |
| Layer (c): Return YouTube Dislike — like/dislike counts in the player bar | implemented (client, parser, UI) | LOW |
| Background play of resolved streams via `PlaybackService` (mediaPlayback + MediaSession + notification) | implemented (shared with video/music) | HIGH |
| Settings toggle + Bangla caveat for online sources | done | — |

**JavaScript engine (8d): resolved.** NewPipeExtractor `v0.24.8` ships
`org.mozilla:rhino:1.8.0` as a runtime dependency and its own `JavaScript` helper
uses Rhino directly, so signature deciphering needs no extra code from us —
verified on the `debugRuntimeClasspath`. What is still unverified is end-to-end
resolution on a real device/network; if a video yields no URL the embed fallback
opens instead of failing.

## 9. Vault · active
| Feature | Status | Risk |
|---|---|---|
| Vault files encrypted with AES-256-GCM, key in the Android Keystore, streamed in 64 KB chunks | implemented + 9 unit tests | LOW |
| Container: magic, encrypted name block, separate IV for the body — the list reads names without decrypting whole files, and a flipped byte is rejected | implemented | LOW |
| PIN gate: PBKDF2-HMAC-SHA256 (120k iterations, per-setup salt), never stored in clear | implemented + 6 unit tests | LOW |
| Fingerprint/face shortcut through the platform prompt (API 28+), PIN stays as the fallback | implemented, not device-verified | MED |
| Import from SAF, list, delete, open into the cache for the player | implemented | LOW |
| Unified search across finished downloads and local music (vault names stay hidden until it is unlocked, so search cannot bypass it) | implemented + 8 unit tests | LOW |
| Unified search across downloads/video/music, mini-player | mini-player implemented | LOW |

## 10. Polish & release · planned
| Feature | Status | Risk |
|---|---|---|
| Device test pass, Baseline Profile, localisation sweep | planned | LOW |
| `v1.0.0` APK + checksums on GitHub Releases | planned | LOW |

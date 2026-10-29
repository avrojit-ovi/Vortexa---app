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
| Speed limiter, proxy/auth, scheduler, categories, SD-card (SAF), batch import | planned | LOW |

## 4. Torrent + adaptive download · planned
| Feature | Status | Risk |
|---|---|---|
| Torrent engine (libtorrent/jlibtorrent, LGPL) | planned | HIGH |
| HLS/DASH download, FFmpeg (LGPL build) merge | planned | MED |
| Per-protocol engine selection + unified queue | planned | MED |

## 5. Browser · planned
| Feature | Status | Risk |
|---|---|---|
| Multi-tab WebView, incognito, bookmarks, history | planned | LOW |
| Own adblock engine (EasyList/uBlock syntax parser) | planned | MED |
| Media sniffer + download hook | planned | MED |

## 6. Player · implemented
| Feature | Status | Risk |
|---|---|---|
| Library of downloaded media (files on disk) | implemented | LOW |
| Media3 player in a `MediaSessionService`, background audio + headset keys | implemented | LOW |
| Resume position per file (credits guard) | implemented + unit tests | LOW |
| Speed presets, fit/fill, ±10 s seek, picture-in-picture | implemented | LOW |
| Sidecar subtitles (`movie.srt`, `movie.en.srt`, `movie.bn.ass`) + CC on/off | implemented + 5 unit tests | LOW |
| Gestures: horizontal swipe seeks, left half = brightness, right half = volume, with a HUD | implemented + 8 unit tests | LOW |
| Subtitle styling/delay, decoder switch, Kids Lock | planned | LOW |

## 7. Music — active
| Feature | Status | Risk |
|---|---|---|
| Local library: scans the download folder for audio, reads title/artist/album/duration | implemented (no storage permission needed) | LOW |
| Local playback with the whole folder as the queue, through the shared session | implemented | LOW |
| Online search → play through the same session | implemented | MED |
| Lazy queue for online results, next on end | implemented | MED |
| Sleep timer (15/30/60 min) | implemented | LOW |
| Real EQ: platform `AudioEffect`, 5 logical bands, presets, persisted | implemented + unit tests | LOW |
| Widgets, Android Auto, lyrics, gapless/crossfade, replay gain | planned | LOW |

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

## 9. Vault · planned
| Feature | Status | Risk |
|---|---|---|
| PIN + biometric lock, file encryption (AES-GCM, keys in Keystore) | planned | LOW |
| Unified search across downloads/video/music, mini-player | mini-player implemented | LOW |

## 10. Polish & release · planned
| Feature | Status | Risk |
|---|---|---|
| Device test pass, Baseline Profile, localisation sweep | planned | LOW |
| `v1.0.0` APK + checksums on GitHub Releases | planned | LOW |

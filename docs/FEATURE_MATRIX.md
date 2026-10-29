# Feature matrix

Status: ✅ done · 🚧 in progress · ⏳ planned · ⛔ not built on purpose

## Downloads (phase 2–4)
| Feature | Status |
|---|---|
| Segmented engine (1–32 parts) + queue + pause/resume UI | ✅ |
| Resume: ETag/Last-Modified/size validation + per-part offsets in Room | ✅ |
| Safe single-connection fallback without ranges | ✅ |
| Retry with exponential backoff | ✅  ·  queue, speed limiter ✅ · proxy/auth ✅ |
| Foreground service (dataSync) + aggregate notification with Pause all | ✅ |
| Torrent (jlibtorrent), HLS/DASH, FFmpeg merge | ⏳ |
| Checksums (SHA-256 / MD5 / SHA-1, streaming) | ✅  ·  categories ✅ · SD-card ⏳ |

## Browser (phase 5)
| Feature | Status |
|---|---|
| Multi-tab WebView, incognito, bookmarks, history | ⏳ |
| Own adblock engine (EasyList/uBlock parser) | ⏳ |
| Media sniffer + download hook | ⏳ |

## Video (phase 6)
| Feature | Status |
|---|---|
| Library of downloaded media (files on disk, not a second table) | ✅ |
| Media3 ExoPlayer in a `MediaSessionService`: keeps playing when the screen closes, notification + headset keys | ✅ |
| Resume where you stopped (per-file, credits guard) | ✅ |
| Speed presets, fit/fill, ±10 s, PiP | ✅ |
| HW/HW+/SW decoder switch, Kids Lock | ⏳ |
| Gestures (brightness/volume/seek), subtitle formats + styling + delay | ⏳ |

## Online sources (plugin architecture, opt-in)
| Feature | Status |
|---|---|
| `Extractor` contract: search, resolve, metadata, playlists | ✅ |
| Built-in direct-link extractor (progressive, HLS, DASH) | ✅ |
| User-installed extractor plugins (separate APK, DexClassLoader, API v1) | ✅ |
| Off by default, plugin install/remove/reload + warning in Settings | ✅ |
| Broken plugin → readable error, never a crash | ✅ (failure isolation unit-tested) |
| Music tab search + play through the shared session (background, lockscreen, sleep timer) | ✅ |
| Queue, mini-player, EQ for online streams | ⏳ next |

## Music (phase 7) — planned next

| Feature | Status |
|---|---|
| Media3 playback, gapless, crossfade, replay gain | ⏳ |
| EQ/DSP via `AudioEffect`/`DynamicsProcessing`, later C++/Oboe | ⏳ |
| Library scan, tags, lyrics, widgets, Android Auto | ⏳ |
| Hi-res / USB DAC | ⏳ |

## Cross-cutting
| Feature | Status |
|---|---|
| Multi-module scaffold, theme, navigation, CI | ✅ |
| Settings (theme + dynamic colour) | ✅ |
| Bangla + English localisation | ✅ |
| Vault, unified search, mini-player (phase 8) | ⏳ |
| Online Video module, opt-in (phase 9) | ⏳ |
| YouTube ad bypass / background play / download | ⛔ |
| YouTube / YouTube Music extraction shipped by this repository | ⛔ (breaks YouTube's terms; the repo itself ships no service-specific extractor — a user-installed plugin is the user's own decision) |

# Extractor plugin API (v1)

Vortexa's core contains **no service-specific code**. It knows one interface,
`com.vortexa.core.media.Extractor`, and loads optional plugins that implement it.
Everything else — search, metadata, stream resolution — is the plugin's job.

## The contract

```kotlin
interface Extractor {
    val id: String              // unique, reverse-DNS: "com.example.mysource"
    val displayName: String
    val version: String
    val capabilities: Set<Capability>   // SEARCH, RESOLVE, PLAYLISTS

    suspend fun search(query: MediaQuery): List<MediaTrack>
    suspend fun resolve(track: MediaTrack): StreamLink
    suspend fun playlists(): List<MediaPlaylist> = emptyList()
    suspend fun playlistItems(playlistId: String): List<MediaTrack> = emptyList()
}
```

`resolve` returns a `StreamLink(url, mimeType, isHls, headers, expiresAtEpochMs)`.
The player opens that URL with Media3, so HLS, DASH manifests and progressive
files all work without extra glue.

## Packaging

* A plugin is **a separate APK**, built as its own Gradle module, never bundled
  in the Vortexa build.
* The APK contains a class `com.vortexa.plugin.Entry` implementing
  `com.vortexa.core.media.plugin.PluginEntry`:

```kotlin
class Entry : PluginEntry {
    override val apiVersion = 1
    override val name = "My source"
    override fun createExtractors(): List<Extractor> = listOf(MyExtractor())
}
```

* The user installs it from **Settings → Online sources → Add extractor plugin**
  (a `.apk` picked with the system file picker). It is copied into the app's
  private folder and loaded with `DexClassLoader`, whose parent is the app class
  loader — so the `Extractor` interface has one identity at runtime.
* `PluginHost.API_VERSION` is bumped only on breaking changes; a plugin built
  against another version is rejected with a readable message instead of
  half-working.

## Safety rules

* Online sources are **off by default**; with the switch off no plugin is
  loaded and the Music tab's search never appears.
* Every call is wrapped: a plugin that throws contributes one red line
  (`ExtractorFailure`) and the other sources keep working. The app never
  crashes because of a plugin.
* `search`/`resolve` are cancellable coroutines; the UI survives rotation.
* Importing an APK executes that code with the app's permissions. That is why
  the feature is opt-in, per-plugin visible in settings, and removable.

## Licensing

`LICENSE` in this repository is **GPL-3.0-or-later**.

**বাংলা:** অ্যাপ এখন GPL-3.0-or-later, তাই GPL-লাইসেন্সি কোড (যেমন
NewPipeExtractor) সরাসরি link করলেও লাইসেন্সের দ্বন্দ্ব নেই — শর্ত একটাই:
সোর্স কোড ও পরিবর্তনগুলোও GPL-3.0-এ প্রকাশ্যে রাখতে হবে।

**English:** the app is GPL-3.0-or-later, so linking another GPL-3.0 library no
longer creates a licence conflict; publishing the combined source satisfies it.
The separate-plugin design is still kept for two other reasons:

1. **Build hygiene** — the core stays service-agnostic, so a plugin can be
   dropped, updated or removed without touching the app, and a plugin failure is
   contained to one source.
2. **Store separation** — a store build can ship without any plugin while the
   APK you install yourself still adds one.

No licence arrangement changes a service's terms of use: a plugin that scrapes
a service can still break that service's terms, which is why the toggle ships
off with a warning and the choice stays with the person installing it.

## What the core does on its own

`DirectLinkExtractor` (`builtin.direct`) needs no plugin: paste an http(s) URL
to a media file, an HLS playlist (`.m3u8`) or a DASH manifest (`.mpd`) and it
plays. It never scrapes a page.

# Legal risk notes

This file explains *why* a feature carries a risk label. It never removes a
feature and it never changes scope — the feature board is
[FEATURES.md](FEATURES.md).

Labels: **LOW** (no meaningful exposure) · **MED** (civil/contract exposure if
misused or if a claim is made) · **HIGH** (likely contract breach and a real
chance of a takedown or anti-circumvention claim).

Distribution is GitHub Releases + F-Droid. Store policy is not a factor.

---

## HIGH — Stream extraction from YouTube / other services (Section 8 layer a)

NewPipeExtractor resolves playable stream URLs by reading a service's internal
player response and deciphering the signature (`n`/`sig`) parameters. Consequences:

* **Contract:** YouTube's Terms of Service forbid accessing content other than
  through the public interface, and specifically forbid downloading or
  separating audio from video. Using the extractor is a breach of those terms,
  which can lead to account termination and civil claims.
* **Anti-circumvention:** deciphering a technical protection measure can be
  treated as circumvention under laws such as DMCA §1201 (US) and the EU
  InfoSoc directive. Distributing the resulting APK publicly raises the exposure
  from "use" to "distribution".
* **Practical:** the extraction breaks whenever the service changes; expect
  breakage, not stability. This is why layer (b) exists as a fallback.

The user accepts this risk for personal use; the code keeps working either way.

## MED — Ad blocking

Ad blocking sits between a site and its revenue. It is legal to filter traffic
on your own device in most jurisdictions, but it breaks site terms, and a
company can still send a claim. Building our own parser (rather than shipping
another project's list) keeps the licensing side clean.

## HIGH — Torrent engine

BitTorrent itself is lawful, but the tool is content-neutral: downloading
copyrighted material without permission is infringement, and in some
jurisdictions merely uploading (which torrents do by design) is treated as
distribution. Seeding is therefore the specific risk, not the protocol.

## MED — Media sniffer + download hook

Detecting media URLs in a page and downloading them can copy protected content.
The feature follows the same rule everywhere: it downloads what the user chooses;
it does not decrypt DRM and it does not ship keys.

## MED — FFmpeg merge

FFmpeg's LGPL build is fine to bundle when it is dynamically linked and its
licence and source offer travel with the app. Using GPL-only components (e.g.
libx264) in the same binary would force the whole app to GPL — which it now is —
but shipping encoders is still an export-control question in some jurisdictions.

## LOW — GPL-3.0 for the whole app

The app is now GPL-3.0-or-later. That is a licensing obligation (publish the
source and modifications), not a legal risk to the user. Plugins can link GPL
code directly; the separate-APK design is kept for build hygiene, not because of
the licence.

## LOW — Local playback, equalizer, vault

Local files, an audio effect on our own session and encryption of our own files
carry no meaningful third-party exposure beyond ordinary data-protection care
(don't lose the vault key).

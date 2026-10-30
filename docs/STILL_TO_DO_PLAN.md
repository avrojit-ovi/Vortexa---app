# Remaining work plan

Selected backlog (in order):
1. Downloads flavor: HLS/DASH + FFmpeg file merge, plus any download-core plumbing those need.
2. Downloads flavor: torrents via a native library (flagged as device/native-verification dependent).
3. Online sources: queue + mini-player + EQ for online streams.
4. Music (phase 7): Media3 gapless/crossfade, EQ/DSP, library scan, tags/lyrics/widgets, Android Auto, hi-res/USB DAC.
5. Vault + unified search + mini-player (phase 8).
6. Online Video module, opt-in (phase 9).

Verification plan:
- Compile and run unit/build verification where possible.
- For anything that needs a real device or a native lib build, note it as not-self-verified here.
- If web-related checks appear later, use agent-browser/Playwright-style checks.

Current workspace limits (accepted):
- Native libs such as jlibtorrent/FFmpeg and real-app runs are not fully verifiable here. They will be built as far as the workspace allows and flagged where they need a device or Cloud VM.

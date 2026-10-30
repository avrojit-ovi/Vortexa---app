# Verification status (remaining-work sweep)

Last updated: 2026-10-30

Purpose:
- One place to record what is being checked, what has finished, what failed,
  and what is still pending.
- Anything closed as done stays done only if it is recorded here and verified.

Legend:
- started
- in-progress
- passed
- failed
- skipped
- pending-only

---

## 0. Prerequisites and environment

- Repository: Vortexa Android app
- Branch: main
- Workspace type: Windows dev checkout
- Recorded state before sweep:
  - Uncommitted changes exist across several modules.
  - Untracked docs and test/screenshot artifacts exist.
  - Gradle wrapper is present.
  - Java runtime is not available in the current shell session.

Status:
- started

---

## 1. Docs and backlog alignment

Items:
- [x] Consolidate phase scope into `docs/phase_completion_checklist.md`
- [x] Refresh `docs/STILL_TO_DO_PLAN.md` from current code/commits
- [x] Refresh `docs/FEATURE_MATRIX.md` status wording to match actual state
- [x] Refresh `FEATURES.md` phase wording to match actual state
- [ ] Final doc consistency pass after build/test results land

Status:
- started
- in-progress for final pass

---

## 2. Uncommitted and untracked work cleanup

Items:
- [ ] Decide commit vs discard for modified files
- [ ] Decide inclusion for untracked files:
  - `docs/STILL_TO_DO_PLAN.md`
  - `docs/phase_completion_checklist.md`
  - `core/database/schemas/.../3.json`
  - `core/database/.../BrowserEntryDao.kt`
  - `core/database/.../BrowserEntryEntity.kt`
  - `feature/browser/.../BrowserLibrary.kt`
  - `feature/browser/.../BrowserHistoryPolicyTest.kt`
  - `screenshots/`
- [ ] Make working tree intentional before build verification

Status:
- pending-only

---

## 3. Build verification

Items:
- [ ] Resolve Java/runtime availability in the build environment
- [ ] Run Gradle wrapper health check and task listing if needed
- [ ] Build debug or release artifact as required
- [ ] Capture build outcome and failing tasks if any

Status:
- pending-only

---

## 4. Test verification

Items:
- [ ] Run unit tests where the environment allows
- [ ] Record which test suites ran
- [ ] Record failures and known limitations
- [ ] Decide whether any failure blocks closure

Status:
- pending-only

---

## 5. Lint and code-quality verification

Items:
- [ ] Run lint task if available in the environment
- [ ] Record lint outcome
- [ ] Record any warnings that must not be ignored

Status:
- pending-only

---

## 6. On-device / native / web items that cannot be fully closed here

Items:
- [ ] HLS/DASH download + FFmpeg merge
- [ ] Torrent engine via native library
- [ ] Incognito, bookmarks, history in browser feature
- [ ] Music stream queue / mini-player / EQ
- [ ] Music library scan, tags, lyrics, widgets, Android Auto, hi-res/USB DAC
- [ ] Unified search + shared mini-player
- [ ] Online Video opt-in module
- [ ] Device test pass, Baseline Profile, localisation sweep
- [ ] Release APK + checksums on GitHub Releases

Status:
- pending-only

---

## 7. Sign-off criteria

All done:
- docs are consistent with remaining-work plan
- working tree is intentional
- build, test, and lint outcomes are recorded
- un-closable items are explicitly flagged

Not done yet:
- everything under sections 2 through 6

Status:
- pending-only

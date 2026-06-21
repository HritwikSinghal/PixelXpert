# Project: PixelXpert Fork Maintenance
> Last updated: 2026-06-21 | Session: 3

## Overview
This is a maintained fork of [siavash79/PixelXpert](https://github.com/siavash79/PixelXpert)
(`origin` = `HritwikSinghal/PixelXpert`), a mixed Xposed+Magisk module for Pixel ROM
customizations. The goal is to carry a small set of **custom features** on top of upstream
and to **autobuild + publish artifacts** on every commit. (As of Phase 3 the fork has
intentionally diverged from upstream -- max cleanup, fork-pointed metadata, deleted
upstream CI -- so rebasing `patch` onto `canary` will conflict on those files; this was an
accepted trade-off, not the original "minimum divergence" stance.)

Branch strategy (decided 2026-06-20):
- **`canary`** stays a clean mirror of upstream `siavash79/PixelXpert:canary`. No custom work lands here.
- **`patch`** is the fork's default branch and carries all custom changes, kept on top of `canary`
  (rebase/merge canary into patch when syncing upstream).

CI strategy: a fork-only workflow triggers on push to `patch`, builds the release zip + APK via
Gradle, and uploads both as GitHub Actions artifacts named `PixelXpert-<branch>-<short7hash>.{zip,apk}`.
The existing upstream workflows clone from `siavash79/PixelXpert` directly, so they are NOT reused;
the fork CI uses `actions/checkout`. End state: pushing to `patch` always yields downloadable,
hash-named artifacts, and upstream syncs stay low-friction.

## Plan

### Phase 1: Fork Tracking & Branch Strategy
- [x] Create `claude/` tracking files (progress.md) -- this file
- [x] Register Long-Running Project + Fork Maintenance section in `CLAUDE.md`
- [x] Add `upstream` git remote (`siavash79/PixelXpert`); confirm `origin` is the fork
- [x] Create `patch` branch from current `canary`; push to `origin`
- [x] Set `patch` as the default branch on GitHub (gh)
- [x] Document the upstream-sync workflow (canary mirror -> patch rebase) in `CLAUDE.md`

### Phase 2: Fork Build CI
- [x] Create `.github/workflows/forkBuild.yml` triggered on push to `patch`
- [x] Use `actions/checkout` with submodules (not the upstream clone)
- [x] JDK 21 + Gradle cache setup matching the project toolchain
- [x] Signing-key prep with graceful fallback when secrets are absent
- [x] Gradle build producing the release Magisk zip + signed APK
- [x] Compute short commit hash + branch; rename to `PixelXpert-<branch>-<short7>.{zip,apk}`
- [x] Upload zip + APK as GitHub Actions artifacts
- [x] Validate workflow (real run on `patch`) -- Fork Build green end-to-end (run 27870238991), builds zip + APK and uploads both

### Phase 3: Repo & README Cleanup (2026-06-20)
User opted for max cleanup (accepting upstream divergence) + repoint to fork. History
rewrite (Phase F in plan) was recommended against and SKIPPED -- it breaks the canary-mirror
model. Builds/tests skipped locally this session; validated via CI push.
- [x] README: A16-QPR1+ only, drop A12/A13 + A13-A16 rows, repoint links/banner to fork
- [x] Delete obsolete `.github/workflowFiles/ReleaseNotesTemplate.md`
- [x] Versioning rewrite: typed `VersionInfo` SoT, format-aware writers (no regex),
      split read/increment (kill double +1), GitTagProvider fails loudly (no "Error" string)
- [x] Repoint module.prop/JSON metadata URLs to the fork
- [x] CI: delete 6 upstream-only workflows + 3 orphaned scripts; forkBuild.yml -> temurin
      + concurrency; add `.github/dependabot.yml` (gradle + github-actions)
- [x] Build hygiene: debug no longer signed with release key; log keystore-load failures;
      migrate off deprecated `tasks.whenTaskAdded`; build-cache on; heap 2G;
      material/swiperefreshlayout -> stable; document pytorch pin
- [x] Untrack `.idea/`, dead 207MB `app/lib/<arch>/*.so`, 10MB banner PSD; tighten .gitignore
      (kept `app/lib/api-82*.jar` -- compileOnly Xposed API deps)
- [x] Confirm CI green on `patch` after push (Fork Build run 27870238991, 3m0s, all steps green)

### Phase 4: Custom Features (rolling) -- NEXT
- [x] **Force close in Recents menu** -- add a "Force close" entry to the task menu shown when tapping
      an app's title/icon on the Recents/Overview screen. Design is DONE (brainstormed + locked):
      see `docs/superpowers/specs/2026-06-20-recents-force-close-design.md`. Key facts: the menu lives
      in **Launcher3 quickstep** (`com.google.android.apps.nexuslauncher`), NOT SystemUI; hook via
      `@LauncherModPack`. Approach = direct view injection (no `SystemShortcut` subclass, no new dep);
      reuse `CustomNavGestures.killForeground()` for the force-stop; gate behind a default-OFF toggle
      `RecentsForceCloseEnabled` in `misc_prefs.xml`. New class:
      `app/src/main/java/sh/siava/pixelxpert/xposed/modpacks/launcher/RecentsForceClose.java`.
      IMPLEMENTED 2026-06-21 (Session 3): default-OFF toggle + title string added; new
      `RecentsForceClose` modpack hooks `TaskMenuView`/`TaskMenuViewWithArrow#populateAndLayoutMenu`,
      injects a cloned native-styled "Force close" row (icon `ic_close`), force-stops via
      `forceStopPackageAsUser`, dismisses the tile via `RecentsView#dismissTask`, closes the menu, and
      Toasts. Fully defensive (`ofIfPossible` + null/best-match lookups, try/catch around inject AND
      click) so a launcher update can at worst omit the row. Compile-correctness verified against
      codebase APIs; runtime acceptance = green Fork Build CI + on-device checklist (closed-source
      quickstep, no in-repo test harness).
<!-- Append one task per feature as they are requested. Each feature = an isolated,
     easily-rebasable change set on `patch`. -->

## Status Summary
| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: Fork Tracking & Branch Strategy | Done | 6/6 |
| Phase 2: Fork Build CI | Done | 8/8 |
| Phase 3: Repo & README Cleanup | Done | 7/7 |
| Phase 4: Custom Features (rolling) | Impl done, CI/on-device pending | 1/1 (Force close implemented) |

## Decisions & Notes
- 2026-06-20: Branch model = `canary` mirrors upstream, `patch` (new default) carries custom work.
- 2026-06-20: CI triggers on push to `patch`; outputs zip + APK as Actions artifacts (no Release pollution).
- 2026-06-20: Artifact naming = `PixelXpert-<branch>-<short7hash>.{zip,apk}`.
- 2026-06-20: Fork CI lives in `forkBuild.yml` and uses `actions/checkout` (it renames artifacts with `mv` in the CI step).
- 2026-06-20 (Phase 3, SUPERSEDES the old "minimum divergence" stance): all 6 upstream
  workflows + 3 helper scripts were DELETED from `patch`; `buildSrc` versioning logic and
  `PXTasks.gradle.kts` were rewritten (no longer identical to upstream); README and all
  module metadata (`module.prop`, `*.json`) repointed to the fork. Expect rebase conflicts on
  these when syncing `canary`; resolve in favor of the fork's versions.
- 2026-06-20: git history hygiene -- squash incidental fix/fixup commits into their logical
  working commit before the branch settles (force-push needs explicit confirmation).

## Blockers
<!-- none -->

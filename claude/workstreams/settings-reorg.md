---
name: settings-reorg
description: PixelXpert settings UI reorganization into 7 umbrella categories, settings search coverage, plus the A16/A17 redundancy and on-device-testing audit
status: done
---

# Settings UI reorganization + redundancy audit

## Current state
Done (2026-06-21). 9 flat top-level categories collapsed into 7 umbrellas; preference keys unchanged
(no behavior change). Commits `b72d4a54` + `9aa71bca`; `assembleDebug` green. Leftover deferred
moves and on-device test flags are tracked as checkboxes in `claude/todo.md`.

## Next actions
None -- remaining items live in `claude/todo.md` (deferred moves, on-device verify backlog).

## Decisions
- 2026-06-21: keep preference keys unchanged; only UI placement moves, so mods bind identically.
- 2026-06-21: deepens upstream divergence -- resolve rebase conflicts toward the fork.
- 2026-06-21: REJECTED removing the "dead" `action_headerFragment_to_{statusbar,dialer,packageManager,quickSettings}Fragment`
  nav actions. On tablet, `onSearchResultClicked` string-replaces the search action name to
  `action_headerFragment_to_*` and navigates with it, so they are live for tablet search.

## Findings

### The 7 umbrellas (key -> backing fragment)
- System UI (`systemui_header` -> new SystemUiFragment): links Quick settings + Status bar; inline Notifications (moved from Misc)
- Appearance (`theming_header` -> ThemingFragment, retitled)
- Lock & security (`lockscreen_header` -> LockScreenFragment, retitled): + AOD/doze block (moved from Misc)
- Navigation (`nav_header` -> NavFragment): + Recents/launcher block (moved from Misc)
- System & hardware (`misc_header` -> MiscFragment, retitled): keeps General + Volume + Monitoring/net-stats + System time
- Connectivity (`hotspot_header` -> HotSpotFragment, retitled)
- Apps & calls (`apps_calls_header` -> new AppsCallsFragment): links Package manager + Phone/dialer

App-switch toggles moved from Misc into Status bar (category retitled "App switch").
Verified with `assembleDebug` (JDK 17 launcher + Android SDK; submodules initialized).

### Bug-hunt fixes on the reorg (2026-06-21, `/bugs`; `assembleDebug` + `createZip --dry-run` green)
- Notification search coverage regression: the 4 notification prefs moved into the new
  `system_ui_prefs.xml`, which was NOT in `HeaderFragment.searchItems[]`, so they fell out of
  Settings search. Fix: registered `R.xml.system_ui_prefs` in `searchItems[]`, added
  `action_searchPreferenceFragment_to_systemUiFragment` to the phone nav graph (tablet derives the
  header variant), and marked the `quicksettings_header`/`statusbar_header` nav rows
  `search:ignore="true"` so they do not duplicate the already-indexed screens.
  [[tablet-search-derives-header-nav-actions]]
- createZip APK-bundling footgun: `createZip` consumed the APK via a hardcoded path +
  `mustRunAfter("renameReleaseApk")`, so `./gradlew createZip` alone could ship an APK-less zip.
  Fix: `from(tasks.named<Copy>("renameReleaseApk"))` wires a real dependency. Verified via
  `:app:createZip --dry-run`. Released `fork-v5.1.1-3`.

### Redundancy audit on A16/A17 (report only, nothing removed)
- Leveled flashlight: the redundant tile was removed (`9f29091e`); remaining flash code
  (`controlFlashWithVolKeys` while screen off, `AnimateFlashlight`) is still used -> keep.
  [[flashlight-leveled-tile-redundant]]
- AppCloneEnabler: native app cloning / private space exists since A15 -> verify on device before
  any removal.
- SyncNTPTime: Android has automatic network time, but PX adds manual control/servers -> keep.
- BrightnessRange / allScreenRotations: extend beyond stock behavior -> keep.

### Flagged for on-device testing (mirrored as checkboxes in todo.md)
- StatusbarGestures -- A17 QPR1 Scene rework (pre-17QPR1 vs 17QPR1+ branches).
- EasyUnlock -- multi-version credential fallbacks (A13..A16 QPR3).
- ScreenshotManager / ScreenRecord -- param-count / version-branch hooks may no-op on signature changes.
- MultiStatusbarRows -- pre-15beta3 IconManager fallback.
- ScreenGestures -- version detection via null checks / catch blocks.
- Cleanup nit: `ScreenOffKeys.java:147` stale "no need to try/catch once QPR1 stable" TODO --
  defensive, leave until device-confirmed.

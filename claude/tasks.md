# Tasks
PixelXpert fork -- ad-hoc findings outside the long-running plan in progress.md.

## Settings reorganization (2026-06-21) -- DONE
Collapsed 9 flat top-level categories into 7 functional umbrellas. Verified with
`assembleDebug` (JDK 17 launcher + Android SDK; submodules initialized) -- BUILD SUCCESSFUL.

7 umbrellas (key -> backing fragment):
- System UI (`systemui_header` -> new SystemUiFragment): links Quick settings + Status bar; inline Notifications (moved from Misc)
- Appearance (`theming_header` -> ThemingFragment, retitled)
- Lock & security (`lockscreen_header` -> LockScreenFragment, retitled): + AOD/doze block (moved from Misc)
- Navigation (`nav_header` -> NavFragment): + Recents/launcher block (moved from Misc)
- System & hardware (`misc_header` -> MiscFragment, retitled): keeps General + Volume + Monitoring/net-stats + System time
- Connectivity (`hotspot_header` -> HotSpotFragment, retitled)
- Apps & calls (`apps_calls_header` -> new AppsCallsFragment): links Package manager + Phone/dialer

Key principle: preference keys unchanged -> mods bind identically, no behavior change; only UI placement moved.
App-switch toggles moved from Misc into Status bar (category retitled "App switch").

### Deferred (would add nav-action re-sourcing risk; revisit with a build)
- [ ] Move Clock (`sbc_header`) + Battery bar (`BBarEnabled`) sub-screens from Status bar into Appearance.
- [ ] Move Network statistics (`netstat_header`) from System & hardware into Connectivity.
- [ ] VoLTE force (`force_volte`) left in dialer_prefs.xml (under Apps & calls) -- avoided editing that file (CallVibrator agent owns it).
- [x] Make relocated Notification block searchable again -- DONE 2026-06-21 (bug-hunt fix below).

## Bug-hunt fixes on the settings reorg (2026-06-21) -- DONE
`/bugs` on the recent reorg + CallVibrator + build commits. `assembleDebug` + `createZip --dry-run` green.
- [x] **[Warning] Notification search coverage regression** -- the 4 notification prefs moved into the new
      `system_ui_prefs.xml`, which was NOT in `HeaderFragment.searchItems[]`, so they fell out of Settings
      search (the old Misc category had `search:ignore` only on the category; children were still indexed).
      Fix: registered `R.xml.system_ui_prefs` in `searchItems[]`, added
      `action_searchPreferenceFragment_to_systemUiFragment` to the phone nav graph (tablet derives the
      header variant, already present), and marked the `quicksettings_header`/`statusbar_header` nav rows
      `search:ignore="true"` so they don't duplicate the already-indexed QS/status-bar screens.
- [x] **[Build] createZip APK-bundling footgun** -- `createZip` consumed the APK via a hardcoded path +
      `mustRunAfter("renameReleaseApk")`, which only ordered IF something else pulled renameReleaseApk into
      the graph; `./gradlew createZip` alone would silently ship an APK-less zip. Fix: `from(tasks.named<Copy>
      ("renameReleaseApk"))` wires a real task dependency. Verified via `:app:createZip --dry-run`.
- [n/a] **[Suggestion] "dead" `action_headerFragment_to_{statusbar,dialer,packageManager,quickSettings}Fragment`**
      -- bug-finder flagged these as removable; REJECTED after verifying. On tablet, `onSearchResultClicked`
      string-replaces the search action name to `action_headerFragment_to_*` and navigates with it, so those
      four actions are live for tablet search. Left in both graphs (keeps phone/tablet symmetric).

## Redundancy / bug audit (2026-06-21) -- report only (CallVibrator excluded; another agent owns it)

### Redundant on A16/A17 -- documented, NOT removed
- Leveled-flashlight: the redundant *tile* was already removed (commit 9f29091e). Remaining flash code
  (`controlFlashWithVolKeys` while screen off, `AnimateFlashlight`) is still used -> keep. See [[flashlight-leveled-tile-redundant]].
- AppCloneEnabler: native app cloning / private space exists since A15 -> verify on-device before any removal.
- SyncNTPTime (NTP): Android has automatic network time, but PX adds manual control/servers -> keep.
- BrightnessRange / allScreenRotations: extend beyond stock behavior -> keep.

### Flag for on-device testing (cannot verify without a device)
- [ ] StatusbarGestures -- A17 QPR1 Scene rework (pre-17QPR1 vs 17QPR1+ branches).
- [ ] EasyUnlock -- multi-version credential fallbacks (A13..A16 QPR3).
- [ ] ScreenshotManager / ScreenRecord -- param-count / version-branch hooks may no-op on signature changes.
- [ ] MultiStatusbarRows -- pre-15beta3 IconManager fallback.
- [ ] ScreenGestures -- version detection via null checks / catch blocks.

### Cleanup nit (not a bug)
- [ ] ScreenOffKeys.java:147 -- stale "no need to try/catch once QPR1 stable" TODO; defensive, leave until device-confirmed.

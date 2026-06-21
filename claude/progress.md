# Project: PixelXpert Fork Maintenance
> Last updated: 2026-06-21 | Session: 7

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
- [x] Publish a GitHub Release on `fork-v*` tag pushes (2026-06-21, Session 4): added a tags
      trigger + `contents: write` perm + a `gh release create` step (annotated-tag message as
      notes) so tagged builds attach the zip + APK as permanent, login-free downloads. Everyday
      `patch` pushes stay artifacts-only. SUPERSEDES the Phase-3 "no Release pollution" stance,
      but only for intentional fork-v* tags.

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

### Phase 4: Custom Features (rolling)
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
- [x] **Settings homepage entry placement** -- the injected "Pixel Xpert" top-level entry
      previously sat at the BOTTOM (own `PreferenceCategory`, `setOrder(9999)`) with a large gap.
      Moved it into the TOP services block alongside "Google services". File:
      `app/src/main/java/sh/siava/pixelxpert/xposed/modpacks/settings/PXSettingsLauncher.java`.
      Approach (new-settings/A16 path): in the `onCreateAdapter` before-hook, DFS the
      PreferenceScreen for the top services tile by key substring (`google`/`microg`/`gms` --
      microG-compatible), then add our entry to that tile's PARENT group with a matching
      `order` so a tie keeps it adjacent INSIDE the same rounded card. Fallback when the tile
      isn't found (not loaded / unknown key): pin our own block to the top via `order=-1000`
      (was 9999). Added dedupe guard (entry tagged key `pixelxpert_top_level`, skip if already
      present) to avoid duplicate inserts on adapter rebuilds. IMPLEMENTED 2026-06-21 (Session 4).
      NOT YET compiled/flashed -- `gradlew` not executable in this env. Key assumption to verify
      on-device: the services tile's preference key actually contains one of those needles; if
      not, it silently uses the top-pinned fallback (still "at top", just its own card).
- [ ] **Reboot actions inline in main power menu** -- surface the 3 advanced-reboot actions
      (Reboot to Bootloader, Soft Reboot, Restart SystemUI) DIRECTLY in the main long-press
      power-menu grid (the Emergency/Lockdown/Power off/Restart screen, Image #2), styled as
      native circular grid items so they look coherent, IN PLACE OF the nested "Advanced..."
      button. Today these 3 live ONE LEVEL DOWN: `advancedPowerMenu` injects a
      `PowerOptionsAction` ("Advanced...") whose submenu (`mPowerItems`) holds
      `BootloaderAction` / `SoftRebootAction` / `SystemUIRebootAction` (that submenu = Image #1).
      File: `app/src/main/java/sh/siava/pixelxpert/xposed/modpacks/systemui/PowerMenu.java`
      (`GlobalActionsDialogLite#createActionItems` after-hook, ~line 61-76). Open questions to
      settle at design time: (a) replace the "Advanced..." entry entirely, or keep it as a
      toggle-driven choice; (b) reuse the existing `advancedPowerMenu` pref or add a new
      default-OFF toggle in `misc_prefs.xml`; (c) icons -- each currently uses android
      `ic_restart`; main grid may want distinct icons. NOT STARTED -- needs brainstorming + design.
- [ ] **Updates tab still points to upstream (in-app self-update repoint)** -- the in-app
      Updates screen shows upstream's version (e.g. `canary-499`) and would download upstream's
      zip, because both manifest URLs in
      `app/src/main/java/sh/siava/pixelxpert/ui/fragments/UpdateFragment.java:69-70`
      (`stableUpdatesURL`/`canaryUpdatesURL`) still hardcode
      `raw.githubusercontent.com/siavash79/PixelXpert/{stable,canary}/latest{Stable,Canary}.json`.
      The updater parses `{versionCode, version, zipUrl, changelog}` from that JSON
      (`updateChecker.run`, ~L389-431) and downloads `zipUrl` via DownloadManager.
      NOT a one-line swap -- has a CI/release dependency: the fork must PUBLISH its own manifest
      JSON first or the new URL 404s. Design conflict to resolve: `canary` must stay a clean
      upstream mirror (branch model), so the fork manifest CANNOT live there -- natural home is
      the `patch` branch (e.g. `latestPatch.json`, `zipUrl` -> a `fork-v*` GitHub Release asset,
      `changelog` -> a fork URL), wired into the existing `fork-v*` Release pipeline
      (`forkBuild.yml`). Decisions needed: (a) keep the Stable/Canary channel split or collapse
      to one fork channel; (b) who writes/bumps the manifest (CI on tag push vs manual); (c)
      changelog source/format. SEPARATE, probably-intentional upstream pointers to leave alone
      unless decided otherwise: `PyTorchSegmentor.java:40,42` (model/lib), `Constants.java:12`
      (`PX_ICON_PACK_REPO`), `strings.xml` `github_repo_summary`. NOT STARTED -- needs design.
- [x] **Remove "Flashlight-leveled tile" feature (redundant)** -- stock Pixel ships this natively
      since Android 16 QPR3 (March 2026 feature drop): native flashlight brightness slider via
      long-press on the flashlight QS tile, identical to PixelXpert's feature. Verified via
      9to5Google/Android Police/Gadget Hacks (2026-06-21). DONE 2026-06-21 (commit `9f29091e`
      "feat: remove redundant leveled flashlight tile"): deleted `FlashlightTile.java` (-394
      lines), removed prefs `leveledFlashTile`/`isFlashLevelGlobal` from `quicksettings_prefs.xml`,
      dropped the 3 strings from `values/strings.xml` + all 28 translated copies, and trimmed
      `PreferenceHelper.java` + `SystemUtils.java` (-11 lines). The CAUTION was heeded: the SHARED
      `SystemUtils` flash plumbing (`getMaxFlashLevel`/`setFlashLevel`/`supportsFlashLevels`/
      `flashPCT`) was RETAINED for the sibling features that depend on it ("Control flashlight with
      volume buttons", "Flashlight fade effect") -- only the leveled-tile-specific code was removed.
      Verified zero remnants by grep. (Note: `CanaryChangelog.md` retains historical upstream lines
      mentioning the leveled tile -- left as-is; it is a dated changelog record, not live docs.)
- [x] **[BUG] Vibrate on call answered/disconnect not working** -- the `vibrateOnAnswered` /
      `vibrateOnDrop` feature (prefs `dialer_prefs.xml:11-25`) did nothing on-device. ROOT CAUSE:
      the `CallVibrator` modpack hooks `com.android.server.telecom.InCallController#onCallStateChanged`
      but was scoped via `@TelecomServerModPack` to package `com.android.server.telecom`, which is
      NOT in `scope.list` -- so LSPosed never injected it and the modpack never loaded (hidden by 3
      nested silent `catch(Throwable ignored)`). Commit `0c09fc33` had retargeted it from
      `@FrameworkModPack` to `@TelecomServerModPack` without adding scope. ON-DEVICE CHECK
      (2026-06-21, Pixel `5C181JEA315207`): `adb shell ps -A | grep -i telecom` returns NOTHING
      and `system_server` (pid 1663) is alive -- so Telecom is hosted INSIDE system_server, there
      is no `com.android.server.telecom` process to inject into. Also confirmed via the LSPosed
      "Connected packages" screen: telecom absent from scope. FIX APPLIED 2026-06-21: retargeted
      `CallVibrator` to `@FrameworkModPack` (package `android` = system_server, already in scope)
      and removed the now-meaningless `@MainProcessModPack`/`@ChildProcessModPack` telecom
      workaround annotations -- now matches the working `BrightnessRange` framework-modpack pattern;
      `ReflectedClass.of` resolves `InCallController` via the system_server classloader. COMMITTED
      as `7a6e4a18` ("fix: load CallVibrator in system_server for call vibration"); compiles in
      `assembleDebug` (Session 6). VERIFY on-device: make a call, confirm vibrate on answer and on disconnect. Secondary risk if still silent:
      `onCallStateChanged` arg layout (args[1]/args[2] = old/new state) may have shifted on A16 QPR.
      CLEANUP NOTE: `@TelecomServerModPack` annotation is now unused dead code (the
      `TELECOM_SERVER_PACKAGE` constant is still referenced by telecom special-casing in
      `XPLauncher.java:93,116,121-122`, so the constant stays) -- safe to delete the annotation file.
- [ ] **Bluetooth device battery in status bar** (NEW, from crDroid) -- show the connected BT
      device's battery level as a status-bar indicator. Verified-feasible scope: new
      `@SystemUIModPack` `BluetoothBatteryIcon.java` following the existing VoLTE-icon pattern in
      `modpacks/systemui/StatusbarMods.java` (capture `StatusBarIconControllerImpl` in
      afterConstruction ~L494; init icon in `PhoneStatusBarViewController.onViewAttached` ~L642;
      show/hide via `StatusBarIconController.setIcon`/`removeAllIconsForSlot` with a new slot
      e.g. `bt_battery`). State source: `BroadcastReceiver` on BT connection-state changes +
      `BluetoothDevice.getBatteryLevel()` (API 31+, returns -1 if unavailable -> hide icon). Adds:
      new toggle `BluetoothBatteryIconEnabled` in `statusbar_settings.xml`, title string, a
      `ic_bluetooth_battery` drawable, and a `BluetoothManager()` helper in `SystemUtils.java`.
      No existing BT-battery code (VolumeTile detects BT audio devices but not battery). NOT STARTED.
- [ ] **QS brightness slider visibility + position** (NEW, from crDroid) -- (a) always-show /
      show-only-when-expanded / hidden, and (b) above vs below the QS tiles. Scope: new
      `@SystemUIModPack` `QSBrightnessSlider.java` (pattern per `QSTileGrid.java`). CAVEAT/RISK:
      A16 QS is the new COMPOSE stack (`qs.panels.ui.compose.*`) -- the brightness slider may NOT
      be a simple `View` with a stable parent, so classic `setVisibility` / `removeView+addView`
      reordering used elsewhere (`MultiStatusbarRows.java:66-71`) may not apply cleanly; hook
      candidates `brightness.BrightnessSliderController` (onViewAttached -> slider view) and the
      QS panel/compose layout for ordering, plus expansion state via shade controller
      (`StatusbarGestures.java`). Existing `BrightnessRange.java` hooks brightness CLAMPING, not
      the slider UI -- no overlap. Adds: 2 `MaterialListPreference`s in `quicksettings_prefs.xml`,
      arrays + strings. NEEDS a feasibility spike on the Compose QS hierarchy before committing
      to an approach. NOT STARTED.
- [ ] **Hide the launcher bottom search bar (QSB)** (NEW, 2026-06-21, user request) -- remove the
      "Search" bar pinned at the bottom of the Pixel launcher home screen. It is the Quick Search
      Box (QSB) widget baked into the hotseat of `com.google.android.apps.nexuslauncher` (quickstep,
      closed-source) -- it persists even after the user disables/removes the Google app, so there is
      no stock toggle. Same hook surface as the shipped Force-close feature (`@LauncherModPack`,
      Launcher3 quickstep -- see [[recents-task-menu-lives-in-quickstep]]). Likely approach: hook the
      hotseat/QSB view setup and hide it (`setVisibility(GONE)` / suppress add), candidate classes
      `com.android.launcher3.qsb.*` / `Hotseat` / `QsbContainerView`; must be fully defensive
      (`ofIfPossible` + try/catch) since the target is closed-source and version-fragile. Gate behind
      a new default-OFF toggle (e.g. `HideLauncherSearchBar`) in `nav_prefs.xml` (Recents/launcher
      block lives under Navigation now). NOT STARTED -- needs a feasibility check on the A16 launcher
      QSB hierarchy + brainstorming before implementation.
- [ ] **[BUG] Double-tap to wake phone not working** (NEW, 2026-06-21, user-reported on-device) --
      the "Double-tap to wake phone" toggle (pref `doubleTapToWake`, "Disables single-tap to wake";
      lives in the AOD/doze block -- moved to `lock_screen_prefs.xml` under Lock & security in the
      Session-6 reorg) is ON but does nothing on the current build. Needs on-device root-cause: find
      the modpack that consumes `doubleTapToWake` (likely a doze/AOD or `PhoneWindowManager`/tap-gesture
      hook), check whether the hooked method/signature still matches on A16 QPR (param-count or
      version-branch drift is the usual culprit -- cf. the StatusbarGestures/EasyUnlock entries in
      tasks.md), and confirm the modpack's target package is in scope.list. NOT STARTED -- needs a device.
<!-- Append one task per feature as they are requested. Each feature = an isolated,
     easily-rebasable change set on `patch`. -->

### Phase 5: Settings UI reorganization (2026-06-21, Session 6)
Collapsed the 9 flat top-level settings categories into 7 functional umbrellas. Preference keys
unchanged -> every mod binds identically; only UI placement moved. Verified locally with
`assembleDebug` (BUILD SUCCESSFUL; JDK 17 launcher + Android SDK, `RangeSliderPreference` submodule
initialized). Commits `b72d4a54` (reorg) + `9aa71bca` (docs/audit).
- [x] 7 umbrellas: System UI (new `SystemUiFragment` -> Quick settings + Status bar + Notifications),
      Appearance (`ThemingFragment`), Lock & security (`LockScreenFragment` + AOD/doze block),
      Navigation (`NavFragment` + Recents block), System & hardware (`MiscFragment`, slimmed),
      Connectivity (`HotSpotFragment`), Apps & calls (new `AppsCallsFragment` -> Package mgr + Dialer).
- [x] Dissolved the Misc grab-bag (Doze->Lock, Recents->Nav, app-switch->Status bar,
      Notifications->System UI) and folded the single-screen Hotspot + Package-manager top-level slots.
- [x] Routing: 2 new fragments + drill actions in `nav_graph_phone` + `nav_graph_tablet_details`;
      `SettingsActivity` switch updated. Reused 5 existing fragments as umbrella landings to limit churn.
- [x] Redundancy/bug audit recorded (CallVibrator excluded -- handled separately). See `tasks.md`.
- [ ] Deferred (low value, needs a build): Clock/Battery-bar -> Appearance; Net-stats -> Connectivity;
      VoLTE left in `dialer_prefs.xml` (Apps & calls, to avoid the CallVibrator agent). Tracked in `tasks.md`.

## Status Summary
| Phase | Status | Progress |
|-------|--------|----------|
| Phase 1: Fork Tracking & Branch Strategy | Done | 6/6 |
| Phase 2: Fork Build CI | Done | 8/8 |
| Phase 3: Repo & README Cleanup | Done | 7/7 |
| Phase 4: Custom Features (rolling) | 4 done (1 pending verify), 5 not started | 4/9 (done: Force close, Settings entry placement, Flashlight-tile removal, [BUG] CallVibrator framework-retarget [CI/on-device verify pending]; pending: Reboot-inline, Updates-repoint, BT battery in statusbar, QS brightness slider, Hide launcher QSB search bar) |
| Phase 5: Settings UI reorganization (Session 6) | Done | 7 umbrellas, Misc dissolved, assembleDebug green |
| Post-Phase hardening (Session 5) | Done | build/CI/mod robustness commits, see Decisions |

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
- 2026-06-21 (Session 3): recomposed the 8 fork commits above BASE `e90b9986` into 4 logical
  commits (`fork:` tracking+CI, `build:` versioning+hygiene, `chore:` cleanup+fork metadata,
  `feat:` Force close) via the git-rewrite skill, then force-pushed `patch`. This does NOT
  contradict the Phase-3 "rewrite skipped to protect the canary mirror" note: only fork commits
  ABOVE the upstream merge-base were rewritten -- `canary` and all upstream history at/below
  `e90b9986` are untouched. The recomposed tree was verified byte-identical to the pre-rewrite tree.
  (`patch` was subsequently rebased onto upstream's newer base commit, so the 4 commits' hashes
  have changed from their post-recompose values -- the logical structure is unchanged.)

- 2026-06-21 (Session 5): post-phase hardening landed on `patch` (single author, all on top of
  the earlier work; no concurrent-session content lost). Commits:
  - `9f29091e` feat: remove redundant leveled flashlight tile (see Phase 4 item, now done).
  - `c586f489` fix(build): resolve stable version from nearest `v*` tag, not the newest repo tag
    (`GitTagProvider.kt`) -- stable channel now versions off the closest tag.
  - `8c099810` fix(build): order version bump BEFORE assemble and stamp APK lazily
    (`PXTasks.gradle.kts`, `app/build.gradle.kts`, `BuildUtils.kt`, `IncrementVersionTask.kt`) --
    fixes version/APK-stamp ordering so the bumped version is the one packaged.
  - `f3f8c533` ci: harden forkBuild release publishing and signing (`forkBuild.yml`).
  - `ddcf814a` fix: harden Recents force-close and Settings-launcher mods
    (`RecentsForceClose.java` +355/-, `PXSettingsLauncher.java` +289/-) -- substantially hardened
    the two shipped Phase-4 features (defensive lookups / edge cases) beyond their initial impl.
  These refine Phases 2-4; no plan items reopened. Docs synced to this state 2026-06-21.

- 2026-06-21 (Session 6): **Settings UI reorganization** (Phase 5) landed -- 9 flat top-level
  categories collapsed into 7 umbrellas (commits `b72d4a54` reorg + `9aa71bca` docs). Reused 5
  existing fragments as umbrella landings + 2 new (`SystemUiFragment`, `AppsCallsFragment`) to keep
  nav-graph churn down; preference keys preserved so no behavior change. Verified with `assembleDebug`
  (BUILD SUCCESSFUL). This deepens upstream divergence (header/fragments/nav graphs/XML) -- resolve
  `patch`-onto-`canary` rebase conflicts in favor of the fork. Deferred items + redundancy/bug audit
  in `tasks.md`. Also reconciled: CallVibrator bug fix is committed as `7a6e4a18` (pending on-device verify).

- 2026-06-21 (Session 6): **CI fix** -- Fork Build had been RED since the versioning-pipeline
  hardening: commit `8c099810` dropped the APK rename, and AGP 9 removed the legacy
  `applicationVariants.outputFileName` API, so the release APK kept its default name
  (`app-release.apk`). Two failures resulted: the Magisk `createZip` `from(file(PixelXpert.apk))`
  matched nothing -> zip silently shipped WITHOUT the APK; and CI staging `cp PixelXpert.apk`
  failed loudly. FIX: new `renameReleaseApk` Copy task in `app/PXTasks.gradle.kts` copies the
  assembled APK to `build/distApk/PixelXpert.apk` (a non-AGP dir, to dodge Gradle 9
  overlapping-output validation), ordered assembleRelease -> renameReleaseApk -> createZip;
  `createZip` and `forkBuild.yml` repointed to that path. Verified locally
  (`assembleRelease renameReleaseApk createZip`): APK present and bundled in the zip.

- 2026-06-21 (Session 7): **Bug-hunt fixes on the settings reorg** (`/bugs`). Two real issues fixed +
  one false finding rejected (see `tasks.md`):
  - Notification prefs had silently dropped out of Settings search after moving into the new
    `system_ui_prefs.xml` (not in `HeaderFragment.searchItems[]`). Registered it + added the phone
    nav action `action_searchPreferenceFragment_to_systemUiFragment` + marked the duplicate nav rows
    `search:ignore`. See [[tablet-search-derives-header-nav-actions]].
  - `createZip` consumed the APK by hardcoded path + `mustRunAfter`, so `./gradlew createZip` alone
    could ship an APK-less zip; rewired to `from(tasks.named<Copy>("renameReleaseApk"))`.
  - Verified: `:app:assembleDebug` + `:app:createZip --dry-run` both green.
  - History: recomposed the 12 commits above `fork-v5.1.1-2` (`09d6ff70`) into logical commits and
    force-pushed `patch`; nothing at/below the tag was touched. New release `fork-v5.1.1-3` published.

## Blockers
<!-- none -->

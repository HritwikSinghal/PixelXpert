# PixelXpert fork -- Todo

## Notes & Conventions

**PUBLIC REPO -- no identifying info anywhere in `claude/`.** Never write real names, account or
GitHub handles, emails, local usernames or home-directory paths, device serials, IMEI/ICCID/phone
numbers, carrier or SIM details, IP addresses, or location. Refer to the repo as "the fork" /
`origin`; the `gh -R` value lives in `CLAUDE.md`. Use relative or placeholder paths (`boot.log`,
`/tmp/...`). Public Android build IDs (e.g. `CP3A.261005.005`) and package names are fine. Scrub
pasted logcat before saving it here. (Older pushed history already leaks some of this -- see
`claude/progress.md` Decisions.)

- **Branch model**: upstream is the community fork `Codecity001/PixelXpert` (switched 2026-10-08;
  the original repo is archived). `canary` = clean upstream mirror, never commit custom work; `patch` = all fork
  work. Rebasing `patch` onto `canary` conflicts on intentionally diverged files (deleted upstream
  CI, rewritten versioning/`PXTasks.gradle.kts`/`buildSrc`, fork metadata) -- resolve toward the fork.
  Exceptions kept from upstream: user-app module packaging and upstream's CI workflow files.
- **Build**: use the Nix flake (`nix run` -> APK; `nix run .#zip` -> flashable zip). The system
  `java-21-openjdk` is a JRE without `javac`, so bare `./gradlew` fails. [[nix-flake-build]]
- **CI**: `forkBuild.yml` on push to `patch` (artifacts) and on `fork-v*` tags (GitHub Release with
  auto changelog + tag message). `gh` defaults to upstream -- always pass `-R` (value in `CLAUDE.md`).
- **Modpack scope**: a modpack only loads if its target package is in
  `app/src/main/resources/META-INF/xposed/scope.list` (mirror `res/values/xp_module_scope.xml`).
  [[modpack-scope-list-requirement]]
- **Reflection**: `findMethodBestMatch` THROWS on miss (never null) -- null-guards on it are dead
  code. [[xposed-findmethodbestmatch-throws]] Be defensive against obfuscated SystemUI/quickstep
  internals: `ReflectedClass.ofIfPossible`, best-match lookups, try/catch around hook AND body.
- **Testing**: no in-repo harness; acceptance = green Fork Build CI + an on-device check. Logging:
  `adb logcat -s "PixelXpert Lsposed Module"`; `verboseLogging` toggle adds per-hook traces.
- **Git hygiene**: record `BASE=$(git rev-parse HEAD)` at task start; never rewrite history at/below
  it; confirm before any force-push.
- **Test device** runs A17 QPR3 with a FORK build installed via Obtainium. The fork reuses upstream's
  version name (`canary-499`), so `versionName` alone does not tell fork from upstream -- check for
  fork-only pref keys (`RecentsForceCloseEnabled`, `verboseLogging`) instead.

## Phase 7: A17 QPR3 compatibility
- [ ] qpr3-statusbar -- diagnosed, fix not started -- `claude/workstreams/qpr3-statusbar.md`
- [x] Fix SystemUI crash on pref change (`SBNIC` NPE) -- arrived with upstream `5749e2f1` in the rebase
- [ ] community-fork-sync -- adopted as upstream; rebase done locally, push pending -- `claude/workstreams/community-fork-sync.md`
- [ ] Log + guard the VoLTE/VoWiFi path (`updateVoData`, `mPhoneStatusbarView` null)
- [ ] Install a new fork build on the device (fork already installed via Obtainium; same key)
- [ ] Verify VoLTE/VoWiFi icons under the Compose status bar root; move init off `onViewAttached` if needed
- [ ] Restore the status-bar notification icon limit on QPR3

## Phase 4: Custom features (rolling)
- [ ] recents-force-close -- blocked: broken on device (user report 2026-10-08), AMS grant hook not firing -- `claude/workstreams/recents-force-close.md`
- [ ] Reboot actions inline in power menu -- `modpacks/systemui/PowerMenu.java` (`GlobalActionsDialogLite#createActionItems` after-hook, ~L61-76); decide replace vs toggle, reuse `advancedPowerMenu` or new toggle, per-action icons. Needs brainstorm.
- [ ] **URGENT before next release** Updates tab repoint (in-app self-update) -- since the rebase the updater pulls Codecity001's manifests (`ui/fragments/UpdateFragment.java:69-70`; `UpdateWorker.java:116-119` notifies whenever their code is higher -- they are at 524), so every fork build offers THEIR zip, which would replace our module (their updateJson) and fail the APK install (different signing key). Parsed ~L389-431. Fork must publish its own manifest on `patch` (e.g. `latestPatch.json`, `zipUrl` -> `fork-v*` asset) wired into `forkBuild.yml`. Leave `PyTorchSegmentor.java:40,42`, `Constants.java:12`, `strings.xml github_repo_summary`. [[in-app-updater-manifest-source]] Tackle last.
- [ ] Bluetooth device battery in status bar (from crDroid) -- new `@SystemUIModPack` `BluetoothBatteryIcon.java` on the VoLTE-icon pattern in `StatusbarMods.java`; `BluetoothDevice.getBatteryLevel()` (API 31+, -1 -> hide). Blocked in practice on qpr3-statusbar (same icon pipeline).
- [ ] QS brightness slider -- now provided by upstream (`QSBrightnessSlider.java`, `2b1f49cc`); just verify on device.
- [ ] Hide launcher bottom search bar (QSB) -- `@LauncherModPack`; likely `com.android.launcher3.qsb.*` / `Hotseat` / `QsbContainerView`; default-OFF `HideLauncherSearchBar` in `nav_prefs.xml`. Feasibility check first.
- [ ] [BUG] Double-tap to wake does nothing -- find the consumer of `doubleTapToWake` (AOD/doze or `PhoneWindowManager`), check A17 signature drift and scope.list. Needs a device.

- [ ] [BUG] APK lags its module metadata by one build: on device `module.prop` says 500 while the installed (and bundled) APK is 499. Suspects: nothing orders `incrementCanaryVersion` before `assembleRelease` (`app/PXTasks.gradle.kts:10` comment claims it, only `renameReleaseApk`/`createZip` have `mustRunAfter`), and/or configuration cache freezing the version provider (`buildSrc/.../BuildUtils.kt:86`). Pre-existing, not from the rebase.
- [ ] [BUG] Module updateJson zipUrl (`releases/download/canary_builds/PixelXpert.zip`) likely dead -- forkBuild publishes only `fork-v*` releases named `PixelXpert-<tag>-<sha7>.zip`, and CI never commits the bumped JSON. Fold into the updates repoint.
- [ ] Install-path trap: `customize.sh`/`service.sh` use `pm install -r -d`; on this user build a LOWER versionCode fails silently (logged only to `$MODDIR/install.log`). Keep versionCode monotonic above the installed build.

## Phase 8: On-device verification backlog
- [ ] [BUG] CallVibrator broken on device (user report 2026-10-08; `vibrateOnAnswered`/`vibrateOnDrop` both on). Fix `7a6e4a18` retargeted it to `@FrameworkModPack` -- suspect the system_server scope (`android` vs `system`, see community-fork-sync); else check `onCallStateChanged` args on A17. Cleanup: `@TelecomServerModPack` annotation is dead (keep `TELECOM_SERVER_PACKAGE`, used by `XPLauncher`).
- [ ] Diagnostic logging -- verbose OFF quiet (errors/warns only); ON -> per-hook traces; deliberate hook miss -> WARN with verbose OFF. [[diagnostic-logging-system]]
- [ ] Settings homepage entry -- services tile key contains google/microg/gms.
- [ ] StatusbarGestures (A17 QPR1 Scene rework branches)
- [ ] EasyUnlock (multi-version credential fallbacks)
- [ ] ScreenshotManager / ScreenRecord (param-count / version-branch hooks)
- [ ] MultiStatusbarRows (pre-15beta3 IconManager fallback)
- [ ] ScreenGestures (version detection via null checks / catch blocks)
- [ ] AppCloneEnabler -- redundant with native cloning since A15? verify before removal

## Deferred (settings moves -- add nav-action re-sourcing risk)
- [ ] Move Clock (`sbc_header`) + Battery bar (`BBarEnabled`) sub-screens from Status bar into Appearance
- [ ] Move Network statistics (`netstat_header`) from System & hardware into Connectivity
- [ ] Move VoLTE force (`force_volte`) out of `dialer_prefs.xml`
- [ ] `ScreenOffKeys.java:147` stale QPR1 TODO -- leave until device-confirmed

## Done
- [x] Phases 1-3: fork tracking + branch strategy, Fork Build CI, repo/README cleanup (CI-green)
- [x] Phase 5: Settings UI reorg into 7 umbrellas -- `claude/workstreams/settings-reorg.md`
- [x] Phase 6 (code): app-wide leveled diagnostic logging (on-device verify in Phase 8)
- [x] Settings homepage entry placement (`PXSettingsLauncher.java`) [[settings-homepage-entry-placement]]
- [x] Remove redundant leveled-flashlight tile (`9f29091e`) [[flashlight-leveled-tile-redundant]]

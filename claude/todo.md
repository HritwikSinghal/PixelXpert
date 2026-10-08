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
  work as ~14 logical commits. Rebase procedure + conflict policy: `docs/rebasing-on-upstream.md`.
  New fixes go in as `git commit --fixup=<sha>` so the next rebase folds them.
- **Build**: use the Nix flake (`nix run` -> APK; `nix run .#zip` -> flashable zip). The system
  `java-21-openjdk` is a JRE without `javac`, so bare `./gradlew` fails. [[nix-flake-build]]
- **CI**: `forkBuild.yml` on push to `patch` (artifacts only); a manual run on `patch` cuts a
  `canary-<N>` Release and pushes a version-bump commit to `patch` (pull afterwards). `gh` defaults to upstream -- always pass `-R` (value in `CLAUDE.md`).
- **Modpack scope**: a modpack only loads if its target package is in
  `app/src/main/resources/META-INF/xposed/scope.list` (mirror `res/values/xp_module_scope.xml`).
  [[modpack-scope-list-requirement]]
- **Reflection**: `findMethodBestMatch` THROWS on miss (never null) -- null-guards on it are dead
  code. [[xposed-findmethodbestmatch-throws]] Be defensive against obfuscated SystemUI/quickstep
  internals: `ReflectedClass.ofIfPossible`, best-match lookups, try/catch around hook AND body.
- **Device logs**: logcat main is 256 KiB by default -- boot hook logs rotate out within ~2 min; run `adb logcat -G 16M` (resets on reboot) or read Vector's own `/data/adb/lspd/log/modules_*.log` (module load per process, whole boot). `service.sh` deliberately `killall`s systemui/launcher/dialer ~5 s after boot -- not a crash.
- **Testing**: no in-repo harness; acceptance = green Fork Build CI + an on-device check. Logging:
  `adb logcat -s "PixelXpert Lsposed Module"`; `verboseLogging` toggle adds per-hook traces.
- **Git hygiene**: record `BASE=$(git rev-parse HEAD)` at task start; never rewrite history at/below
  it; confirm before any force-push.
- **Test device** runs A17 QPR3 with fork `canary-525` (user app, KSU module). PixelXpert must have
  "System Framework" ticked in Vector, or every `@FrameworkModPack` silently never loads. Phone is
  shared with another session -- ask before flash/reboot.

## Phase 7: A17 QPR3 compatibility
- [ ] qpr3-statusbar -- VoLTE/VoWiFi WORKS on device via `CommandQueue` (local fixup, unpushed); left: app-switch icon on the same route, SB icon limit -- `claude/workstreams/qpr3-statusbar.md`
- [x] Fix SystemUI crash on pref change (`SBNIC` NPE) -- arrived with upstream `5749e2f1` in the rebase
- [ ] community-fork-sync -- adopted as upstream, pushed 2026-10-08; on-device smoke test pending -- `claude/workstreams/community-fork-sync.md`
- [x] Log + guard the VoLTE/VoWiFi path (`updateVoData`, `mPhoneStatusbarView` null)
- [x] Install a new fork build on the device (`canary-525`, 2026-10-08)
- [x] VoLTE/VoWiFi icons under the Compose status bar -- verified on device 2026-10-08 (local build, fixup `dcc9dccd`)
- [ ] App-switch icon (`APP_SWITCH_SLOT`) still uses `StatusBarIconController` -- route via `setSBIconSlot` so it draws on the Compose bar
- [ ] Restore the status-bar notification icon limit on QPR3

## Phase 9: Release pipeline
- [x] Delete old `fork-v*` tags + releases; disable upstream's 8 workflows in Actions
- [x] Write the `canary-<N>` manual release workflow (`.github/workflows/forkBuild.yml`, actionlint clean)
- [x] release-pipeline -- pushed `patch`, cut `canary-525` (2026-10-08) -- `claude/workstreams/release-pipeline.md`
- [ ] Verify the release end-to-end -- server side DONE for 525 and 526; remaining: KSU manager + in-app updater offer 526 over 525 on device
- [ ] Clean up local leftovers after the release is verified (needs user OK -- deletes branches/refs): branches `patch-scrubbed`, `patch-private`, `backup/patch-pre-codecity-rebase`, `backup/patch-scrubbed-pre-versioning`, `backup/pre-rewrite-s7`; ref `refs/compare/codecity-canary`; the detached scratch worktree under the session scratchpad (`git worktree prune` after its dir is gone). Never `git push --tags` -- 81 local tags are upstream's.

## Phase 4: Custom features (rolling)
- [ ] recents-force-close -- force-stop works with `system` scope; tile-dismiss + row-alignment fixes in `canary-526`, verify on device -- `claude/workstreams/recents-force-close.md`
- [ ] Reboot actions inline in power menu -- `modpacks/systemui/PowerMenu.java` (`GlobalActionsDialogLite#createActionItems` after-hook, ~L61-76); decide replace vs toggle, reuse `advancedPowerMenu` or new toggle, per-action icons. Needs brainstorm.
- [x] Updates tab repoint -- in-app updater reads this fork's `patch/latestCanary.json` for both channels (`1d230786`)
- [ ] Bluetooth device battery in status bar (from crDroid) -- new `@SystemUIModPack` `BluetoothBatteryIcon.java` on the VoLTE-icon pattern in `StatusbarMods.java`; `BluetoothDevice.getBatteryLevel()` (API 31+, -1 -> hide). Unblocked: use the `CommandQueue` route in `setSBIconSlot`.
- [ ] QS brightness slider -- now provided by upstream (`QSBrightnessSlider.java`, `2b1f49cc`); just verify on device.
- [ ] Hide launcher bottom search bar (QSB) -- `@LauncherModPack`; likely `com.android.launcher3.qsb.*` / `Hotseat` / `QsbContainerView`; default-OFF `HideLauncherSearchBar` in `nav_prefs.xml`. Feasibility check first.
- [ ] [BUG] Double-tap to wake does nothing -- find the consumer of `doubleTapToWake` (AOD/doze or `PhoneWindowManager`), check A17 signature drift and scope.list. Needs a device.

- [x] [BUG] APK lagged its module metadata by one build -- did NOT recur on `canary-525` (module.prop and bundled APK both 525, checked with `aapt2 dump badging`); reopen if a later build drifts
- [x] [BUG] Dead module updateJson zipUrl -- release workflow now publishes `canary-<N>/PixelXpertFork-canary-<N>.zip` and commits the bumped JSONs back
- [ ] Install-path trap: `customize.sh`/`service.sh` use `pm install -r -d`; on this user build a LOWER versionCode fails silently (logged only to `$MODDIR/install.log`). Keep versionCode monotonic above the installed build.

## Phase 8: On-device verification backlog
- [ ] [BUG] (null guard committed; no NPE in SystemUI log after restart 2026-10-08; whether ignored-icons actually hides icons under Compose is untested) `StatusIconTuner.setIgnoredIcons` NPE (getObjectField on null) on every pref load, canary-525 -- "failed to apply ignored icons"
- [ ] [BUG] `GestureNavbarManager` BackPanelController#onMotionEvent hook NPE (getObjectField on null), canary-525, fires on back gestures
- [x] [BUG] CallVibrator -- FIXED on device 2026-10-08 by ticking System Framework for PixelXpert in Vector (scope was missing `system`). Leftover (user report 2026-10-08; `vibrateOnAnswered`/`vibrateOnDrop` both on). Fix `7a6e4a18` retargeted it to `@FrameworkModPack` -- suspect the system_server scope (`android` vs `system`, see community-fork-sync); else check `onCallStateChanged` args on A17. Cleanup: `@TelecomServerModPack` annotation is dead (keep `TELECOM_SERVER_PACKAGE`, used by `XPLauncher`).
- [x] Diagnostic logging (verbose traces + hook-callback errors seen on device 2026-10-08) -- verbose OFF quiet (errors/warns only); ON -> per-hook traces; deliberate hook miss -> WARN with verbose OFF. [[diagnostic-logging-system]]
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

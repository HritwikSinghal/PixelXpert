---
name: community-fork-sync
description: Cherry-pick candidates from the active community fork Codecity001/PixelXpert ("PixelXpertFork", canary-52x) -- A17 QPR3 fixes, user-app install, KernelSU, new tiles
status: active
---

# Community fork sync (Codecity001/PixelXpert)

## Current state
ADOPTED AS UPSTREAM 2026-10-08: our commits were rebased onto its `canary` (see Decisions in
`claude/progress.md`), so everything below is now in our tree. Rebase done locally; push pending.
The commit lists below remain useful as a map of what upstream changed.

Original survey (2026-10-08): `Codecity001/PixelXpert` is a GitHub fork of the archived
upstream, branded "PixelXpertFork", actively released (`canary-524` on 2026-10-07, stable `v6.0.3`
on 2026-10-05). Its `canary` is 141 commits ahead / 0 behind upstream `canary`, touching 73 files.
It is the most complete source of A17 QPR fixes we have found.

## Next actions
1. Push rebased `patch`, fast-forward `canary` to upstream, disable upstream's workflows in our Actions.
2. On device: smoke-test the upstream features we now ship (QS brightness slider, Compose clock,
   user-app install over the existing build) alongside ours.
3. After that, set this workstream to done; later syncs follow CLAUDE.md "Fork Maintenance".

## Decisions
<!-- APPEND. YYYY-MM-DD: what was decided, why, what was rejected. -->
- 2026-10-08: adopt as upstream via rebase (not cherry-picks); user-app packaging taken from
  upstream; upstream CI kept alongside ours. Rebase notes under Findings.

## Findings

### Rebase log (2026-10-08)
- Backup of the pre-rebase tip: branch `backup/patch-pre-codecity-rebase`.
- 24 commits replayed (22 original + tracker restructure + force-close WIP).
- Conflicts and resolutions, in order:
  1. fork CI commit: kept upstream's CI files (both deleted-by-us and conflicting ones).
  2. versioning commit: `buildSrc/BuildUtils.kt`, `GitTagProvider.kt` -> ours (their changes serve
     their release flow; only caller outside buildSrc was their zip name in PXTasks).
  3. metadata commit: `module.prop`, `MagiskModuleUpdate_{Full,Xposed}.json`, `README.md`,
     `latestCanary.json` -> ours (module identifies as this fork, versionCode stays on our 499 line).
  4. APK packaging commit: `app/PXTasks.gradle.kts` merged -- our `from(tasks.named<Copy>("renameReleaseApk"))`
     dependency + their zip-root layout (`into("")`); their installer expects `$MODPATH/PixelXpert.apk`,
     which is what `renameReleaseApk` produces.
  5. logging commit: `XPrefs.java` (blank line), `ScreenOffKeys.java` (their
     `ensureDoubleTapPowerEnabledIfNeeded()` + our `logWarn`), `CustomNavGestures.java` (their
     `saveFocusedTask()` retry + our `logWarn`). `logWarn` needs no import: `XposedModPack extends Logger`.
- Scope (corrected after an adversarial check): `isSystemServer` comes from
  `ModuleLoadedParam.isSystemServer()` in `onModuleLoaded` (`XPLauncher.java:73`), not from
  `onSystemServerStarting` (which only installs the StatusbarSize early hook). Framework packs load
  at `XPLauncher.java:231` only if the framework calls `onPackageReady` inside system_server, the
  `PhoneWindowManager.init` before-hook fires after that, and `isBootLooped` does not bail. With
  `scope.list` = `system` (and `module.prop` `minApiVersion=101`, `staticScope=true`), Vector v2.2
  treats `system` as system_server and `android` as the ordinary package, so injection into
  system_server is likely -- but that `onPackageReady` still fires there is UNVERIFIED. Test on
  device: verbose logging on, look for "Loaded modpack PackageManager"/"CallVibrator" lines.

### Adversarial review of the rebase (2026-10-08, 2 bug hunters + 4 refuters)
- CONFIRMED: no change of ours lost at HEAD (3-way `git merge-file` of all 29 shared files vs HEAD;
  range-diff shows only the intended deviations). Per-commit content of a few `!` commits not checked.
- FIXED (critical, silent): upstream rewrote `version.properties` to `CANARY_*`/`STABLE_*` keys and we
  never touched the file, so git merged it with no conflict; our `buildSrc` reads only `VERSION_CODE`
  and falls back to 0 -> next build would be versionCode 1, a downgrade vs the installed 499.
  Restored our `VERSION_CODE=499`/`VERSION_NAME=canary-499` as a fixup into the versioning commit.
  Lesson: after a rebase onto a base that rewrote shared config, diff every file our code READS, not
  only the conflicted ones.
- FIXED: their zip-name line called the removed `getVersionNameProvider()` -> `PixelXpert.zip`.
- OPEN: upstream workflows do not fit our build -- `makeCanaryTestPackage.yml:50` and
  `makeStableRelease.yml:85,116,135` expect `output/PixelXpertFork-*.zip`; `makeStableRelease.yml:68`
  passes `-PversionName` (unused by our versioning); `makeCanaryRelease.yml:67` and
  `.github/prepareCanaryChangelog.sh:3` read `CANARY_VERSION_NAME`. `makeStableRelease` fires on `v*`
  tags (ours are `fork-v*`, which do not match). Disable them in our Actions before pushing `canary`.
- PRE-EXISTING (not from the rebase): `fullPackageTestBuild.yml:49`, `xposedPackageTestBuild.yml:52`,
  `.github/make_debug_zip.sh` expect `app/build/outputs/apk/debug/PixelXpert.apk`, which our
  `app/build.gradle.kts` no longer produces.
- LOW: `CustomNavGestures.java` -- upstream calls `saveFocusedTask()` on every ACTION_DOWN; if recents
  reflection keeps failing, our `logWarn` (with stack trace) in `killForeground` could spam. Consider
  `logDebug`.
- Privacy refuter: content clean of serial/home paths in our range EXCEPT a `Signed-off-by:` trailer
  with the owner's name+email in commit `.github: add changelog to releases`, and a
  local plans-file path added then removed in the tracker history. All commits also
  carry the owner email as AUTHOR metadata (public on GitHub regardless of content).

- Packaging hunter: zip layout, module id, output paths verified clean. Updater now targets
  Codecity001 (confirmed: `UpdateFragment.java:69-70`); on-device module.prop 500 vs APK 499
  (confirmed via adb). `latestVersion.json` still carries Codecity canary-508 URLs. A stale
  `output/PixelXpert.zip` (Jun 21, old priv-app layout) sits in the tree -- do not flash it.
  Not reached: signing-cert comparison of the device APK vs the local release key.

### How it was surveyed
```sh
gh api "repos/siavash79/PixelXpert/compare/canary...Codecity001:PixelXpert:canary"
```
Their VoLTE/VoWiFi code is structurally unchanged (still initialised from
`PhoneStatusBarViewController.onViewAttached`), and their issue tracker has no VoLTE/VoWiFi
reports -- so they do not answer the Compose-root question in qpr3-statusbar.

### Three-way comparison: upstream vs ours vs community fork (2026-10-08)
Fetched without adding a remote:
```sh
git fetch https://github.com/Codecity001/PixelXpert.git canary:refs/compare/codecity-canary
git merge-tree --write-tree --name-only --no-messages patch refs/compare/codecity-canary
```
- Common base: upstream `3f761368`. Upstream `canary` = base + 1 (archive notice `354cb462`).
  Ours (`patch`) = base + 22, behind upstream by that 1 README commit. Theirs = base + 141 (they
  merged then reverted the archive notice).
- Files changed since base: ours 142 (incl. `claude/`, nix, CI), theirs 73, both 29.
- Dry-run merge: 16 conflicts. 11 are CI/build/metadata where BOTH forks diverged from upstream on
  purpose (`.github/*`, `MagiskModBase/module.prop`, `MagiskModuleUpdate_*.json`, `latestCanary.json`,
  `README.md`, `app/PXTasks.gradle.kts`, `buildSrc/.../BuildUtils.kt`, `GitTagProvider.kt`) -- keep ours.
  Only 3 code conflicts, each "our logging hunk vs their real fix":
  - `xposed/XPrefs.java` -- ours: logging (`65c73ddc`, +9); theirs: thread-safe `runningMods` +
    guarded pref dispatch (`c654ba82`, +8/-2).
  - `modpacks/android/ScreenOffKeys.java` -- ours: logging (+4/-1); theirs: double-tap power vs
    Wallet/Camera + flashlight/power-wake fixes (+190/-34).
  - `modpacks/launcher/CustomNavGestures.java` -- ours: logging (+16/-5); theirs: launcher crash /
    navbar restart / proxy binding on A15+ (+98/-21).
  Resolution pattern: take theirs, re-apply our log calls on top.
- Auto-merging overlaps (13 files): `StatusbarMods`, `SystemUtils`, `XPLauncher`, `KeyguardMods`,
  `StatusIconTuner`, `BatteryDataProvider`, `SettingsActivity`, `PreferenceHelper`, `strings.xml`,
  `misc_prefs.xml`, `quicksettings_prefs.xml`, `statusbar_settings.xml`, `.gitignore`. Their new prefs
  land in the old XML files; after our settings reorg some may need moving to the right umbrella.

### Scope list difference (lead for recents-force-close and CallVibrator)
`app/src/main/resources/META-INF/xposed/scope.list`: base and ours list `android`; theirs replaced it
with `system` (`ebeb7b1b` added `system`, `c226b95e` removed `android` "to prevent duplicate
onPackageReady invocations and ModPack instantiation in system_server"). Our system_server
modpacks -- the force-close FORCE_STOP grant in `PackageManager.java` and the retargeted
`CallVibrator` -- depend on how the framework scope loads under the LSPosed API 100 migration.
UNVERIFIED whether this explains our grant hook not firing; test it in recents-force-close.

### Feature overlap
| Feature | Upstream | Ours | Theirs |
|---|---|---|---|
| Recents force close | -- | in progress | -- |
| Settings reorg (7 umbrellas) | -- | done | -- |
| Leveled diagnostic logging | -- | done | -- |
| Leveled flashlight tile | yes | removed (stock has it) | kept; added high-brightness tile |
| QS brightness slider | -- | open todo | done (`2b1f49cc`) |
| SBNIC NPE crash fix | -- | -- | done (`5749e2f1`) |
| A17 Compose clock / chips | -- | -- | done |
| Module install | priv-app | priv-app | user app (`d3f338c9`) |
| Own update manifest | upstream JSON | open todo | done (own JSON + release CI) |
| Double-tap to sleep A17 | broken | open bug (wake) | fixed (sleep) |

### High value for us (A17 QPR3 / our open bugs)
- `5749e2f1` fix(StatusbarMods): guard `AODNIC` and `SBNIC` separately -- exactly our crash
  (2-line change at the `maxIcons` block).
- `d58d7a13`, `45a6a543`, `cde529b6`, `6415443f`, `f106c71a`: A17 Compose clock hook
  (`ClockInteractor`), clock repositioning/format, ongoing-activity chip placement.
- `dcdca552` Network Traffic mid-right visibility; `12eb1571`, `54dd13de` NetworkTraffic freeze/spikes;
  `f40801bb` NetworkTraffic view disappearing.
- `77fcf97c` StatusIconTuner hiding icons; `60ff0292` StatusbarGestures QQS pulldown on CP3A/CP41;
  `7eec5d19` NotificationExpander on CP3A/CP41; `b238177d` PIN scrambler for Compose keyguard.
- `58d9ec0d` BatteryDataProvider `calculateChargingSpeed` signature change on A17 QPR.
- `78fac00e`, `2fbc1b34` ScreenGestures double-tap-to-sleep on A17 QPR1+ (related to our open
  double-tap-to-wake bug -- check before debugging ours).
- `3e216bc2` ScreenOffKeys double-tap power vs Wallet/Camera.
- `6262dfe6`, `e56dec8e`, `5fcb1d2d` EasyUnlock rework (on our verify backlog).
- `964116bf` CustomNavGestures launcher crash / navbar restart on A15+.
- `c654ba82` thread-safe `runningMods` + guarded preference dispatch; `69d6da72` KeyguardMods
  multi-method hook type; `b4b228da` duplicated carrier-text hooks.
- `12bc45fc` clipboard overlay smart-actions hook; `3ca5a7f6`, `d6787ecd` dialer Call Notes /
  recording announcements on A17.

### Packaging / root (KernelSU relevance)
- `d3f338c9` migrate the module to install as a USER app: drops `system/priv-app`, bundles the APK
  at the zip root, installs via `pm install` in `service.sh`, backs up/restores prefs, removes
  `sepolicy.rule`, and avoids KernelSU "umount modules by default" bootloops. Follow-ups:
  `c2c6ea9b` (install during flash, allow downgrades), `dc243850` (migration from system app).
- `817c6c25`, `bcf018a5`, `ca6cb6cb`: sepolicy + root-grant handling for KernelSU/APatch.
- `2587bb18`/`a7d8cb96`/`94f6ee13`: post-boot restart of SystemUI/Launcher/Dialer so LSPosed hooks
  apply (added, dropped, restored -- unstable decision on their side; `f84d57ab`/`6ed11bb9` are
  marked TEMP).

### New features (optional)
- `2b1f49cc`, `417e718b`, `c2a1b4e8` QSBrightnessSlider (bottom QS + QQS slider) -- overlaps our
  open "QS brightness slider" item; credits PixelXpert-Next.
- `6a53c42c`, `1a0608e2`, `5cade719` Caffeine QS tile; `14e2b8c8` high-brightness flashlight tile.
- `62221b17` AdbWifiPortPin (static wireless ADB port).
- StatusbarSize no-cutout layout series (`ab08e8ae` .. `64da49a8`).

### Not for us
CI/branding/release-workflow commits (`421b9ffa`, `b329b663`, `625c97f9`, `81abb5c0`, `f244bd0b`,
`57f03070`, README/Telegram links) -- our CI and versioning already diverged.

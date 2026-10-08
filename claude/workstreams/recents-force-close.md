---
name: recents-force-close
description: Recents task menu "Force close" row in Pixel Launcher quickstep -- FORCE_STOP_PACKAGES grant hook in system_server (AMS checkCallingPermission) not firing on A17
status: blocked
---

# Recents "Force close"

## Current state
Add a "Force close" row to the Launcher3 quickstep recents task menu (`@LauncherModPack`,
`com.google.android.apps.nexuslauncher` -- [[recents-task-menu-lives-in-quickstep]]). Toggle
`RecentsForceCloseEnabled` (default OFF). Files: `xposed/modpacks/launcher/RecentsForceClose.java`
(row + force-stop) and `xposed/modpacks/android/PackageManager.java` (the FORCE_STOP_PACKAGES grant).
Design spec: `docs/superpowers/specs/2026-06-20-recents-force-close-design.md`.

WORKING: row appears; styled by re-inflating the launcher's own option-row layout via
`View.getSourceLayoutResId()` (`ic_cross` glyph); task resolves correctly on A17 (via
`menu.getTaskContainer().getTask()` / `getTaskView().getFirstTaskContainer()` -- A17 dropped the old
`TaskView` field + `getTask()`).

BLOCKED: the force-stop. The launcher calls `forceStopPackageAsUser` and system_server throws
`SecurityException ... requires android.permission.FORCE_STOP_PACKAGES`. The grant hook's one-shot
diagnostic never logs at tap time => the `checkCallingPermission` grant hook is not installed/firing
in system_server.

UNCOMMITTED in the working tree (keep -- not yet verified on device):
- `PackageManager.java`: `getLauncherAppId(Context)` resolves the launcher uid via AMS's own
  `mContext` (`getObjectField(param.thisObject,"mContext")`) instead of the modpack's; one-shot
  FORCE_STOP diagnostic; grant compares `Binder.getCallingUid() % 100000` to the launcher app-id.
  (First attempt `19a8ee15` is committed.)
- `RecentsForceClose.java`: `copyLayoutParams` clones the template row's live LayoutParams onto our
  row to fix a centered-instead-of-left-aligned look.

## Next actions
1. Read the BOOT log (hook installs at boot, not tap; every prior attempt grepped the rotated
   post-boot buffer and saw nothing):
   `adb logcat -c && adb reboot && adb wait-for-device && timeout 90 adb logcat -v time > boot.log`
   then `grep -iE "PixelXpert|Hook NOT installed|checkCallingPermission|PackageManager:" boot.log`.
2. Follow the decision tree under Findings. New lead (2026-10-08): the community fork replaced
   `android` with `system` in `META-INF/xposed/scope.list` for the LSPosed API 100 migration -- check
   whether our `@FrameworkModPack` loads under `android` at all (see community-fork-sync).
3. Verify the `copyLayoutParams` alignment fix on device.

## Decisions
<!-- APPEND. YYYY-MM-DD: what was decided, why, what was rejected. -->
- 2026-06-21: grant the permission via an Xposed AMS hook, not the ROM route (privapp allowlist +
  launcher `<uses-permission>`). Rejected because privapp permissions only grant perms the app
  DECLARES, and stock Pixel Launcher does not declare FORCE_STOP_PACKAGES.

## Findings

### AMS permission check (proven by decompiling the device services.jar)
`forceStopPackage` (~line 3495 of the decompiled `ActivityManagerService.java`) checks at ~3509
`checkCallingPermission("android.permission.FORCE_STOP_PACKAGES")`, and `checkCallingPermission`
is declared on AMS (~5464). So the hooked method name is correct.

### Decision tree for the boot log
- No PixelXpert framework lines at all => the `@FrameworkModPack` is not loading in system_server;
  investigate framework-scope loading in `XPLauncher.java` (~L90-130 captures the framework `mContext`).
- `Hook NOT installed: no method 'checkCallingPermission'` => STRONGEST SUSPECT.
  `HookHelper.hookAllMethods` uses `clazz.getDeclaredMethods()` (NOT inherited); if at runtime the
  method is on a SUPERCLASS of the obfuscated AMS, it is missed. Fix: hook
  `checkPermission(String,int,int)` instead, walk the superclass chain, or hook the static
  `checkComponentPermission`. (`hookAllMethods` does NOT throw on a missing method -- it logs and
  returns empty -- so a missing `checkBroadcastFromSystem` hooked just before it does NOT skip the
  grant; that theory is ruled out.)
- Diagnostic DOES appear => the bug is in the grant condition; read the logged
  `launcherAppId`/`callingAppId` (e.g. `launcherAppId=-1` => `getPackageUid` failed; resolve the
  launcher uid another way).

### ROM research (crDroid / Evolution X / DerpFest, 2026-06-21)
All call `forceStopPackage` IN the launcher process via a `SystemShortcut` in
`TaskShortcutFactory`/`TaskOverlayFactory` MENU_OPTIONS, and grant the perm via a
privapp-permissions allowlist + `<uses-permission>` in the launcher manifest -- not usable here (see
Decisions). Fallbacks if the hook proves unfixable: (1) styling -- register a native `SystemShortcut`
instead of view injection (perfect native styling, bigger change); (2) permission -- do the kill in
SystemUI (holds the perm natively) and signal it from the launcher.

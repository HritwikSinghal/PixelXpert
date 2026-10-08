# Design: "Force close" in the Recents task menu

> Date: 2026-06-20 | Project: PixelXpert fork (Phase 3 custom feature)

## Goal

Add a "Force close" entry to the menu that appears when the user taps an app's
title/icon on the Android Recents (Overview) screen -- the menu that currently
shows "App info", "Split screen", "Pin", "Pause app", etc. Tapping "Force close"
force-stops the app's package and removes its tile from Recents.

The feature is gated behind a settings toggle (default OFF), per PixelXpert's
"everything is optional" convention. It is implemented as a single, isolated
`@LauncherModPack` so it stays easily rebasable on the `patch` branch.

## Decisions (locked in during brainstorming)

- **Behavior:** force-stop the package, then dismiss its tile from Recents, then
  close the menu. A short Toast confirms.
- **Toggle:** `MaterialSwitchPreference`, key `RecentsForceCloseEnabled`,
  default OFF. Lives in `misc_prefs.xml` next to the related Clear All toggle.
- **Label:** "Force close".
- **Injection strategy:** direct view injection (NOT subclassing
  `SystemShortcut`). Rationale: the project has no `dexmaker`/`ProxyBuilder`
  dependency, so subclassing the abstract `SystemShortcut` at runtime would
  require adding a build dependency and divergence from upstream. View injection
  matches the existing `ClearAllButtonMod` / `NotificationExpander` patterns and
  adds no dependency.
- **Risk accepted:** quickstep task-menu internals are version-specific and
  partially obfuscated; the hook is defensive and may need re-tweaking after
  large Pixel Launcher updates.

## Target & context (verified in codebase)

- Target package: `com.google.android.apps.nexuslauncher`
  (`Constants.LAUNCHER_PACKAGE`). Already hooked via `@LauncherModPack`.
- The Recents task menu lives in quickstep, NOT SystemUI. On current Pixel
  Launcher it is `com.android.quickstep.views.TaskMenuView`; newer builds use
  `com.android.quickstep.views.TaskMenuViewWithArrow`. Menu options are built
  from `SystemShortcut` factories via
  `com.android.quickstep.TaskOverlayFactory#getEnabledShortcuts`.
- Reusable force-stop logic already exists in
  `CustomNavGestures.killForeground()`
  (`app/src/main/java/.../modpacks/launcher/CustomNavGestures.java:309-324`):
  `ActivityManager.forceStopPackageAsUser(packageName, userId)` via reflection.
- The launcher already holds `android.permission.FORCE_STOP_PACKAGES`, granted by
  `PackageManager.java:88-104`, so the force-stop call succeeds.
- Single-task dismissal precedent: `ClearAllButtonMod` calls
  `RecentsView#dismissAllTasks`; for one task we locate the single-task dismiss
  method (`RecentsView#dismissTask(TaskView, ...)` / a `TaskView`-level dismiss)
  by best-match.

## Architecture

A single new mod class, isolated and self-contained:

`app/src/main/java/sh/siava/pixelxpert/xposed/modpacks/launcher/RecentsForceClose.java`

```
@LauncherModPack
public class RecentsForceClose extends XposedModPack {
    // pref: RecentsForceCloseEnabled (default false)
    // onPreferenceUpdated -> read the boolean
    // onPackageLoaded -> hook the task-menu population
}
```

### Components / responsibilities

1. **Preference gate.** `onPreferenceUpdated(String... Key)` reads
   `Xprefs.getBoolean("RecentsForceCloseEnabled", false)`. When the hook fires it
   returns early if disabled.

2. **Menu hook.** In `onPackageLoaded`, resolve the task-menu class with
   `ReflectedClass.ofIfPossible("com.android.quickstep.views.TaskMenuView")` and,
   if null, fall back to `TaskMenuViewWithArrow`. Hook the method that finishes
   populating/laying out the menu options (after the native options are added).

3. **Task resolution.** From the menu instance, obtain the associated `TaskView`
   (held as a field) -> `Task` -> extract `packageName` and `userId`
   (`Task.key.getComponent()/baseIntent` package, `Task.key.userId`). Resolve
   field/method names by best-match where obfuscation is possible.

4. **Row injection.** Inflate a menu-option row that matches the existing
   siblings (clone an existing option child's layout/styling so it looks native),
   set the label to the "Force close" string and a stop/close icon (vector
   drawable from module resources), and append it to the option container at the
   bottom of the list.

5. **Click action.** On tap:
   - `ActivityManager.forceStopPackageAsUser(packageName, userId)` via reflection
     (same call as `CustomNavGestures.killForeground()`).
   - Dismiss this task's tile (single-task dismiss on `RecentsView`/`TaskView`,
     method located by best-match).
   - Close the menu and show a short confirmation Toast.

### Data flow

```
tap "Force close" row
  -> read TaskView -> Task -> {packageName, userId}
  -> ActivityManager.forceStopPackageAsUser(packageName, userId)
  -> dismiss the TaskView tile from RecentsView
  -> close menu + Toast
```

### Settings UI

- `app/src/main/res/xml/misc_prefs.xml`: add a `MaterialSwitchPreference`
  (key `RecentsForceCloseEnabled`, default false, `summaryOn`/`summaryOff`,
  title `@string/recents_force_close_title`) near the Clear All reposition toggle.
- `app/src/main/res/values/strings.xml`: add `recents_force_close_title`
  (and summary if a custom one is wanted; otherwise reuse `general_on`/
  `general_off`).
- New vector drawable for the row icon (or reuse an existing stop/close drawable
  in module resources).

## Error handling & robustness

- All class lookups via `ReflectedClass.ofIfPossible(...)`; bail (log + return)
  if the menu class is not found on the running launcher.
- Locate fields/methods by best-match/signature rather than hardcoded names where
  obfuscation is likely (mirrors `CustomNavGestures.saveFocusedTask()`'s
  field-scanning approach).
- Wrap the injection and the click handler in try/catch so a launcher update can
  never crash the menu; worst case the entry silently does not appear.
- Edge cases: null/locked task, system/persistent apps that cannot be force
  stopped (the call is best-effort; wrap in try/catch), and a task whose
  component/package cannot be resolved (skip injecting the row).

## Testing

This is Xposed/reflection code hooking closed-source quickstep internals, so there
is no practical unit-test harness in-repo. Verification is manual on-device:

1. Build the module, flash, reboot.
2. With the toggle OFF: confirm the task menu is unchanged (no "Force close").
3. Toggle ON: open Recents, tap an app title -> "Force close" appears at the
   bottom of the menu, styled like the native options.
4. Tap it: the app is force-stopped (verify the process is gone), its tile is
   removed from Recents, the menu closes, and the Toast shows.
5. Edge cases: a system app (best-effort, no crash), rapidly opening/closing the
   menu, and confirming OFF state cleanly removes the entry after a respring.

## Out of scope (YAGNI)

- No confirmation dialog (decided: act immediately).
- No support for non-Pixel launchers.
- No additional menu entries beyond "Force close".

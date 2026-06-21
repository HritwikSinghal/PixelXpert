# Handoff: implement "Force close in Recents" (Phase 4)

> **STATUS: COMPLETED 2026-06-21 (Session 3).** This handoff is fulfilled -- the feature is
> implemented in `app/.../modpacks/launcher/RecentsForceClose.java` (toggle `RecentsForceCloseEnabled`,
> string `recents_force_close_title`) and merged into `patch` as the `feat: add opt-in Force close
> button to the Recents task menu` commit. Kept as a
> historical record of the original scope/decisions; safe to delete. See `claude/progress.md` Phase 4
> for the as-built summary.
>
> Written 2026-06-20 for a fresh-context agent. The cleanup work (Phase 1-3) is done and
> CI-green on `patch`. This hands off the next feature: implementing Force close in Recents.

## Copy-paste prompt for the next agent

```
You are continuing the PixelXpert fork (repo: /home/hritwik/Projects/PixelXpert, branch: patch).
Read these first, in order, then stop and confirm scope with me before coding:
  1. claude/progress.md            -- project state; Phase 1-3 done, Phase 4 is this feature
  2. docs/superpowers/specs/2026-06-20-recents-force-close-design.md  -- the LOCKED design
  3. The project memory "recents-task-menu-lives-in-quickstep" (key code locations)
  4. CLAUDE.md                     -- fork/branch/build/CI conventions

Task: implement the "Force close" entry in the Android Recents task menu exactly as the
design spec describes. It is an isolated @LauncherModPack, gated behind a default-OFF toggle.
Do NOT redesign -- the approach (direct view injection, reuse killForeground(), no new deps)
is already decided. Follow the existing modpack patterns (ClearAllButtonMod / NotificationExpander).

Constraints:
  - This is Xposed/reflection code against obfuscated quickstep internals -- be defensive
    (ReflectedClass.ofIfPossible, best-match field/method lookup, try/catch around inject + click).
  - One isolated, easily-rebasable change set; match surrounding code style.
  - No on-device test harness exists; verification is the manual on-device checklist in the spec.
    Build is validated via CI (push to patch -> Fork Build). Use `gh -R HritwikSinghal/PixelXpert`.
  - Commit logically; squash incidental fixups into their working commit; confirm before any force-push.
  - Update claude/progress.md as you complete steps.
```

## What's already true (so the next agent doesn't re-derive it)

- **Where the menu lives:** Launcher3 quickstep (`com.google.android.apps.nexuslauncher`,
  `Constants.LAUNCHER_PACKAGE`), class `com.android.quickstep.views.TaskMenuView` (newer Pixels:
  `TaskMenuViewWithArrow`). NOT SystemUI. Hook via `@LauncherModPack`.
- **Force-stop already implemented:** `CustomNavGestures.killForeground()`
  (`app/src/main/java/.../modpacks/launcher/CustomNavGestures.java:309-324`) --
  `ActivityManager.forceStopPackageAsUser(pkg, userId)` via reflection. The launcher already holds
  `android.permission.FORCE_STOP_PACKAGES` (granted in `PackageManager.java:88-104`).
- **Single-task dismiss precedent:** `ClearAllButtonMod` uses `RecentsView#dismissAllTasks`; for one
  task, locate the single-task dismiss (`RecentsView#dismissTask` / a `TaskView`-level dismiss) by best-match.
- **No `dexmaker`/`ProxyBuilder`** in the project -> cannot subclass the abstract `SystemShortcut` at
  runtime -> use direct view injection (clone an existing menu-option child for native styling).
- **Settings:** toggle `RecentsForceCloseEnabled` (default false) in `app/src/main/res/xml/misc_prefs.xml`
  near the Clear All reposition toggle; string `recents_force_close_title` in `values/strings.xml`.

## Files to create / touch

- NEW: `app/src/main/java/sh/siava/pixelxpert/xposed/modpacks/launcher/RecentsForceClose.java`
- `app/src/main/res/xml/misc_prefs.xml` (add the toggle)
- `app/src/main/res/values/strings.xml` (add the title string; reuse generic on/off summaries)
- A vector drawable for the row icon (or reuse an existing stop/close drawable)
- Register the modpack if the framework needs explicit registration (check how other
  `@LauncherModPack` classes are picked up).

## Suggested build sequence

1. Skeleton modpack + preference gate + class resolution (defensive), no-op click. Build via CI.
2. Task resolution (TaskView -> Task -> packageName/userId) + the click action (force-stop + dismiss + Toast).
3. Native-looking row injection + icon + string.
4. Settings toggle wiring + OFF-state cleanup.
5. Manual on-device verification per the spec's checklist; mark Phase 4 done in progress.md.

## Out of scope (per spec)

No confirmation dialog, no non-Pixel launcher support, no extra menu entries.

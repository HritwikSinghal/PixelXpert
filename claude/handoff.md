# Handoff: PixelXpert fork -- continue Phase 4 (custom features + 1 bug)

> Written 2026-06-21 (Session 5) for a fresh-context agent. Phases 1-3 (fork tracking, build CI,
> repo/README cleanup) are DONE and CI-green on `patch`, plus a round of build/CI/mod hardening.
> Phase 4 (rolling custom features) has 3 shipped + the CallVibrator bug fixed (committed `7a6e4a18`,
> pending on-device verify); 4 items remain open. Session 6 also landed a Settings UI reorganization
> (9 -> 7 umbrella categories; commits `b72d4a54`/`9aa71bca`; see progress.md Phase 5 + tasks.md).
> Supersedes `claude/handoff-recents-force-close.md` (that feature shipped; the file is historical).

## Copy-paste prompt for the next agent

```
You are continuing the PixelXpert fork (repo: /home/hritwik/Projects/PixelXpert, branch: patch).
PixelXpert is an Xposed+Magisk module that customizes Pixel SystemUI / Settings / Launcher3 on
Android 16 QPR. Read these first, in order, then STOP and confirm scope + pick an item with me
before coding:
  1. claude/progress.md   -- full project state; Phase 4 list has the 5 open items with file:line scope
  2. CLAUDE.md            -- fork/branch/build/CI conventions (READ the Fork Maintenance section)
  3. Project memory index: claude memory MEMORY.md, especially:
       - modpack-scope-list-requirement  (a modpack targeting a package absent from scope.list
         silently never loads -- this IS the CallVibrator bug below)
       - xposed-findmethodbestmatch-throws  (reflective-lookup gotcha; null-guard fallbacks are dead code)
       - in-app-updater-manifest-source, settings-homepage-entry-placement, recents-task-menu-lives-in-quickstep

Do NOT start coding until we agree which item to take. Each item is an isolated, easily-rebasable
change set on `patch`. For a feature build, brainstorm/lock the design first (use the brainstorming
skill); for the bug, verify the on-device check before changing code.

Hard constraints:
  - Xposed/reflection against obfuscated SystemUI/quickstep internals: be DEFENSIVE
    (ReflectedClass.ofIfPossible, best-match field/method lookup, try/catch around hook + body).
    Note: findMethodBestMatch THROWS on miss (never returns null) -- see the memory; don't write
    null-guard fallbacks for it.
  - A new modpack ONLY loads if its target package is in app/src/main/resources/META-INF/xposed/scope.list
    (mirror res/values/xp_module_scope.xml). Adding a modpack for a new package REQUIRES adding scope.
  - No on-device test harness exists. Compile-correctness is verified against codebase APIs; runtime
    acceptance = green Fork Build CI (push to patch) + an on-device checklist. gradlew may not be
    executable in this env -- rely on CI. Use `gh -R HritwikSinghal/PixelXpert ...` (gh defaults to upstream).
  - Branch model: `canary` = clean upstream mirror (never commit custom work there); all work on `patch`.
  - Commit logically; squash your own incidental fixups into their working commit; record BASE=$(git
    rev-parse HEAD) at task start; NEVER rewrite history at/below that base; confirm before any force-push.
  - Update claude/progress.md as you complete steps (mark [x], recount the Status Summary table).
```

## Current state (so the next agent doesn't re-derive it)

- **Done in Phase 4:** Force close in Recents (`RecentsForceClose.java`, toggle `RecentsForceCloseEnabled`);
  Settings homepage entry placement (`PXSettingsLauncher.java`); removal of the redundant
  Flashlight-leveled tile (stock A16 QPR3 added it natively). All three were further hardened in
  commit `ddcf814a` / `9f29091e`.
- **Build/CI:** Fork Build runs on push to `patch` (artifacts); `fork-v*` tags publish a GitHub Release.
  Versioning + signing pipeline was hardened in Session 5 (commits `c586f489`, `8c099810`, `f3f8c533`).

## The Phase-4 items (CallVibrator fixed/verify-pending; 4 still open) -- full scope in progress.md

1. **[BUG] Vibrate on call answered/disconnect** -- DONE, committed `7a6e4a18` (pending on-device verify).
   On-device check (`adb shell ps -A | grep -i telecom`) returned nothing -> Telecom is hosted in
   system_server, not its own process. Retargeted `CallVibrator` from `@TelecomServerModPack` to
   `@FrameworkModPack` (package `android`, in scope) and dropped the telecom-specific process
   annotations. REMAINING: confirm via green Fork Build CI + an on-device call (vibrate on answer +
   on disconnect). If still silent, check the `onCallStateChanged` arg layout on A16 QPR. Optional
   cleanup: delete the now-unused `TelecomServerModPack` annotation file (keep the
   `TELECOM_SERVER_PACKAGE` constant -- still used by `XPLauncher`).
2. **Bluetooth device battery in status bar** (from crDroid) -- CLEAN FIT. New `@SystemUIModPack`
   `BluetoothBatteryIcon.java` following the existing VoLTE-icon pattern in `StatusbarMods.java`
   (StatusBarIconController slot system). Scope/hook points are in progress.md.
3. **QS brightness slider visibility + position** (from crDroid) -- DO A FEASIBILITY SPIKE FIRST.
   A16 QS is the new Compose stack; the slider may not be a plain View with a stable parent, so the
   usual setVisibility / removeView+addView reordering may not apply. Confirm the hook surface before
   committing to an approach.
4. **Reboot actions inline in main power menu** -- surface the 3 advanced-reboot actions directly in
   the main power grid in place of "Advanced..." (they already exist nested under it in
   `PowerMenu.java`). Needs a small design decision (replace vs toggle; reuse `advancedPowerMenu` pref).
5. **Updates tab repoint (in-app self-update)** -- BIGGEST / has a release dependency. The in-app
   updater still pulls upstream's manifest JSON; the fork must publish its own manifest (on `patch`,
   NOT `canary`) wired into the `fork-v*` Release pipeline before the URL repoint means anything.
   Needs design decisions (channel split, who bumps the manifest). Tackle last.

# PixelXpert (Fork)

This is a maintained fork of upstream [`siavash79/PixelXpert`](https://github.com/siavash79/PixelXpert),
a mixed Xposed+Magisk module for Pixel ROM customizations.

## Fork Maintenance

Branch model:
- **`canary`** -- clean mirror of upstream `siavash79/PixelXpert:canary`. Never commit custom work here.
  Sync with: `git fetch upstream && git checkout canary && git merge --ff-only upstream/canary && git push origin canary`.
- **`patch`** -- the fork's default branch; carries all custom features on top of `canary`.
  After syncing canary, bring patch up to date with `git checkout patch && git rebase canary` (then force-push).

`patch` has *intentionally* diverged from upstream: deleted upstream-only CI workflows, a
rewritten versioning system, and fork-pointed metadata (module.prop, JSON, README). As a result,
rebasing `patch` onto a freshly synced `canary` will conflict on those files -- resolve all such
conflicts in favor of the fork (`patch`), not upstream.

Remotes: `origin` = `HritwikSinghal/PixelXpert` (the fork), `upstream` = `siavash79/PixelXpert`.
Note: `gh` defaults to the `upstream` remote, so pass `-R HritwikSinghal/PixelXpert` to target the fork.

Build: `./gradlew buildCanary -Pchannel=canary` -> reads/increments the version via the typed
`VersionInfo` source of truth (format-aware writers, no regex) -> `assembleRelease` ->
zips to `output/PixelXpert.zip` (flashable Magisk module containing the APK).

CI: `.github/workflows/forkBuild.yml` runs on push to `patch`, building the zip + APK and uploading
both as Actions artifacts named `PixelXpert-<branch>-<short7hash>.{zip,apk}`. It is now the **only**
workflow on `patch` -- the upstream-only workflows (which clone `siavash79/PixelXpert` directly)
were deleted. The fork builds artifacts on every push but does NOT commit version bumps back.

## Long-Running Project

This project uses session-persistent tracking. At the start of every session:
1. Read `claude/progress.md` silently for a full catch-up -- do not ask the user to re-explain anything.
2. Do NOT automatically continue working -- wait for the user to indicate they want to proceed.
3. After each completed task, update `claude/progress.md` immediately (mark `[x]`, recount Status Summary, update date).
4. `claude/progress.md` is the primary task tracker. Use `claude/tasks.md` only for ad-hoc items outside the long-running plan.

# PixelXpert (Fork)

This is a maintained fork of PixelXpert, a mixed Xposed+Magisk module for Pixel ROM customizations.
Its upstream is the community fork [`Codecity001/PixelXpert`](https://github.com/Codecity001/PixelXpert)
("PixelXpertFork"), which carries the Android 17 fixes; the original
[`siavash79/PixelXpert`](https://github.com/siavash79/PixelXpert) is archived (final build canary-499).

## Fork Maintenance

Branch model:
- **`canary`** -- clean mirror of upstream `Codecity001/PixelXpert:canary`. Never commit custom work here.
  Sync with: `git fetch upstream && git checkout canary && git merge --ff-only upstream/canary && git push origin canary`.
- **`patch`** -- the fork's default branch; carries all custom features on top of `canary`.
  After syncing canary, bring patch up to date with `git checkout patch && git rebase canary` (then force-push).

`patch` keeps upstream's versioning (`buildSrc`, `version.properties`, `app/PXTasks.gradle.kts`,
`app/build.gradle.kts`), packaging (user app: APK at the zip root, installed by
`MagiskModBase/customize.sh`/`service.sh`) and CI workflow files, so those rarely conflict.
Fork-only divergence is kept small and marked "Fork-specific" in comments: the release URL in
`BuildUtils.kt`, debug-build signing in `app/build.gradle.kts`, fork-pointed metadata (module.prop,
update JSONs, latestCanary.json, README) and our `forkBuild.yml`. Resolve conflicts on those toward
the fork; for code conflicts take upstream's fix and re-apply our hunk on top (usually our `log*`
calls). After a rebase, also diff every config file the build READS -- a silently merged
`version.properties` once broke versioning with no conflict.

Remotes: `origin` = `HritwikSinghal/PixelXpert` (the fork), `upstream` = `Codecity001/PixelXpert`,
`original` = `siavash79/PixelXpert` (archived; reference only).
Note: `gh` defaults to a non-origin remote, so pass `-R HritwikSinghal/PixelXpert` to target the fork.

Build: `./gradlew buildCanary -Pchannel=canary` (or `nix run .#zip`) -> upstream's versioning bumps
`CANARY_VERSION_CODE` in `version.properties` and stamps module.prop + the update JSONs ->
`assembleRelease` (APK renamed in place to `app/build/outputs/apk/release/PixelXpert.apk`) -> zips
to `output/PixelXpertFork-canary-<N>.zip` (flashable Magisk module, APK at the zip root).

CI: `.github/workflows/forkBuild.yml` runs on push to `patch`, building the zip + APK and uploading
both as Actions artifacts named `PixelXpert-<branch>-<short7hash>.{zip,apk}`; `fork-v*` tags publish a
Release. The fork builds artifacts on every push but does NOT commit version bumps back. Upstream's
workflows also live in the tree (crowdin*, makeCanaryRelease, makeStableRelease, makeCanaryTestPackage,
*PackageTestBuild); they target upstream's branding and release flow, so keep them disabled in this
repo's Actions settings (`gh workflow disable -R ...`) -- `makeCanaryRelease` and `crowdin_upload`
trigger on pushes to `canary`, which the mirror sync does.

## Long-Running Project

This project uses session-persistent tracking in `claude/`. At the start of every session:
1. Read `claude/todo.md` and `claude/progress.md` silently for a full catch-up -- do not ask the user to re-explain anything.
2. Open a `claude/workstreams/<slug>.md` only when the task matches its manifest line. Do not bulk-read them.
3. Do NOT automatically continue working -- wait for the user to indicate they want to proceed.
4. After each completed task, update the tracker immediately: mark `[x]` in `claude/todo.md`, recount the Status Summary and rewrite `## Current state` in `claude/progress.md`, and record any topic detail in that topic's workstream file.

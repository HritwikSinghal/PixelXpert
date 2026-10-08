---
name: release-pipeline
description: Fork release pipeline -- canary-<N> GitHub releases via manual forkBuild run, version-bump commit-back, update JSON zipUrl, Obtainium / KSU manager / in-app updater updates
status: active
---

# Release pipeline (canary-<N>)

## Current state
Two releases cut 2026-10-08. `canary-525` (run 37790571253) is verified server side: the zip's
`module.prop` and bundled APK (`aapt2 dump badging`) are both versionCode 525, every update JSON
zipUrl returns 200, and the zip has no `system/` overlay. It is installed on the device.
`canary-526` (run 37797365634) is published with both assets, and CI pushed "Version update:
canary-526" (`dc11294a`); it is not flashed yet. The device-side updater check is still open.

## Next actions
1. With 525 installed, check that the KSU manager and the in-app Updates tab offer 526. That proves
   the commit-back plus manifest path end to end.
2. To cut the next release: `git pull` first (CI pushes the bump commit to `patch`), then
   `gh workflow run forkBuild.yml -R HritwikSinghal/PixelXpert --ref patch`. A rebase drops the
   accumulated version commits and re-stamps once -- `docs/rebasing-on-upstream.md` step 4.
3. Housekeeping: `actions/checkout@v4` and `actions/upload-artifact@v4` run on deprecated Node 20
   (CI annotation) -- bump when convenient.

## Decisions
- 2026-10-08: releases are cut by `workflow_dispatch` on `patch`, not by tag push -- the version is
  only known after upstream's bump runs in CI. The job commits the bumped files back to `patch`
  (GITHUB_TOKEN pushes do not re-trigger workflows) and tags `canary-<N>`.
- 2026-10-08: asset keeps upstream's name `PixelXpertFork-canary-<N>.zip` because upstream's bump
  writes `zipUrl = .../releases/download/<N-name>/PixelXpertFork-<N-name>.zip` (repo owner patched to
  this fork in `buildSrc/.../BuildUtils.kt`, marked Fork-specific).
- 2026-10-08: in-app updater (`UpdateFragment.java`) reads `patch/latestCanary.json` for BOTH
  channels; the fork publishes canary only. Upstream's manifests would offer builds signed with a
  different key (install would fail).

## Findings
- Workflow paths: push to `patch` = artifacts only (`PixelXpert-<ref>-<sha7>.{zip,apk}`); manual run
  = artifacts + commit-back + tag + Release. Guards: release only from `refs/heads/patch`; fails
  without `SIGNING_KEY`; fails if tag exists; asserts the zip's `module.prop` versionCode equals
  `CANARY_VERSION_CODE`; plain (non-force) push so a moved `patch` fails the run.
- Files the bump writes (upstream `app/PXTasks.gradle.kts` `incrementCanaryVersion`):
  `version.properties`, `MagiskModBase/module.prop`, `latestCanary.json`,
  `MagiskModuleUpdate_Xposed.json`, `MagiskModuleUpdate_Full.json`.
- Local end-to-end `buildCanary` (throwaway clone, debug-key fallback) stamped 525 consistently in
  all of those plus the APK; zip root = customize.sh, service.sh, module.prop, sqlite3, META-INF,
  PixelXpert.apk (no `system/`).
- Known risk: upstream's `buildCanary` chains `finalizedBy` (unordered) and stamps the APK version
  at configuration time -- the "APK lags metadata by one build" bug class. Not seen in the local
  run; the workflow's module.prop assertion does NOT cover the APK's own versionCode -- compare
  `aapt dump badging` of the released APK against N after the first release.
- `latestVersion.json` (stable manifest) still carries upstream canary-508 values; unused by the
  updater now.
- Upstream workflows are disabled (not deleted) in Actions; `makeCanaryRelease` and `crowdin_upload`
  would otherwise fire on `canary` pushes.

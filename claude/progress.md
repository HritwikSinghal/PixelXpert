# PixelXpert fork -- Progress
> Last updated: 2026-10-08 | Session: 9

## Current state
Objective: carry a small set of custom features and Android-version fixes on top of upstream
`Codecity001/PixelXpert` (community fork with the A17 fixes; the original repo is archived), and
publish signed `canary-<N>` releases.

Pushed 2026-10-08: `patch` (rebased, privacy-scrubbed) and `canary` (mirror of upstream). Upstream's
8 workflows are DISABLED in this repo's Actions (only "Fork Build" is active). First release
`canary-525` is published and verified server side (assets, version commit-back, update JSONs, APK
versionCode 525). CI pushes a bump commit to `patch` on every release -- pull before working.

Device: runs `canary-525` (flashed 2026-10-08; user app, no priv-app overlay). PixelXpert needed
"System Framework" ticked in Vector (scope lacked `system`) -- after that CallVibrator works and
Recents force-stop works; only the tile dismiss failed (fix committed, unverified). VoLTE/VoWiFi icons
still missing with no PixelXpert log on that path. The phone is SHARED with another session -- ask
before flash/reboot/app restarts.

| Phase | Status | Progress |
|-------|--------|----------|
| 1-3: Fork tracking, Build CI, Repo cleanup | Done | 3/3 |
| 4: Custom features (rolling) | In progress (force-close blocked) | 0/10 open |
| 5: Settings UI reorg | Done | -- |
| 6: Diagnostic logging | Code done; verify in Phase 8 | -- |
| 7: A17 QPR3 compatibility | In progress | 1/7 |
| 8: On-device verification backlog | Pending | 0/9 |
| 9: Release pipeline | In progress (canary-525 released; device check left) | 3/5 |

Workstreams in flight:
- release-pipeline -- canary-525 released + verified server side; on-device updater check left -- `claude/workstreams/release-pipeline.md`
- qpr3-statusbar -- VoLTE/VoWiFi null-view bug confirmed + fix committed, unverified -- `claude/workstreams/qpr3-statusbar.md`
- recents-force-close -- blocked: broken on device; force-stop works after enabling `system` scope; tile-dismiss fix committed, unverified -- `claude/workstreams/recents-force-close.md`
- community-fork-sync -- adopted as upstream and pushed; adversarial review recorded -- `claude/workstreams/community-fork-sync.md`

## Next actions
1. Install a build with `6d31f7c3` + the VoLTE fix and verify the Recents tile dismiss (next release `canary-526`, or a locally built signed
   APK + launcher restart).
2. qpr3-statusbar: verify VoLTE/VoWiFi icons with the same build; read the verbose `vo_data` line if
   still missing (see workstream Findings).
3. New canary-525 NPEs in Phase 8: `StatusIconTuner.setIgnoredIcons`, `GestureNavbarManager` back hook.
4. Confirm KSU manager + Updates tab see 525 as current; local leftover refs cleanup (needs user OK).

## Decisions (durable)
- Branch model + intentional upstream divergence: resolve `patch`-onto-`canary` rebase conflicts
  toward the fork (deleted upstream CI, rewritten versioning/`PXTasks.gradle.kts`/`buildSrc`, fork
  metadata).
- Git hygiene: only fork commits ABOVE the upstream merge-base are ever rewritten; canary/upstream
  history is untouchable; force-push needs explicit confirmation.
- Build pipeline (Sessions 5-6): version bump is ordered BEFORE assemble and the APK is lazily stamped
  (AGP 9 removed `outputFileName`); `renameReleaseApk` copies to `build/distApk/PixelXpert.apk`,
  which both `createZip` and `forkBuild.yml` consume. Stable channel versions off the nearest `v*` tag.
- 2026-10-08: tracker restructured to `todo.md` + `progress.md` + `workstreams/`; `tasks.md` folded
  into `workstreams/settings-reorg.md` and `todo.md`; `handoff.md` retired (superseded by the tracker).
- 2026-10-08: the repo is public -- no identifying info in `claude/` (rule at the top of `todo.md`).
  Known leak NOT yet fixed: older pushed revisions of the tracker contain a device serial and a local
  home path. Removing them needs a history rewrite + force-push; deferred to an explicit decision.

- 2026-10-08: switched upstream to the community fork `Codecity001/PixelXpert` and rebased our
  commits onto its `canary` (141 commits ahead of the archived original, already carrying A17 QPR
  fixes incl. the SBNIC crash fix). Rejected: cherry-picking selected commits (would leave us
  re-porting their A17 fixes indefinitely). Conflict policy: versioning/metadata/forkBuild -> ours;
  code -> theirs + our log hunks; packaging -> theirs (user app, matches how the device installs it);
  their CI workflows kept alongside ours but must stay disabled in our Actions.
- 2026-10-08: during that rebase, scrubbed the device serial and a local home path from our own
  commits (`git filter-repo --replace-text`, range canary..patch only). Old SHAs may stay fetchable
  on GitHub by hash until garbage-collected; older leaking commits outside `patch` are untouched.

- 2026-10-08: versioning now FOLLOWS UPSTREAM (buildSrc, version.properties, PXTasks,
  app/build.gradle.kts) to keep rebases cheap; our typed-VersionInfo rewrite was removed from history
  ("build: harden versioning pipeline" dropped; "build: re-architect version bumping" reduced to its
  non-versioning hunks). Kept fork-only: release URL in `BuildUtils.kt` (upstream hardcodes its repo),
  debug builds signed with the debug key, forkBuild/flake reading upstream's outputs. Accepted trade-
  off: upstream's `buildCanary` chains `finalizedBy` with no ordering and stamps the APK version at
  configuration time -- the "APK lags metadata by one build" class of bug our rewrite fixed. Verify on
  the next build; if it recurs, fix it UPSTREAM (PR to the community fork) rather than diverging.
  Version line jumps to upstream's (524 -> next 525), above the installed 499.

- 2026-10-08: pushed the rewritten history (force-push of `patch`, approved) after scrubbing the
  remaining personal info: a Signed-off-by trailer, a local plans-file path, and all author/committer
  emails mapped to the GitHub noreply address; repo-local `user.email` set to that address. Deleted
  all `fork-v5.1.1-*` tags + releases (approved) for a clean slate. Upstream workflows disabled in
  Actions (they target upstream's branding/release flow). In-app updater repointed at this fork's
  `patch/latestCanary.json` for both channels (fork publishes canary only).
- 2026-10-08: release model = manual `workflow_dispatch` on `patch` cuts `canary-<N>`; CI commits the
  bumped version files back to `patch` (needed so Magisk/KSU + the in-app updater see updates).
  Rejected: tag-triggered releases (the version is only known after the CI bump).

## Session log
- Sessions 1-8 (to 2026-06-21): fork setup, CI, repo cleanup, force-close, settings reorg, logging.
  Detail in `git log -p claude/progress.md` and the workstream files.
- Session 9 (2026-10-08): diagnosed the A17 QPR3 status bar breakage on-device (SBNIC NPE crash;
  VoLTE path unreachable; `status_bar_root_modernization` enabled). Restructured the tracker.
  Adopted Codecity001/PixelXpert as upstream (rebase), moved versioning to upstream's, scrubbed
  personal info from history, ran 2 bug hunters + 4 refuters (findings in community-fork-sync),
  force-pushed, deleted old releases, wrote the canary-<N> release workflow, pushed it and cut + verified
  `canary-525`.

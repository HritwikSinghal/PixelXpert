# PixelXpert fork -- Progress
> Last updated: 2026-10-08 | Session: 9 (ended; resume from Next actions)

## Current state
Objective: carry a small set of custom features and Android-version fixes on top of upstream
`Codecity001/PixelXpert` (community fork with the A17 fixes; the original repo is archived), and
publish signed `canary-<N>` releases.

`patch` = 14 logical commits above `canary` (recomposed + force-pushed 2026-10-08, tree-identical),
plus a `fixup!` (force-close row alignment) and CI version commits that the next rebase folds away --
procedure in `docs/rebasing-on-upstream.md`. Releases: `canary-525` (on device) and `canary-526`
(published 2026-10-08, NOT yet flashed; contains every fix below). Upstream's 8 workflows stay
DISABLED in Actions. CI pushes a version commit to `patch` on every release -- pull before working.

Device: runs `canary-525`. PixelXpert needs "System Framework" ticked in Vector (scope lacked
`system`); with it CallVibrator works and Recents force-stop works. The phone is SHARED with another
session -- ask before flash/reboot/app restarts.

Unverified fixes in `canary-526`: Recents tile dismiss on launcher 907 (`dismissTaskView`), Force
close row alignment (icon set as background of the `id/icon` View), VoLTE/VoWiFi icons under the
Compose status bar (main-looper Handler, init from `StatusBarIconControllerImpl`), StatusIconTuner
null guard.

| Phase | Status | Progress |
|-------|--------|----------|
| 1-3: Fork tracking, Build CI, Repo cleanup | Done | 3/3 |
| 4: Custom features (rolling) | In progress | -- |
| 5: Settings UI reorg | Done | -- |
| 6: Diagnostic logging | Done; verified on device (verbose traces seen) | -- |
| 7: A17 QPR3 compatibility | In progress (fixes in canary-526, unverified) | -- |
| 8: On-device verification backlog | In progress | -- |
| 9: Release pipeline | Done except on-device updater check | 4/5 |

Workstreams in flight:
- release-pipeline -- canary-525/526 released; KSU/in-app updater on-device check left -- `claude/workstreams/release-pipeline.md`
- qpr3-statusbar -- VoLTE/VoWiFi null-view fix in canary-526, verify on device -- `claude/workstreams/qpr3-statusbar.md`
- recents-force-close -- works with `system` scope; tile dismiss + row alignment fixes in canary-526, verify -- `claude/workstreams/recents-force-close.md`
- community-fork-sync -- upstream adopted; rebase procedure now in `docs/rebasing-on-upstream.md` -- `claude/workstreams/community-fork-sync.md`

## Next actions
1. With the user's go-ahead (shared phone): flash `PixelXpertFork-canary-526.zip` via
   `ksud module install`, reboot, `adb logcat -G 16M`, keep `verboseLogging` on.
2. Verify on device: Recents Force close row lines up with native rows and the tile disappears;
   VoLTE/VoWiFi icons show (else read the verbose `StatusbarMods: vo_data ...` line, see
   qpr3-statusbar Findings); KSU manager + Updates tab report 526 as current.
3. Remaining canary-525 NPE: `GestureNavbarManager` BackPanelController#onMotionEvent hook.
4. Next upstream sync or before the next release: rebase per `docs/rebasing-on-upstream.md`
   (folds the `fixup!` and version commits; needs a confirmed force-push).

## Decisions (durable)
- Branch model + upstream sync: rebase `patch` onto `canary` with the policy in
  `docs/rebasing-on-upstream.md` (code -> upstream + our hunks; fork metadata/CI/docs -> ours).
- Git hygiene: only fork commits ABOVE the upstream merge-base are ever rewritten; canary/upstream
  history is untouchable; force-push needs explicit confirmation.
- 2026-10-08: `patch` is kept as minimal logical commits (one per concern) so upstream rebases stay
  cheap; fixes land as `fixup!` commits and fold at the next rebase.
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
  force-pushed, deleted old releases, wrote the canary-<N> release workflow, cut `canary-525` and
  flashed it; found the missing Vector `system` scope (CallVibrator fixed), fixed Recents dismiss/
  alignment and VoLTE/VoWiFi for A17 QPR3, recomposed `patch` 41 -> 14 commits, cut `canary-526`,
  wrote `docs/rebasing-on-upstream.md`.

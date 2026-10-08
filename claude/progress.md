# PixelXpert fork -- Progress
> Last updated: 2026-10-08 | Session: 9

## Current state
Objective: carry a small set of custom features and Android-version fixes on top of upstream
`Codecity001/PixelXpert` (community fork with the A17 fixes; the original repo is archived), and
publish signed `canary-<N>` releases.

Pushed 2026-10-08: `patch` force-pushed to the rebased, privacy-scrubbed history (`4716d94f`), and
`canary` fast-forwarded to upstream (`60268ff9`). Upstream's 8 workflows are DISABLED in this repo's
Actions (only "Fork Build" is active). All old `fork-v*` tags and releases were deleted; the repo has
NO releases yet. Local `patch` is 4 commits ahead of `origin/patch` (README, updater repoint, new
release workflow, this tracker update) -- committed, NOT pushed.

Device: runs an old fork build (Obtainium install, versionName `canary-499`). On it, the SystemUI
pref-change crash (fixed by upstream in our new base), broken Recents force-close and CallVibrator,
and missing VoLTE/VoWiFi icons are confirmed. Nothing new is installed yet: the next step is the
first release, then on-device verification.

| Phase | Status | Progress |
|-------|--------|----------|
| 1-3: Fork tracking, Build CI, Repo cleanup | Done | 3/3 |
| 4: Custom features (rolling) | In progress (force-close blocked) | 0/10 open |
| 5: Settings UI reorg | Done | -- |
| 6: Diagnostic logging | Code done; verify in Phase 8 | -- |
| 7: A17 QPR3 compatibility | In progress | 1/7 |
| 8: On-device verification backlog | Pending | 0/9 |
| 9: Release pipeline | In progress (workflow written, not pushed) | 2/5 |

Workstreams in flight:
- release-pipeline -- canary-<N> manual release workflow written + linted, not pushed; first release pending -- `claude/workstreams/release-pipeline.md`
- qpr3-statusbar -- crash fixed via upstream; VoLTE icons unverified (Compose root suspected) -- `claude/workstreams/qpr3-statusbar.md`
- recents-force-close -- blocked: broken on device; AMS grant hook not firing; scope change is a lead -- `claude/workstreams/recents-force-close.md`
- community-fork-sync -- adopted as upstream and pushed; adversarial review recorded -- `claude/workstreams/community-fork-sync.md`

## Next actions
1. Push local `patch` (4 commits, plain push -- no force needed), then cut the first release:
   `gh workflow run forkBuild.yml -R HritwikSinghal/PixelXpert --ref patch`; watch with
   `gh run watch -R HritwikSinghal/PixelXpert`. Expect release `canary-525`.
2. Install `PixelXpertFork-canary-525.zip` on the device (flash in KSU manager; its installer
   pm-installs the APK over the Obtainium one -- same signing key, higher versionCode).
3. On device, with `verboseLogging` on: re-test Recents force-close, CallVibrator, VoLTE/VoWiFi
   icons; check that `system_server` mod packs load under the new `system` scope.
4. qpr3-statusbar: log/guard the VoLTE path; recents-force-close: boot-log decision tree.

## Decisions (durable)
- Branch model + intentional upstream divergence: resolve `patch`-onto-`canary` rebase conflicts
  toward the fork (deleted upstream CI, rewritten versioning/`PXTasks.gradle.kts`/`buildSrc`, fork
  metadata).
- Releases: `fork-v*` annotated tags trigger a CI Release with assets + an auto changelog (commits
  since the previous fork tag) + the tag message as preamble.
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
  force-pushed, deleted old releases, wrote the canary-<N> release workflow (not yet pushed).

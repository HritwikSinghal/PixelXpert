# PixelXpert fork -- Progress
> Last updated: 2026-10-08 | Session: 9

## Current state
Objective: carry a small set of custom features and Android-version fixes on top of the archived
upstream PixelXpert, and autobuild/publish them (`fork-v*` releases; latest `fork-v5.1.1-5`).

The test device moved to Android 17 QPR3 (Sept/Oct 2026). That broke the status bar: SystemUI
crashes on any PixelXpert pref change, and the VoLTE/VoWiFi icons are gone. Diagnosed on-device,
the SystemUI crash fix arrived with the new upstream; the VoLTE icons are still unverified
(Phase 7). The device runs a fork build (installed via
Obtainium; versionName `canary-499` is reused by the fork). Recents force-close and CallVibrator --
both system_server modpacks -- are confirmed broken on it.

Recents force-close stays blocked on the system_server permission-grant hook; its uncommitted
fixes in `PackageManager.java` / `RecentsForceClose.java` are still in the working tree.

| Phase | Status | Progress |
|-------|--------|----------|
| 1-3: Fork tracking, Build CI, Repo cleanup | Done | 3/3 |
| 4: Custom features (rolling) | In progress (force-close blocked) | 0/10 open |
| 5: Settings UI reorg | Done | -- |
| 6: Diagnostic logging | Code done; verify in Phase 8 | -- |
| 7: A17 QPR3 compatibility | In progress | 1/7 |
| 8: On-device verification backlog | Pending | 0/9 |

Workstreams in flight:
- qpr3-statusbar -- diagnosed: SBNIC NPE blocks VoLTE path; Compose root suspected -- `claude/workstreams/qpr3-statusbar.md`
- recents-force-close -- blocked: AMS grant hook not firing -- `claude/workstreams/recents-force-close.md`
- community-fork-sync -- Codecity001/PixelXpert adopted as upstream; our 24 commits rebased onto it locally, push pending -- `claude/workstreams/community-fork-sync.md`

## Next actions
1. Push the rebased `patch` + fast-forward `canary` (needs explicit OK; force-push), disable upstream's
   workflows in Actions, install the new build, re-test force-close / CallVibrator / VoLTE.
2. qpr3-statusbar: log/guard the VoLTE path.
3. On device: verify VoLTE/VoWiFi icons with verbose logging; check whether `onViewAttached` fires.
4. recents-force-close: capture the boot log and follow the decision tree in its workstream.

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

## Session log
- Sessions 1-8 (to 2026-06-21): fork setup, CI, repo cleanup, force-close, settings reorg, logging.
  Detail in `git log -p claude/progress.md` and the workstream files.
- Session 9 (2026-10-08): diagnosed the A17 QPR3 status bar breakage on-device (SBNIC NPE crash;
  VoLTE path unreachable; `status_bar_root_modernization` enabled). Restructured the tracker. Surveyed the Codecity001 community fork for cherry-picks.

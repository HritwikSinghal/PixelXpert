# Rebasing `patch` onto upstream

TL;DR -- sync `canary` from upstream, then rebase `patch` onto it with fixups folded in and the CI
"Version update" commits dropped. Resolve conflicts with the policy table below, re-stamp the version
files once (taking the higher versionCode), compile every commit, then force-push after the user
confirms. The result should be the same ~14 logical commits on the new upstream tip.

Written for an agent working in a fresh context. All commands run from the repo root. Remotes:
`origin` = the fork, `upstream` = `Codecity001/PixelXpert`. Always pass `-R HritwikSinghal/PixelXpert`
to `gh`, because it defaults to a non-origin remote.

## Rules that never bend

- `canary` is a clean mirror of `upstream/canary`. Never commit fork work there, and only ever
  fast-forward it.
- Only rewrite commits in `canary..patch`. Anything at or below `canary` belongs to upstream.
- A force-push of `patch` and a reset of local `patch` are destructive. State the exact range, wait
  for the user's explicit yes, and push with `--force-with-lease` pinned to the SHA you started from.
- Interactive editors are unavailable. Drive `rebase -i` only through `GIT_SEQUENCE_EDITOR`, as in
  step 3.
- The repo is public. Never write personal identifiers (names, emails, serials, home paths) into
  commits or `claude/`.

## Shape of `patch`

Each concern is one commit, in dependency order:

1. `chore: untrack IDE/build cruft`
2. `build: point versioning and signing at this fork` (`buildSrc/.../BuildUtils.kt` release URL,
   `app/build.gradle.kts` debug signing, `gradle.properties`, `gradle/libs.versions.toml`)
3. `build(nix): add flake ...` (`flake.nix`, `flake.lock`, `gradlew` mode)
4. `ci: fork build workflow ...` (`.github/workflows/forkBuild.yml`, `.github/dependabot.yml`)
5. `fork: point module metadata, README and updater at this fork` (`MagiskModBase/module.prop`,
   `MagiskModuleUpdate_{Full,Xposed}.json`, `latestCanary.json`, `version.properties`, `README.md`,
   `ui/fragments/UpdateFragment.java`)
6. `docs: fork maintenance notes and project tracker` (`CLAUDE.md`, `claude/`, `docs/`)
7. Feature and fix commits (settings entry, flashlight-tile removal, Recents force close, settings
   reorg, CallVibrator, logging, FORCE_STOP grant, status bar). The order matters: the settings reorg
   moves the force-close prefs, and the FORCE_STOP grant builds on the logging hunks in
   `PackageManager.java`.

Between rewrites, follow-up fixes land as `fixup! <subject>` commits (`git commit --fixup=<sha>`),
and every release adds a CI commit `Version update: canary-<N>`. The rebase below folds both away.

## Procedure

```sh
# 0. Clean start
git status --short                       # must be empty
git fetch upstream && git fetch origin
git switch patch && git merge --ff-only origin/patch   # CI pushes version commits to patch
OLD=$(git rev-parse refs/heads/patch)    # record this; it is the lease and the rollback point
MB=$(git merge-base "$OLD" canary)       # the old base, before the mirror moves

# 1. Sync the mirror (fast-forward only)
git switch canary && git merge --ff-only upstream/canary && git push origin canary
git switch patch

# 2. Preview the conflict surface: upstream changes to files our commits also touch
git diff --stat "$MB" canary -- $(git diff --name-only "$MB" "$OLD")

# 3. Rebase: fold fixups, drop CI version commits (git >= 2.45 writes "pick <sha> # <subject>")
GIT_SEQUENCE_EDITOR="sed -i -E '/^pick [0-9a-f]+ (# )?Version update: /s/^pick/drop/'" \
  git rebase -i --autosquash canary
#    On each conflict: resolve per the table below, `git add`, then `git rebase --continue`.

# 4. Re-stamp the version files once, as a fixup of the metadata commit (see the version rule below)
META=$(git log --format=%H --grep='^fork: point module metadata' canary..HEAD)
git checkout "$OLD" -- version.properties MagiskModBase/module.prop \
  MagiskModuleUpdate_Full.json MagiskModuleUpdate_Xposed.json latestCanary.json
#    Apply the version rule, then:
git commit --fixup="$META" && git rebase --autosquash canary
```

## Conflict policy

| Files | Take | Why |
|---|---|---|
| Java/Kotlin code, `res/` | upstream's fix, then re-apply our hunk on top (usually `log*` calls, a null guard, or our feature block) | Upstream carries the Android-version fixes; ours are small additions. |
| `values-*/strings.xml` (crowdin) | upstream, then drop the strings our commits delete (the flashlight-tile keys) | Translations churn upstream; our only change there is removals. |
| `buildSrc/.../BuildUtils.kt`, `app/build.gradle.kts` | upstream, then re-apply the hunks marked "Fork-specific" (release URL owner, debug signing) | Versioning deliberately follows upstream so these stay small. |
| `module.prop`, `MagiskModuleUpdate_*.json`, `latestCanary.json`, `README.md` | ours for URLs and names (`HritwikSinghal/PixelXpert`, `PixelXpertFork`), the version rule for version fields | These point the module, the updater and KSU at the fork. |
| `version.properties` | the version rule | -- |
| `.github/workflows/forkBuild.yml`, `flake.nix`, `CLAUDE.md`, `claude/`, `docs/` | ours | Fork-only files; upstream does not have them. |
| Upstream's own workflows (`crowdin*`, `make*Release`, `*TestBuild`) | upstream, unchanged | Kept in the tree but DISABLED in the fork's Actions settings. |
| `MagiskModBase/customize.sh`, `service.sh` (packaging) | upstream | We use upstream's user-app packaging unchanged. |

Version rule: `CANARY_VERSION_CODE` must stay above both the last fork release and upstream's code,
because `pm install -r -d` on the device silently refuses a lower versionCode. Take the higher of
ours (from `$OLD`) and upstream's (`git show canary:version.properties`), and make all five files agree
on that code and name (`canary-<N>`). The next release run bumps it by one.

## Verify before pushing

```sh
# a. Same feature content: the fork's diff vs the new base should change only where upstream did.
git range-diff "$MB".."$OLD" canary..HEAD      # every commit should map 1:1 (fixups/version commits vanish)

# b. Config files the build READS -- a silent bad merge once broke versioning with no conflict.
git diff "$OLD" HEAD -- version.properties buildSrc app/build.gradle.kts app/PXTasks.gradle.kts \
  gradle.properties MagiskModBase/module.prop

# c. Every commit compiles on its own (submodule must be initialised).
git submodule update --init
for c in $(git rev-list --reverse canary..HEAD); do
  git checkout -q "$c" && nix develop -c ./gradlew -q :app:compileDebugJavaWithJavac >/dev/null 2>&1 \
    && echo "ok   $(git log --oneline -1)" || echo "FAIL $(git log --oneline -1)"
done; git switch -q patch
```

Then, after the user explicitly confirms the force-push:

```sh
git push --force-with-lease=refs/heads/patch:"$OLD" origin patch
```

The push triggers a "Fork Build" run. Cut a release only when asked:
`gh workflow run forkBuild.yml -R HritwikSinghal/PixelXpert --ref patch`, then
`git pull --ff-only` for the version commit it pushes back.

## Traps

- A local hook blocks Bash commands containing the word "claude". Stage tracker files with
  `git add -u` or by an explicit path list, and keep the word out of commit messages.
- If a rebase goes wrong mid-way: `git rebase --abort` restores `patch` to `$OLD`. Nothing is lost
  until the force-push.
- If the history has drifted into many small commits that `--autosquash` cannot fold, recompose it:
  build the new branch in a worktree off `canary` by replaying `git diff c^ c -- <paths>` through
  `git apply -3 --index` per group, then check the tree hash equals the old tip. `git apply` can
  silently skip a binary deletion, so add an explicit `git rm --cached` for those.

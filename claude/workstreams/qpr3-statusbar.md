---
name: qpr3-statusbar
description: Android 17 QPR3 (Sept/Oct 2026) status bar breakage -- VoLTE/VoWiFi icons gone, SystemUI crash on pref change (NotificationIconLimit SBNIC NPE), Compose status bar root
status: active
---

# A17 QPR3 status bar: VoLTE/VoWiFi icons + SystemUI pref crash

## Current state
Crash fixed by the upstream switch (2026-10-08: upstream `5749e2f1` guards `SBNIC`
separately); VoLTE/VoWiFi still unverified. Original diagnosis: Two linked breakages since the A17 QPR3 update (first seen on the Sept 2026
build, still present on `CP3A.261005.005`, Oct 2026):

1. **SystemUI crashes on any PixelXpert pref change.** `StatusbarMods.onPreferenceUpdated` NPEs at
   `StatusbarMods.java:263` (`setObjectField(SBNIC, "maxIcons", ...)`): the guard at `:261` checks
   only `AODNIC != null`, and on QPR3 `SBNIC` stays null.
2. **VoLTE/VoWiFi icons never appear.** The `//region vo_data` handling (`:410-430`) runs after the
   crash point in the same method, so toggling `VolteIconEnabled`/`VowifiIconEnabled` never reaches
   `initVoData()`. Whether the icons work once (1) is fixed is UNVERIFIED -- see the Compose-root
   suspect below.

Test device runs a fork build (Obtainium install; fork reuses versionName `canary-499`).

## Next actions
1. Fix the crash: guard `AODNIC` and `SBNIC` independently at `StatusbarMods.java:261-263`, and wrap
   the block so one dead QPR3 hook cannot abort the rest of `onPreferenceUpdated`. The community
   fork already has this exact fix as `5749e2f1` -- cherry-pick it (see community-fork-sync).
2. Stop swallowing errors in the VoLTE path: `updateVoData` (`:988`) posts `setIcon` inside
   `catch (Exception ignored)`, and `mPhoneStatusbarView` may be null -- log via `logWarn` and guard.
3. Build the fork and install it over the existing fork build (same signing key).
4. With verbose logging on, check whether `PhoneStatusBarViewController.onViewAttached` (`:649`)
   fires under the Compose root. If not, move VoLTE startup onto the `StatusBarIconControllerImpl`
   construction hook (`:494`), which does not need the view.
5. Find what replaced `NotificationIconContainerStatusBarViewModel` construction on QPR3 so the
   status-bar icon limit works again (AOD limit still hooks fine).

## Decisions
<!-- APPEND. YYYY-MM-DD: what was decided, why, what was rejected. -->

## Findings
- 2026-10-08 (canary-525, verbose on): confirmed `mPhoneStatusbarView` is null under the Compose root --
  toggling VolteIconEnabled NPEs at `updateVoData` (`View.post` on null); the boot path never runs
  (init lived in a PhoneStatusBarView hook). Phone reports VoLTE + VoWiFi active (ImsPhoneCallTracker).
  Fix committed: main-looper Handler instead of the view, init from `StatusBarIconControllerImpl`
  construction, `setIcon`/`removeAllIconsForSlot` failures logged, verbose `vo_data` trace line.
  UNVERIFIED: whether the Compose bar renders custom slots set via StatusBarIconController -- if the
  trace shows `controller=true` + no setIcon error but no icon, the slot is not rendered and the
  icon must be injected differently.

### The crash (verified from logcat on-device, 2026-10-08)
`logcat -b crash` -- FATAL in `com.android.systemui`:
```
java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on a null object reference
  at ...XposedHelpers.setObjectField
  at sh.siava.pixelxpert.xposed.modpacks.systemui.StatusbarMods.onPreferenceUpdated
  at sh.siava.pixelxpert.xposed.XPrefs.lambda$loadEverything$2
  ...
  at sh.siava.pixelxpert.xposed.utils.ExtendedRemotePreferences ... onSharedPreferenceChanged
  at android.database.ContentObserver.onChange
```
Triggered by a pref change (ContentObserver path), not by boot. The LSPosed verbose log
(`/data/adb/lspd/log/verbose_*.log`) shows the same trace under `E/Vector Crash unexpectedly`.
Mapped to the fork source: the only `setObjectField` calls in `onPreferenceUpdated` are `:262-263`.

### Bytecode proof (adversarial check, 2026-10-08: CONFIRMED)
`dexdump -d` of the installed APK (source_file `r8-map-id-15c4f641...` = the crash frame's map id):
`onPreferenceUpdated` holds exactly two `setObjectField` calls. `0x0092 if-eqz AODNIC -> 0x00aa`
skips both when AODNIC is null; the second call at `0x00a7` (position table `line=168`, the crash
frame) loads `SBNIC` with no null check. So AODNIC was non-null and SBNIC null. No helper reachable
from the method has an uncaught `setObjectField`. Fixed on the rebased branch by upstream
`5749e2f1` (`StatusbarMods.java:277-281`).

### Why SBNIC is null
Both hooked classes still exist in QPR3 SystemUI (`dexdump` of the device
`/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk`):
- `com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerAlwaysOnDisplayViewModel`
- `com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel`
- (new sibling: `NotificationIconContainerShelfViewModel`)

So the class is not renamed; its constructor evidently never runs (or runs later than the AOD one),
leaving `SBNIC` null while `AODNIC` is set (hooks at `StatusbarMods.java:500-513`).

### Compose status bar root (likely QPR3 change)
`aflags list` on device:
```
com.android.systemui.status_bar_root_modernization          enabled  - default read-only system
com.android.systemui.status_bar_event_forwarding_modernization enabled - default read-only system
```
`dumpsys activity service com.android.systemui/.SystemUIService` shows 12 `ComposeView` instances.
Not verified that the flag was OFF on the pre-QPR3 build (device already updated). Suspect: the
legacy status-bar view model and/or `PhoneStatusBarViewController.onViewAttached` are no longer
driven, which would kill both the icon limit and VoLTE's startup init (`:663`).

### Ruled out (verified by dexdump of QPR3 SystemUI + framework.jar)
- `StatusBarIconControllerImpl` still has `setIcon(String, StatusBarIconHolder)`,
  `removeAllIconsForSlot(String, boolean)`, `setIconVisibility(String, boolean)`.
- `StatusBarIconHolder` public fields: `icon` (StatusBarIcon), `tag`, `type` -- the holder builder's
  "first field containing 'icon'" lookup (`:926-937`) still resolves to `icon`.
- `TelephonyManager.isVolteAvailable` / `isWifiCallingAvailable` still exist.
- `StatusBarIcon` gained `type` (`StatusBarIcon$Type`), `shape` (`StatusBarIcon$Shape`) and
  `preloadedIcon` fields, which PixelXpert's Objenesis-built icon (`:906-923`) leaves null. Not the
  cause: SystemUI only compares `shape` to `Shape.FIXED_SPACE` (`TintedIconManager.onCreateLayoutParams`
  is `if-ne` against the constant, null -> wrap-content), and `type` is read only in
  `setResourceIconInternal` / notification `IconManager`, not on the `setIcon(String, holder)` path.
  Still worth using the real constructor
  `(UserHandle, String, Icon, int, int, CharSequence, Type, Shape)` for robustness.

### IMS state at diagnosis time
`ImsPhoneCallTracker` logs showed `isVolteEnabled=true` / `isVowifiEnabled=true` at different times,
so the telephony side reports capability; the icons are lost on the SystemUI side.

### How to re-run the evidence
```sh
adb shell "su -c 'logcat -d -b crash'" | grep -A20 StatusbarMods
adb shell aflags list | grep status_bar_root_modernization
P=$(adb shell pm path com.android.systemui | sed 's/package://' | tr -d '\r'); adb pull "$P" SystemUI.apk
unzip -oq SystemUI.apk 'classes*.dex' -d dex; for d in dex/*.dex; do dexdump -d "$d"; done > dump.txt
```

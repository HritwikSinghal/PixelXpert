package sh.siava.pixelxpert.xposed.modpacks.android;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static sh.siava.pixelxpert.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;

import java.util.Timer;
import java.util.TimerTask;

import io.github.libxposed.api.XposedModuleInterface;
import sh.siava.pixelxpert.BuildConfig;
import sh.siava.pixelxpert.Constants;
import sh.siava.pixelxpert.xposed.XposedModPack;
import sh.siava.pixelxpert.xposed.annotations.FrameworkModPack;
import sh.siava.pixelxpert.xposed.utils.reflection.ReflectedClass;

/**
 * @noinspection RedundantThrows, ConstantValue
 */
@FrameworkModPack
public class PackageManager extends XposedModPack {
	private static final int AUTO_DISABLE_MINUTES = 5;
	private static final String ALLOW_SIGNATURE_PREF = "PM_AllowMismatchedSignature";
	private static final String ALLOW_DOWNGRADE_PREF = "PM_AllowDowngrade";

	public static final int PERMISSION = 4;
	private static final int PERMISSION_GRANTED = 0;

	// UserHandle.PER_USER_RANGE: the uid block size per Android user. app-id = uid % PER_USER_RANGE
	// (this is exactly what the hidden UserHandle.getAppId(int) computes; inlined since that method is
	// not in the public SDK and won't compile against compileSdk).
	private static final int PER_USER_RANGE = 100000;

	private static boolean PM_AllowMismatchedSignature = false;
	private static boolean PM_AllowDowngrade = false;

	// Cached launcher app-id (uid without the user portion); -1 until first resolved. Used to grant
	// the launcher FORCE_STOP_PACKAGES by comparing the calling app-id.
	private int launcherAppId = -1;

	public PackageManager(Context context) {
		super(context);
	}

	/**
	 * Resolves and caches the launcher's app-id. Resolved lazily (at permission-check time, long after
	 * boot) so the PackageManager is ready. Returns -1 if it cannot be resolved, in which case the
	 * FORCE_STOP_PACKAGES grant is skipped rather than granted to an unknown caller.
	 */
	private int getLauncherAppId() {
		if (launcherAppId == -1) {
			try {
				int uid = mContext.getPackageManager().getPackageUid(Constants.LAUNCHER_PACKAGE, 0);
				launcherAppId = uid % PER_USER_RANGE;
			}
			catch (Throwable ignored) {
			}
		}
		return launcherAppId;
	}

	@Override
	public void onPreferenceUpdated(String... Key) {
		PM_AllowMismatchedSignature = Xprefs.getBoolean(ALLOW_SIGNATURE_PREF, false);
		PM_AllowDowngrade = Xprefs.getBoolean(ALLOW_DOWNGRADE_PREF, false);

		if (PM_AllowDowngrade || PM_AllowMismatchedSignature) {
			if (Key.length == 0) {
				disablePMMods();
			} else if (Key[0].equals(ALLOW_SIGNATURE_PREF) || Key[0].equals(ALLOW_DOWNGRADE_PREF)) {
				new Timer().schedule(new TimerTask() {
										 @Override
										 public void run() {
											 disablePMMods();
										 }
									 },
						AUTO_DISABLE_MINUTES * 60000);
			}
		}
	}

	private void disablePMMods() {
		Xprefs.edit()
				.putBoolean(ALLOW_SIGNATURE_PREF, false)
				.putBoolean(ALLOW_DOWNGRADE_PREF, false)
				.apply();
	}

	@Override
	public void onPackageLoaded(XposedModuleInterface.PackageReadyParam PRParam) throws Throwable {
		try {
			ReflectedClass InstallPackageHelperClass = ReflectedClass.of("com.android.server.pm.InstallPackageHelper");
			ReflectedClass PackageManagerServiceUtilsClass = ReflectedClass.of("com.android.server.pm.PackageManagerServiceUtils");
			ReflectedClass SigningDetailsClass = ReflectedClass.of("android.content.pm.SigningDetails");

			try {
				ReflectedClass ActivityManagerServiceClass = ReflectedClass.of("com.android.server.am.ActivityManagerService");

				ActivityManagerServiceClass
						.before("checkBroadcastFromSystem")
						.run(param -> {
							String action = ((Intent) param.args[0]).getAction();

							//noinspection DataFlowIssue
							if (action.startsWith(BuildConfig.APPLICATION_ID + ".ACTION")) {
								param.setResult(null);
							}
						});

				// Grant the Pixel launcher FORCE_STOP_PACKAGES (used by recents force-close + the
				// nav-gesture kill). Match by calling app-id rather than the old
				// mInternal.getPackageNameByPid() lookup: that ActivityManagerInternal call no longer
				// resolves the launcher on A17, so the grant silently dropped and both features failed
				// with a SecurityException. The calling-UID app-id is stable and version-independent.
				ActivityManagerServiceClass
						.before("checkCallingPermission")
						.run(param -> {
							try {
								if (!"android.permission.FORCE_STOP_PACKAGES".equals(param.args[0])) return;
								int appId = getLauncherAppId();
								if (appId != -1 && (Binder.getCallingUid() % PER_USER_RANGE) == appId) {
									param.setResult(PERMISSION_GRANTED);
								}
							} catch (Throwable ignored) {
							}
						});

			} catch (Throwable t) {
				// These two hooks grant the launcher FORCE_STOP_PACKAGES (used by the recents force-close
				// and nav-gesture kill) and suppress our broadcast permission check. If ActivityManagerService
				// can't be resolved, both die silently -- log so a framework change is diagnosable.
				logWarn("PackageManager: ActivityManagerService hooks not installed", t);
			}

			PackageManagerServiceUtilsClass
					.before("checkDowngrade")
					.run(param -> {
						if (PM_AllowDowngrade) {
							param.setResult(null);
						}
					});

			SigningDetailsClass
					.before("checkCapability")
					.run(param -> {
						if (PM_AllowMismatchedSignature && !param.args[1].equals(PERMISSION)) {
							param.setResult(true);
						}
					});

			PackageManagerServiceUtilsClass
					.before("verifySignatures")
					.run(param -> {
						try {
							if (PM_AllowMismatchedSignature &&
									callMethod(
											callMethod(param.args[0], "getSigningDetails"),
											"getSignatures"
									) != null) {
								param.setResult(true);
							}
						} catch (Throwable ignored) {
						}
					});

			InstallPackageHelperClass
					.before("doesSignatureMatchForPermissions")
					.run(param -> {
						try {
							if (PM_AllowMismatchedSignature
									&& callMethod(param.args[1], "getPackageName").equals(param.args[0])
									&& ((String) callMethod(param.args[1], "getBaseApkPath")).startsWith("/data")) {
								param.setResult(true);
							}
						} catch (Throwable ignored) {
						}
					});
		} catch (Throwable t) {
			// Wraps resolution of InstallPackageHelper / PackageManagerServiceUtils / SigningDetails and
			// all the downgrade + signature-bypass hooks. A single renamed framework class kills every
			// PackageManager feature at once -- log instead of swallowing.
			logWarn("PackageManager: feature hooks not installed (framework class resolution failed)", t);
		}
	}
}

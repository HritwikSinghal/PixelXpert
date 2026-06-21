package sh.siava.pixelxpert.xposed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import sh.siava.pixelxpert.BuildConfig;
import sh.siava.pixelxpert.Constants;
import sh.siava.pixelxpert.xposed.utils.ExtendedRemotePreferences;
import sh.siava.pixelxpert.xposed.utils.toolkit.Logger;


public class XPrefs {
	@SuppressLint("StaticFieldLeak")
	public static ExtendedRemotePreferences Xprefs;
	public static final String MagiskRoot = "/data/adb/modules/PixelXpert";
	public static String packageName;

	private static final OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> loadEverything(packageName, key);
	public static void init(Context context) {
		packageName = context.getPackageName();

		Xprefs = new ExtendedRemotePreferences(context, BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + "_preferences", true);
	}

	public static void onContentProviderLoaded() {
		loadEverything(packageName);
		Xprefs.registerOnSharedPreferenceChangeListener(listener);
	}

	public static void loadEverything(String packageName, String... key) {
		if (key.length > 0 && (key[0] == null || Constants.PREF_UPDATE_EXCLUSIONS.stream().anyMatch(exclusion -> key[0].startsWith(exclusion))))
			return;

		// Sync the verbose-logging gate here: this runs once at initial load and again on every pref
		// change, in every hooked process, with the prefs handle in hand -- the single, clean point to
		// propagate the toggle to Logger (no dedicated modpack needed).
		if (Xprefs != null) {
			Logger.setVerbose(Xprefs.getBoolean("verboseLogging", false));
		}
		Logger.logVerbose("pref update: " + (key.length > 0 ? key[0] : "<all>"));

		setPackagePrefs(packageName);

		XPLauncher.runningMods.forEach(thisMod -> thisMod.onPreferenceUpdated(key));
	}

	/** @noinspection unused*/
	public static void setPackagePrefs(String packageName) {
	}
}
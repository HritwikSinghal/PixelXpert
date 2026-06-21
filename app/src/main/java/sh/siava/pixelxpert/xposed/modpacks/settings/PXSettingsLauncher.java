package sh.siava.pixelxpert.xposed.modpacks.settings;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static sh.siava.pixelxpert.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.res.ResourcesCompat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

import io.github.libxposed.api.XposedModuleInterface;
import sh.siava.pixelxpert.BuildConfig;
import sh.siava.pixelxpert.R;
import sh.siava.pixelxpert.xposed.XPLauncher;
import sh.siava.pixelxpert.xposed.XposedModPack;
import sh.siava.pixelxpert.xposed.annotations.SettingsModPack;
import sh.siava.pixelxpert.xposed.utils.reflection.ReflectedClass;

@SuppressWarnings("RedundantThrows")
@SettingsModPack
public class PXSettingsLauncher extends XposedModPack {
	private static boolean PXInSettings = true;

	private boolean mNewSettings = true;

	private boolean mExpressiveTheme = false;

	/** Key we tag our injected entry with, so we can find it / avoid duplicate inserts. */
	private static final String PX_TOP_LEVEL_KEY = "pixelxpert_top_level";

	/**
	 * Key substrings (case-insensitive) used to locate the top "services" tile we
	 * want to sit next to. Covers stock "Google services" as well as a microG
	 * install that replaces it (microG ships its GmsCore under the GMS package but
	 * may surface its homepage tile under a microg/gms key instead of "google").
	 */
	private static final String[] SERVICES_TILE_KEY_NEEDLES = {"google", "microg", "gms"};

	private Class<?> mPreferenceGroupClass;

	public PXSettingsLauncher(Context context) {
		super(context);
	}

	@Override
	public void onPreferenceUpdated(String... Key) {
		PXInSettings = Xprefs.getBoolean("PXInSettings", true);
	}

	@Override
	public void onPackageLoaded(XposedModuleInterface.PackageReadyParam PRParam) throws Throwable {
		ReflectedClass HomepagePreferenceClass = ReflectedClass.of("com.android.settings.widget.HomepagePreference");
		ReflectedClass TopLevelSettingsClass = ReflectedClass.of("com.android.settings.homepage.TopLevelSettings");
		ReflectedClass OnPreferenceClickListenerInterface = ReflectedClass.of("androidx.preference.Preference$OnPreferenceClickListener");

		ReflectedClass PreferenceCategoryClass = ReflectedClass.ofIfPossible("androidx.preference.PreferenceCategory");
		ReflectedClass PreferenceManagerClass = ReflectedClass.ofIfPossible("androidx.preference.PreferenceManager");
		ReflectedClass PreferenceGroupClass = ReflectedClass.ofIfPossible("androidx.preference.PreferenceGroup");
		mPreferenceGroupClass = PreferenceGroupClass != null ? PreferenceGroupClass.getClazz() : null;

		try { //A16 expressive theme needs a different icon of PX
			ReflectedClass SettingsThemeHelperClass = ReflectedClass.of("com.android.settingslib.widget.SettingsThemeHelper");
			mExpressiveTheme = (boolean) SettingsThemeHelperClass.callStaticMethod("isExpressiveTheme", mContext);
		}
		catch (Throwable ignored){}

		TopLevelSettingsClass
				.after("getPreferenceScreenResId")
				.run(param -> {
					@SuppressLint("DiscouragedApi")
					int oldResName = mContext.getResources().getIdentifier("top_level_settings", "xml", mContext.getPackageName());

					if (param.getResult().equals(oldResName)) {
						mNewSettings = false;
					}
				});

		TopLevelSettingsClass
				.before("onCreateAdapter")
				.run(param -> {
					if (PXInSettings) {
						Object preferenceScreen = param.args[0];

						// onCreateAdapter can fire more than once (config changes, adapter
						// rebuilds); bail if we already injected our entry into this screen.
						if (findPreference(preferenceScreen, PX_TOP_LEVEL_KEY) != null) {
							return;
						}

						Object PXPreference = HomepagePreferenceClass.getClazz().getConstructor(Context.class).newInstance(mContext);

						callMethod(PXPreference, "setKey", PX_TOP_LEVEL_KEY);
						callMethod(PXPreference, "setIcon",
								ResourcesCompat.getDrawable(XPLauncher.moduleResources,
										mExpressiveTheme
												? R.mipmap.ic_launcher
												: R.drawable.ic_notification_foreground,
										mContext.getTheme()));
						callMethod(PXPreference, "setTitle", XPLauncher.moduleResources.getString(R.string.app_name));

						Object onClickListener = Proxy.newProxyInstance(
								OnPreferenceClickListenerInterface.getClazz().getClassLoader(),
								new Class[]{OnPreferenceClickListenerInterface.getClazz()},
								new PXClickListener());

						setObjectField(PXPreference, "mOnClickListener", onClickListener);

						if (mNewSettings) {
							callMethod(PXPreference, "setSummary", XPLauncher.moduleResources.getString(R.string.xposed_desc));

							// Preferred placement: drop our entry into the same block as the
							// top services tile (Google services, or microG if it replaces it).
							// Cards are formed by adjacency within a PreferenceGroup, so reusing
							// that tile's parent + matching its order keeps us inside that card.
							Object servicesTile = findPreference(preferenceScreen, SERVICES_TILE_KEY_NEEDLES);
							Object servicesParent = servicesTile != null ? callMethod(servicesTile, "getParent") : null;

							if (servicesParent != null) {
								callMethod(PXPreference, "setOrder", callMethod(servicesTile, "getOrder"));
								callMethod(servicesParent, "addPreference", PXPreference);
							} else {
								// Fallback: services tile not found (not loaded yet, or an
								// unrecognized key) -- pin our own block to the very top.
								Object PXPreferenceCategory = PreferenceCategoryClass.getClazz().getConstructor(Context.class).newInstance(mContext);

								setObjectField(PXPreferenceCategory,
										"mPreferenceManager",
										PreferenceManagerClass.getClazz().getConstructor(Context.class).newInstance(mContext));
								callMethod(PXPreferenceCategory, "setOrder", -1000);

								@SuppressLint("DiscouragedApi")
								int layoutID = mContext.getResources().getIdentifier(
										"settingslib_preference_category_no_title",
										"layout",
										mContext.getPackageName());

								if (layoutID != 0)
									callMethod(PXPreferenceCategory, "setLayoutResource", layoutID);

								callMethod(PXPreferenceCategory, "addPreference", PXPreference);

								callMethod(preferenceScreen, "addPreference", PXPreferenceCategory);
							}
						} else {
							callMethod(PXPreference, "setOrder", -1000);
							callMethod(preferenceScreen, "addPreference", PXPreference);
						}
					}
				});
	}

	/**
	 * Depth-first search of a preference hierarchy for the first preference whose
	 * key contains any of {@code keyNeedles} (case-insensitive). Returns {@code null}
	 * if none match or the search root is not a {@link androidx.preference.PreferenceGroup}.
	 */
	private Object findPreference(Object group, String... keyNeedles) {
		if (group == null || mPreferenceGroupClass == null || !mPreferenceGroupClass.isInstance(group)) {
			return null;
		}

		int count = (int) callMethod(group, "getPreferenceCount");
		for (int i = 0; i < count; i++) {
			Object pref = callMethod(group, "getPreference", i);
			Object key = callMethod(pref, "getKey");

			if (key != null) {
				String lowerKey = key.toString().toLowerCase(Locale.ROOT);
				for (String needle : keyNeedles) {
					if (lowerKey.contains(needle)) {
						return pref;
					}
				}
			}

			if (mPreferenceGroupClass.isInstance(pref)) {
				Object nested = findPreference(pref, keyNeedles);
				if (nested != null) {
					return nested;
				}
			}
		}

		return null;
	}

	class PXClickListener implements InvocationHandler {
		/**
		 * @noinspection SuspiciousInvocationHandlerImplementation
		 */
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
			mContext.startActivity(intent);

			return true;
		}
	}
}
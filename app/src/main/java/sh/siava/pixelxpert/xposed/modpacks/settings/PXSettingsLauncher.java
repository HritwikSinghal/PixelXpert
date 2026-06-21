package sh.siava.pixelxpert.xposed.modpacks.settings;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static sh.siava.pixelxpert.xposed.XPrefs.Xprefs;
import static sh.siava.pixelxpert.xposed.utils.toolkit.Logger.log;

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
	 * Mirror of androidx.preference.Preference.DEFAULT_ORDER (== Integer.MAX_VALUE).
	 * Inlined rather than reflected: it is a stable public API constant and we must
	 * compare against it on every insert.
	 */
	private static final int PREFERENCE_DEFAULT_ORDER = Integer.MAX_VALUE;

	/**
	 * Exact top-level keys (case-insensitive) for the stock "Google services" tile,
	 * tried first so we latch onto the right tile deterministically rather than the
	 * first key that merely contains a substring. "top_level_google" is the documented
	 * homepage key on Pixel Settings; the others cover microG variants that replace it.
	 */
	private static final String[] SERVICES_TILE_KEY_EXACT = {
			"top_level_google", "top_level_microg", "top_level_gms"};

	/**
	 * Fallback boundary-aware tokens, used only when no exact key matched. These are
	 * matched against '_'-delimited key segments (not raw substrings) so a long key
	 * like "google_..." matches but an unrelated key that merely embeds the token does
	 * not. The bare 3-letter "gms" substring was dropped on purpose -- it was prone to
	 * latching onto unrelated tiles (first DFS hit wins) and inheriting their card/order.
	 */
	private static final String[] SERVICES_TILE_KEY_TOKENS = {"google", "microg"};

	private Class<?> mPreferenceGroupClass;

	/**
	 * Stateful injected-flag, scoped per resolved preference-screen instance. The
	 * key-based dedup below is the primary guard, but if the PreferenceGroup class
	 * fails to resolve (minified/renamed Settings -- see findPreference) the key search
	 * cannot run; this fallback still prevents a second insert into the same screen on
	 * a repeated onCreateAdapter (config change / dual-pane re-layout).
	 */
	private Object mInjectedScreen;

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
						// rebuilds, tablet/foldable dual-pane re-layout). Since
						// PreferenceGroup.addPreference does NOT dedupe by key (it only logs a
						// warning), a naive re-insert produces a second "Pixel Xpert" tile.
						// Primary guard: detach any pre-existing entry with our key wherever it
						// sits in the hierarchy, so the insert below is always the only copy.
						boolean removedExisting = removePreferenceByKey(preferenceScreen, PX_TOP_LEVEL_KEY);

						// Fallback guard for when the PreferenceGroup class failed to resolve
						// (minified/renamed Settings): the key search above is a no-op then, so
						// rely on a per-screen-instance flag to avoid a duplicate insert.
						if (!removedExisting && mPreferenceGroupClass == null && mInjectedScreen == preferenceScreen) {
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
							// Wrapped in try/catch: every step here is unguarded reflection
							// against the Settings PreferenceGroup; on a minified/renamed build a
							// NoSuchMethodError/NPE would otherwise escape the before-hook and
							// disrupt Settings. On failure we fall through to the top-of-list
							// category fallback below instead.
							Object servicesTile = null;
							Object servicesParent = null;
							try {
								servicesTile = findServicesTile(preferenceScreen);
								servicesParent = servicesTile != null ? callMethod(servicesTile, "getParent") : null;
							} catch (Throwable ignored) {
							}

							boolean placedNextToServices = false;
							if (servicesParent != null) {
								try {
									// Many top-level tiles use Preference.DEFAULT_ORDER
									// (Integer.MAX_VALUE), for which PreferenceGroup assigns a
									// sequential order at add time. Copying MAX_VALUE verbatim
									// would not place us "next to" the tile -- it would just
									// stamp us with MAX_VALUE too, yielding undefined relative
									// order. So only copy a real, explicitly-set order; when the
									// tile uses DEFAULT_ORDER, leave PX at DEFAULT_ORDER as well
									// so it is appended sequentially right after siblings.
									int servicesOrder = (int) callMethod(servicesTile, "getOrder");
									if (servicesOrder != PREFERENCE_DEFAULT_ORDER) {
										callMethod(PXPreference, "setOrder", servicesOrder + 1);
									}
									callMethod(servicesParent, "addPreference", PXPreference);
									placedNextToServices = true;
								} catch (Throwable ignored) {
								}
							}

							if (!placedNextToServices) {
								// Fallback: services tile not found (not loaded yet, an
								// unrecognized key) or the preferred path threw -- pin our own
								// block to the very top. Guard the ofIfPossible-resolved classes:
								// getClazz() can be null on a build where these are renamed.
								Class<?> categoryClazz = PreferenceCategoryClass != null ? PreferenceCategoryClass.getClazz() : null;
								Class<?> managerClazz = PreferenceManagerClass != null ? PreferenceManagerClass.getClazz() : null;

								if (categoryClazz != null && managerClazz != null) {
									Object PXPreferenceCategory = categoryClazz.getConstructor(Context.class).newInstance(mContext);

									setObjectField(PXPreferenceCategory,
											"mPreferenceManager",
											managerClazz.getConstructor(Context.class).newInstance(mContext));
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
								} else {
									// Last-resort: category classes unavailable -- add directly
									// to the screen with a high-priority order so we still appear.
									callMethod(PXPreference, "setOrder", -1000);
									callMethod(preferenceScreen, "addPreference", PXPreference);
								}
							}
						} else {
							callMethod(PXPreference, "setOrder", -1000);
							callMethod(preferenceScreen, "addPreference", PXPreference);
						}

						// Record the screen we injected into, so the stateful fallback guard
						// (used when the PreferenceGroup class did not resolve) can recognize a
						// repeated onCreateAdapter on the same screen instance.
						mInjectedScreen = preferenceScreen;
					}
				});
	}

	/**
	 * Locate the top "services" tile to sit next to. Tries the documented exact keys
	 * first (deterministic -- avoids latching onto an unrelated tile whose key merely
	 * embeds a token), then falls back to boundary-aware token matching. The previous
	 * raw-substring approach (especially the bare 3-letter "gms") could match the first
	 * unrelated DFS hit and inherit its card/order; exact-first removes that ambiguity.
	 */
	private Object findServicesTile(Object preferenceScreen) {
		Object exact = findPreferenceByExactKey(preferenceScreen, SERVICES_TILE_KEY_EXACT);
		if (exact != null) {
			return exact;
		}
		return findPreferenceByKeyToken(preferenceScreen, SERVICES_TILE_KEY_TOKENS);
	}

	/**
	 * DFS for the first preference whose key equals (case-insensitive) any of
	 * {@code exactKeys}.
	 */
	private Object findPreferenceByExactKey(Object group, String... exactKeys) {
		return findPreferenceMatching(group, lowerKey -> {
			for (String candidate : exactKeys) {
				if (lowerKey.equals(candidate)) {
					return true;
				}
			}
			return false;
		});
	}

	/**
	 * DFS for the first preference whose key, split on '_', contains any of
	 * {@code tokens} as a whole segment. Boundary-aware (segment equality, not raw
	 * substring) so "google_settings" matches "google" but "my_googleads_x" would not
	 * match on the bare token incorrectly via substring.
	 */
	private Object findPreferenceByKeyToken(Object group, String... tokens) {
		return findPreferenceMatching(group, lowerKey -> {
			for (String segment : lowerKey.split("_")) {
				for (String token : tokens) {
					if (segment.equals(token)) {
						return true;
					}
				}
			}
			return false;
		});
	}

	/**
	 * DFS for the first preference whose key equals {@code key} (exact, case-sensitive).
	 */
	private Object findPreferenceByKey(Object group, String key) {
		return findPreferenceMatching(group, lowerKey -> key.equals(lowerKey)
				|| key.toLowerCase(Locale.ROOT).equals(lowerKey));
	}

	/**
	 * Depth-first search of a preference hierarchy for the first preference whose
	 * (lower-cased) key satisfies {@code matcher}. Returns {@code null} if none match,
	 * the search root is not a {@link androidx.preference.PreferenceGroup}, or the
	 * group class failed to resolve.
	 * <p>
	 * All reflection here is defensively wrapped: {@code getPreferenceCount} is unboxed
	 * via {@link #safeInt} (null -> 0) and a thrown NoSuchMethodError/NPE on a
	 * minified/renamed Settings build is swallowed, returning {@code null} so callers
	 * fall through to their fallback path rather than letting it escape the before-hook
	 * and crash Settings.
	 */
	private Object findPreferenceMatching(Object group, KeyMatcher matcher) {
		if (group == null || mPreferenceGroupClass == null || !mPreferenceGroupClass.isInstance(group)) {
			return null;
		}

		try {
			int count = safeInt(callMethod(group, "getPreferenceCount"));
			for (int i = 0; i < count; i++) {
				Object pref = callMethod(group, "getPreference", i);
				if (pref == null) {
					continue;
				}

				Object key = callMethod(pref, "getKey");
				if (key != null && matcher.matches(key.toString().toLowerCase(Locale.ROOT))) {
					return pref;
				}

				if (mPreferenceGroupClass.isInstance(pref)) {
					Object nested = findPreferenceMatching(pref, matcher);
					if (nested != null) {
						return nested;
					}
				}
			}
		} catch (Throwable t) {
			log("PXSettingsLauncher: findPreference reflection failed: " + t);
			return null;
		}

		return null;
	}

	/**
	 * Detach every preference with {@code key} from its parent group anywhere in the
	 * hierarchy. addPreference does not dedupe by key, so removing the prior copy first
	 * is what actually prevents duplicate tiles across repeated onCreateAdapter calls.
	 * Returns {@code true} if at least one was removed. Best-effort: any reflection
	 * failure is swallowed (returns whatever was removed so far) so it never escapes
	 * the hook.
	 */
	private boolean removePreferenceByKey(Object preferenceScreen, String key) {
		boolean removedAny = false;
		// Loop: removing shifts indices and there could (defensively) be more than one
		// stale copy from earlier buggy inserts; keep going until none remain.
		while (true) {
			Object existing = findPreferenceByKey(preferenceScreen, key);
			if (existing == null) {
				break;
			}
			try {
				Object parent = callMethod(existing, "getParent");
				if (parent == null) {
					break;
				}
				callMethod(parent, "removePreference", existing);
				removedAny = true;
			} catch (Throwable t) {
				log("PXSettingsLauncher: removePreferenceByKey failed: " + t);
				break;
			}
		}
		return removedAny;
	}

	/** Defensive unbox: a null Integer (renamed/absent getPreferenceCount) -> 0. */
	private static int safeInt(Object value) {
		return value instanceof Integer ? (Integer) value : 0;
	}

	private interface KeyMatcher {
		boolean matches(String lowerKey);
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
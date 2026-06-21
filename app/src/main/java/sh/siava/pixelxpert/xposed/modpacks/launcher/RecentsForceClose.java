package sh.siava.pixelxpert.xposed.modpacks.launcher;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findMethodBestMatch;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static sh.siava.pixelxpert.xposed.XPrefs.Xprefs;
import static sh.siava.pixelxpert.xposed.utils.toolkit.Logger.log;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import java.lang.reflect.Field;

import io.github.libxposed.api.XposedModuleInterface;
import sh.siava.pixelxpert.R;
import sh.siava.pixelxpert.xposed.XPLauncher;
import sh.siava.pixelxpert.xposed.XposedModPack;
import sh.siava.pixelxpert.xposed.annotations.LauncherModPack;
import sh.siava.pixelxpert.xposed.utils.reflection.ReflectedClass;

/**
 * Adds an opt-in "Force close" row to the Android Recents (Overview) task menu.
 *
 * <p>This mod is intentionally defensive: every quickstep class/field/method lookup is
 * null-guarded and every injection/click path is wrapped in try/catch(Throwable) so that a
 * launcher update that shifts internals can, at worst, omit the row -- it can never crash the
 * launcher.
 */
@SuppressWarnings("RedundantThrows")
@LauncherModPack
public class RecentsForceClose extends XposedModPack {
	private static boolean enabled = false;

	// Stable marker placed on our injected row so a repeated populate (orientation/insets change,
	// split-screen) can detect and skip an already-present row instead of appending a duplicate.
	private static final Object ROW_TAG = "PixelXpert_RecentsForceCloseRow";

	// quickstep internals are obfuscated; resolved once lazily by type and cached.
	private String mTaskViewFieldName = null;

	public RecentsForceClose(Context context) {
		super(context);
	}

	@Override
	public void onPreferenceUpdated(String... Key) {
		enabled = Xprefs.getBoolean("RecentsForceCloseEnabled", false);
	}

	@Override
	public void onPackageLoaded(XposedModuleInterface.PackageReadyParam PRParam) throws Throwable {
		// The task menu is one of two quickstep classes depending on launcher version/form factor, and
		// BOTH can be present in the dex at once: the older full-width TaskMenuView often lingers beside
		// TaskMenuViewWithArrow (the arrow/bubble menu that actually renders on modern Pixels). So hook
		// each independently instead of picking one -- whichever class is shown will fire, and the other
		// hook is a harmless no-op. The injected-row dedupe guard prevents any double injection.
		//
		// Each class also builds its option rows via a different method, so for each we try the known
		// candidates in order and attach to the FIRST that exists (its hook-handle set is non-empty),
		// which both avoids hooking two methods on the same class and pinpoints launcher shifts in-log.
		hookTaskMenu("com.android.quickstep.views.TaskMenuView",
				"populateAndLayoutMenu", "addMenuOptions");
		hookTaskMenu("com.android.quickstep.views.TaskMenuViewWithArrow",
				"addMenuOptions", "populateAndShowForTask", "populateAndLayoutMenu");
	}

	/**
	 * Resolves {@code className} (no-op if absent on this launcher) and hooks the first of
	 * {@code candidateMethods} that exists, running {@link #injectForceCloseRow} after it so our row is
	 * appended once the native option rows are laid out. Logs which method attached (and the handle
	 * count) -- or a warning when none matched -- so the on-device log pinpoints a launcher shift.
	 */
	private void hookTaskMenu(String className, String... candidateMethods) {
		ReflectedClass menuClass = ReflectedClass.ofIfPossible(className);
		if (menuClass.getClazz() == null) {
			log(getClass().getSimpleName() + ": menu class not present, skipping: " + className);
			return;
		}

		for (String method : candidateMethods) {
			try {
				int hooks = menuClass
						.after(method)
						.run(param -> injectForceCloseRow(param.thisObject))
						.size();
				if (hooks > 0) {
					log(getClass().getSimpleName() + ": hooked " + className + "#" + method
							+ " (" + hooks + " method(s))");
					return;
				}
			}
			catch (Throwable t) {
				log(getClass().getSimpleName() + ": hook attempt failed for " + className + "#" + method, t);
			}
		}

		log(getClass().getSimpleName() + ": no known population method matched on " + className
				+ "; Force close row will not appear there");
	}

	/**
	 * Clones an existing native option row for styling, relabels it "Force close" and wires the
	 * force-stop click action, then appends it to the bottom of the option container.
	 */
	private void injectForceCloseRow(Object menuView) {
		if (!enabled) return;

		try {
			if (!(menuView instanceof ViewGroup)) return;

			// Locate the container that holds the native option rows. The menu view itself is a
			// ViewGroup; the options live in a child group with >0 children.
			ViewGroup optionsContainer = findOptionsContainer((ViewGroup) menuView);
			if (optionsContainer == null || optionsContainer.getChildCount() == 0) return;

			// populateAndLayoutMenu can fire more than once on the same live menu (orientation/insets
			// change, split-screen). Skip if our row is already present to avoid duplicate rows.
			if (hasInjectedRow(optionsContainer)) return;

			// Gate injection on the task being resolvable so we never add a dead row, but resolve the
			// task lazily at click time (below) so a reused menu/row never acts on a stale task.
			if (resolveTask(menuView) == null) return;

			// Clone an existing row for native styling (background via constant state, like
			// NotificationExpander). We build a fresh, simple row laid out like the template.
			View template = optionsContainer.getChildAt(optionsContainer.getChildCount() - 1);
			View row = buildRow(template);
			if (row == null) return;

			row.setTag(ROW_TAG);

			final Object menuViewRef = menuView;
			row.setOnClickListener(v -> onForceCloseClicked(menuViewRef));

			optionsContainer.addView(row);
		}
		catch (Throwable t) {
			// Never let a launcher-internals shift crash the menu: worst case the row is absent.
			log(getClass().getSimpleName() + ": failed to inject Force close row", t);
		}
	}

	/**
	 * Finds the ViewGroup that holds the option rows: the descendant group with the most direct
	 * children, searched recursively. TaskMenuView keeps its rows in a direct child, but the arrow
	 * menu (TaskMenuViewWithArrow) nests them deeper in {@code mOptionLayout}, so a one-level scan would
	 * miss it. Falls back to the menu view itself when no richer group is found.
	 */
	private ViewGroup findOptionsContainer(ViewGroup menuView) {
		ViewGroup best = findRichestGroup(menuView, null);
		return best != null ? best : menuView;
	}

	/** Recursively returns the descendant ViewGroup with the greatest direct child count. */
	private ViewGroup findRichestGroup(ViewGroup group, ViewGroup best) {
		for (int i = 0; i < group.getChildCount(); i++) {
			View child = group.getChildAt(i);
			if (child instanceof ViewGroup) {
				ViewGroup childGroup = (ViewGroup) child;
				if (best == null || childGroup.getChildCount() > best.getChildCount()) {
					best = childGroup;
				}
				best = findRichestGroup(childGroup, best);
			}
		}
		return best;
	}

	/** Returns true if a previously-injected Force close row (carrying {@link #ROW_TAG}) is present. */
	private boolean hasInjectedRow(ViewGroup optionsContainer) {
		for (int i = 0; i < optionsContainer.getChildCount(); i++) {
			if (ROW_TAG.equals(optionsContainer.getChildAt(i).getTag())) return true;
		}
		return false;
	}

	/**
	 * Builds a "Force close" row styled after the supplied template row. The background drawable is
	 * cloned from the template (constant-state copy, as in NotificationExpander); the label uses the
	 * module's {@code recents_force_close_label} string and the {@code ic_close} drawable.
	 */
	private View buildRow(View template) {
		try {
			// Short button label ("Force close"); recents_force_close_title is the settings subtitle.
			final CharSequence label = XPLauncher.moduleResources.getString(R.string.recents_force_close_label);
			final Drawable icon = ResourcesCompat.getDrawable(
					XPLauncher.moduleResources, R.drawable.ic_close, mContext.getTheme());

			// If the template is itself a row group with a label/icon, mirror it for native fidelity.
			if (template instanceof ViewGroup) {
				ViewGroup templateGroup = (ViewGroup) template;
				android.widget.LinearLayout row = new android.widget.LinearLayout(mContext);
				row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
				row.setGravity(android.view.Gravity.CENTER_VERTICAL);

				ViewGroup.LayoutParams templateParams = templateGroup.getLayoutParams();
				if (templateParams != null) {
					row.setLayoutParams(new ViewGroup.LayoutParams(templateParams.width, templateParams.height));
				}

				int pl = templateGroup.getPaddingLeft();
				int pt = templateGroup.getPaddingTop();
				int pr = templateGroup.getPaddingRight();
				int pb = templateGroup.getPaddingBottom();
				row.setPadding(pl, pt, pr, pb);

				try {
					Drawable bg = templateGroup.getBackground();
					if (bg != null && bg.getConstantState() != null) {
						row.setBackground(bg.getConstantState().newDrawable());
					}
				}
				catch (Throwable ignored) {}

				// Mirror icon + text sizing/colors from the template's children if present.
				TextView templateText = findFirstTextView(templateGroup);
				ImageView templateIcon = findFirstImageView(templateGroup);

				ImageView iconView = new ImageView(mContext);
				iconView.setImageDrawable(icon);
				if (templateIcon != null) {
					try {
						iconView.setColorFilter(templateText != null
								? templateText.getCurrentTextColor()
								: android.graphics.Color.WHITE);
					}
					catch (Throwable ignored) {}
					ViewGroup.LayoutParams iconParams = templateIcon.getLayoutParams();
					if (iconParams != null) {
						iconView.setLayoutParams(new ViewGroup.LayoutParams(iconParams.width, iconParams.height));
					}
				}
				row.addView(iconView);

				TextView textView = new TextView(mContext);
				textView.setText(label);
				if (templateText != null) {
					try {
						textView.setTextColor(templateText.getCurrentTextColor());
						textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, templateText.getTextSize());
						textView.setTypeface(templateText.getTypeface());
					}
					catch (Throwable ignored) {}
					android.widget.LinearLayout.LayoutParams textParams =
							new android.widget.LinearLayout.LayoutParams(
									ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					textParams.setMarginStart(templateGroup.getPaddingLeft());
					textView.setLayoutParams(textParams);
				}
				row.addView(textView);

				return row;
			}

			// Template is not a group (unusual): fall back to a simple text row.
			TextView textView = new TextView(mContext);
			textView.setText(label);
			if (template instanceof TextView) {
				try {
					textView.setTextColor(((TextView) template).getCurrentTextColor());
					textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, ((TextView) template).getTextSize());
				}
				catch (Throwable ignored) {}
			}
			try {
				Drawable bg = template.getBackground();
				if (bg != null && bg.getConstantState() != null) {
					textView.setBackground(bg.getConstantState().newDrawable());
				}
			}
			catch (Throwable ignored) {}
			textView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
			return textView;
		}
		catch (Throwable t) {
			log(getClass().getSimpleName() + ": failed to build Force close row", t);
			return null;
		}
	}

	private TextView findFirstTextView(ViewGroup group) {
		for (int i = 0; i < group.getChildCount(); i++) {
			View child = group.getChildAt(i);
			if (child instanceof TextView) return (TextView) child;
			if (child instanceof ViewGroup) {
				TextView nested = findFirstTextView((ViewGroup) child);
				if (nested != null) return nested;
			}
		}
		return null;
	}

	private ImageView findFirstImageView(ViewGroup group) {
		for (int i = 0; i < group.getChildCount(); i++) {
			View child = group.getChildAt(i);
			if (child instanceof ImageView) return (ImageView) child;
			if (child instanceof ViewGroup) {
				ImageView nested = findFirstImageView((ViewGroup) child);
				if (nested != null) return nested;
			}
		}
		return null;
	}

	/**
	 * Resolves the {@code Task} associated with this menu instance.
	 *
	 * <p>The menu holds a reference to its {@code TaskView} in a field whose name is obfuscated, so
	 * we locate it by type (the field-scan precedent from CustomNavGestures.saveFocusedTask), then
	 * pull the {@code Task} off the {@code TaskView}.
	 */
	private Object resolveTask(Object menuView) {
		try {
			// TaskMenuView path: a direct TaskView field whose getTask()/typed ".Task" field gives the task.
			Object taskView = findTaskView(menuView);
			if (taskView != null) {
				Object task = tryInvokeNoArg(taskView, "getTask");
				if (task != null) return task;
				task = findFieldValueByTypeName(taskView, ".Task");
				if (task != null) return task;
			}

			// Arrow-menu path: TaskMenuViewWithArrow holds no direct TaskView field -- the task sits behind
			// a TaskContainer/TaskIdAttributeContainer holder that exposes getTask().
			Object holder = findTaskHolder(menuView);
			if (holder != null) {
				Object task = tryInvokeNoArg(holder, "getTask");
				if (isTask(task)) return task;
			}
		}
		catch (Throwable t) {
			log(getClass().getSimpleName() + ": failed to resolve task", t);
		}
		return null;
	}

	/** Invokes a no-arg method by name, returning its result or null on any failure (incl. absence). */
	private Object tryInvokeNoArg(Object target, String methodName) {
		try {
			// findMethodBestMatch throws NoSuchMethodError (never returns null) when absent -> caught here.
			return findMethodBestMatch(target.getClass(), methodName).invoke(target);
		}
		catch (Throwable ignored) {
			return null;
		}
	}

	/** True if {@code o} looks like a recents {@code Task} model object (class name ends in ".Task"). */
	private boolean isTask(Object o) {
		return o != null && o.getClass().getName().endsWith(".Task");
	}

	/**
	 * Finds an object held by the menu that exposes a no-arg {@code getTask()} -- the arrow menu's
	 * TaskContainer/TaskIdAttributeContainer. Located by capability (has getTask) rather than by type
	 * name, since the holder type is obfuscated/version-specific.
	 */
	private Object findTaskHolder(Object menuView) {
		try {
			for (Class<?> c = menuView.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (!f.isAccessible()) f.setAccessible(true);
					Object value = f.get(menuView);
					if (value == null || value == menuView) continue;
					if (isTask(tryInvokeNoArg(value, "getTask"))) return value;
				}
			}
		}
		catch (Throwable ignored) {}
		return null;
	}

	/** Finds the TaskView held by the menu instance, locating the field by type name. */
	private Object findTaskView(Object menuView) {
		try {
			if (mTaskViewFieldName != null) {
				Field f = findField(menuView.getClass(), mTaskViewFieldName);
				if (f != null) {
					if (!f.isAccessible()) f.setAccessible(true);
					return f.get(menuView);
				}
			}

			for (Class<?> c = menuView.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (f.getType().getName().endsWith(".TaskView")
							|| f.getType().getSimpleName().equals("TaskView")) {
						if (!f.isAccessible()) f.setAccessible(true);
						Object value = f.get(menuView);
						if (value != null) {
							mTaskViewFieldName = f.getName();
							return value;
						}
					}
				}
			}

			// Arrow-menu fallback: no direct TaskView field, but the TaskContainer holder yields one.
			Object holder = findTaskHolder(menuView);
			if (holder != null) {
				Object taskView = tryInvokeNoArg(holder, "getTaskView");
				if (taskView != null) return taskView;
			}
		}
		catch (Throwable ignored) {}
		return null;
	}

	private Object findFieldValueByTypeName(Object owner, String typeNameSuffix) {
		try {
			for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					if (f.getType().getName().endsWith(typeNameSuffix)) {
						if (!f.isAccessible()) f.setAccessible(true);
						Object value = f.get(owner);
						if (value != null) return value;
					}
				}
			}
		}
		catch (Throwable ignored) {}
		return null;
	}

	private Field findField(Class<?> clazz, String name) {
		for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				return c.getDeclaredField(name);
			}
			catch (Throwable ignored) {}
		}
		return null;
	}

	/**
	 * Force-stops the package (best effort; system/persistent apps may be denied), dismisses the
	 * task's Recents tile, closes the menu, and shows a Toast.
	 */
	private void onForceCloseClicked(Object menuView) {
		// Resolve the task fresh at click time (not at injection time) so a menu/row reused for a
		// different task never force-stops a stale/wrong app.
		Object task = resolveTask(menuView);
		if (task == null) {
			closeMenu(menuView);
			return;
		}

		// Null-check package + user before invoking: a missing package/userId (e.g. an unresolved or
		// work-profile task) would otherwise break overload resolution or NPE.
		String packageName = extractPackageName(task);
		Object userId = extractUserId(task);
		if (packageName == null || packageName.isEmpty() || userId == null) {
			closeMenu(menuView);
			return;
		}

		// Track whether the force-stop call actually returned without throwing: the OS may refuse
		// persistent/system/denied apps (SecurityException), and we must not claim success then.
		boolean forceStopped = false;
		try {
			callMethod(mContext.getSystemService(Context.ACTIVITY_SERVICE),
					"forceStopPackageAsUser",
					packageName,
					userId);
			forceStopped = true;
		}
		catch (Throwable t) {
			log(getClass().getSimpleName() + ": force-stop failed for " + packageName, t);
		}

		// Only dismiss the tile when the app was actually stopped; a no-op leaves the tile in place.
		if (forceStopped) {
			try {
				dismissTaskTile(menuView);
			}
			catch (Throwable t) {
				log(getClass().getSimpleName() + ": tile dismiss failed", t);
			}
		}

		// Close the menu regardless of outcome so the UI does not get stuck open.
		closeMenu(menuView);

		// Confirm only on a real force-stop; surface a distinct message on failure (no positive
		// feedback for a no-op).
		try {
			int msg = forceStopped
					? R.string.recents_force_close_label
					: R.string.recents_force_close_failed;
			Toast.makeText(mContext,
					XPLauncher.moduleResources.getString(msg),
					Toast.LENGTH_SHORT).show();
		}
		catch (Throwable ignored) {}
	}

	/**
	 * Extracts the task's package name, covering both task models seen across launchers: a direct
	 * ComponentName field (RecentTaskInfo-style {@code realActivity}/{@code topActivity}) and the
	 * systemui shared {@code Task} model, where it lives behind {@code key.baseIntent.getComponent()}.
	 * Returns null if no source resolves.
	 */
	private String extractPackageName(Object task) {
		for (String field : new String[]{"realActivity", "topActivity", "origActivity", "baseActivity"}) {
			Object value = getFieldQuietly(task, field);
			if (value instanceof ComponentName) return ((ComponentName) value).getPackageName();
		}

		Object key = getFieldQuietly(task, "key");
		if (key != null) {
			Object baseIntent = getFieldQuietly(key, "baseIntent");
			if (baseIntent instanceof Intent) {
				ComponentName component = ((Intent) baseIntent).getComponent();
				if (component != null) return component.getPackageName();
			}
		}
		return null;
	}

	/**
	 * Extracts the task's user id as a boxed int suitable for {@code forceStopPackageAsUser}, trying a
	 * direct {@code userId} field first and then the systemui {@code Task} model's {@code key.userId}.
	 * Returns null if neither resolves (caller then skips the force-stop rather than guessing a user).
	 */
	private Object extractUserId(Object task) {
		Object userId = getFieldQuietly(task, "userId");
		if (userId != null) return userId;

		Object key = getFieldQuietly(task, "key");
		if (key != null) return getFieldQuietly(key, "userId");
		return null;
	}

	/** Reads an object field, returning null instead of throwing when the field is absent. */
	private Object getFieldQuietly(Object owner, String fieldName) {
		try {
			return getObjectField(owner, fieldName);
		}
		catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * Closes the task menu. Tries the boolean-arg {@code close(boolean)} first, then the no-arg
	 * {@code close()}.
	 *
	 * <p>Each lookup is wrapped in its own try/catch because {@code findMethodBestMatch} throws
	 * {@code NoSuchMethodError} (it never returns null) when no signature matches -- without per-attempt
	 * guards the throw would skip the remaining fallbacks entirely.
	 */
	private void closeMenu(Object menuView) {
		try {
			findMethodBestMatch(menuView.getClass(), "close", boolean.class).invoke(menuView, true);
			return;
		}
		catch (Throwable ignored) {}

		try {
			// Empty args -> resolves the no-arg close() overload.
			findMethodBestMatch(menuView.getClass(), "close").invoke(menuView);
		}
		catch (Throwable ignored) {}
	}

	/**
	 * Dismisses this task's Recents tile. Reaches RecentsView (the precedent is ClearAllButtonMod's
	 * dismissAllTasks hook) via the menu's TaskView, then calls a single-task dismiss.
	 */
	private void dismissTaskTile(Object menuView) {
		Object taskView = findTaskView(menuView);
		if (taskView == null) return;

		// Preferred path: RecentsView#dismissTask(TaskView, boolean animate, boolean removeTask).
		// Each findMethodBestMatch attempt is guarded on its own because it throws NoSuchMethodError
		// (never returns null) when the signature is absent; a single shared try/catch would let the
		// first throw skip the TaskView-level fallback below.
		ReflectedClass RecentsViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.RecentsView");
		Object recentsView = findRecentsView(taskView, RecentsViewClass);
		if (recentsView != null && RecentsViewClass.getClazz() != null) {
			try {
				findMethodBestMatch(RecentsViewClass.getClazz(), "dismissTask",
						taskView.getClass(), boolean.class, boolean.class)
						.invoke(recentsView, taskView, true, true);
				return;
			}
			catch (Throwable ignored) {}
		}

		// Fall back to a TaskView-level dismiss if the RecentsView path is unavailable.
		try {
			findMethodBestMatch(taskView.getClass(), "dismiss").invoke(taskView);
		}
		catch (Throwable t) {
			log(getClass().getSimpleName() + ": tile dismiss failed", t);
		}
	}

	/** Walks up the view hierarchy from the TaskView to find the enclosing RecentsView. */
	private Object findRecentsView(Object taskView, ReflectedClass recentsViewClass) {
		try {
			if (recentsViewClass.getClazz() == null || !(taskView instanceof View)) return null;
			View v = (View) taskView;
			for (int i = 0; i < 20 && v != null; i++) {
				if (recentsViewClass.getClazz().isInstance(v)) return v;
				if (v.getParent() instanceof View) {
					v = (View) v.getParent();
				} else {
					break;
				}
			}
		}
		catch (Throwable ignored) {}
		return null;
	}
}

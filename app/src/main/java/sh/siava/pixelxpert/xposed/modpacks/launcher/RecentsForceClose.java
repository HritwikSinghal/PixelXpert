package sh.siava.pixelxpert.xposed.modpacks.launcher;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findMethodBestMatch;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static sh.siava.pixelxpert.xposed.XPrefs.Xprefs;
import static sh.siava.pixelxpert.xposed.utils.toolkit.Logger.log;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
		// The task menu class differs across launcher versions; try both known names.
		ReflectedClass TaskMenuViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.TaskMenuView");
		if (TaskMenuViewClass.getClazz() == null) {
			TaskMenuViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.TaskMenuViewWithArrow");
		}
		if (TaskMenuViewClass.getClazz() == null) {
			log(getClass().getSimpleName() + ": could not resolve TaskMenuView/TaskMenuViewWithArrow; bailing");
			return;
		}

		// "populateAndLayoutMenu" finishes adding the native option rows; hooking it after lets us
		// append our row at the bottom of the freshly-built list.
		TaskMenuViewClass
				.after("populateAndLayoutMenu")
				.run(param -> injectForceCloseRow(param.thisObject));
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

			// Resolve the focused task's package + user before building the row so we can skip
			// entirely if it cannot be determined.
			Object task = resolveTask(menuView);
			if (task == null) return;

			Object realActivity = getObjectField(task, "realActivity");
			if (!(realActivity instanceof ComponentName)) return;
			final String packageName = ((ComponentName) realActivity).getPackageName();
			if (packageName == null || packageName.isEmpty()) return;
			final Object userId = getObjectField(task, "userId");

			// Clone an existing row for native styling (background via constant state, like
			// NotificationExpander). We build a fresh, simple row laid out like the template.
			View template = optionsContainer.getChildAt(optionsContainer.getChildCount() - 1);
			View row = buildRow(template);
			if (row == null) return;

			final Object menuViewRef = menuView;
			row.setOnClickListener(v -> onForceCloseClicked(menuViewRef, task, packageName, userId));

			optionsContainer.addView(row);
		}
		catch (Throwable t) {
			// Never let a launcher-internals shift crash the menu: worst case the row is absent.
			log(getClass().getSimpleName() + ": failed to inject Force close row", t);
		}
	}

	/**
	 * Finds the child ViewGroup that holds the option rows. Falls back to the menu view itself if
	 * no nested group is found.
	 */
	private ViewGroup findOptionsContainer(ViewGroup menuView) {
		ViewGroup best = null;
		int bestCount = 0;
		for (int i = 0; i < menuView.getChildCount(); i++) {
			View child = menuView.getChildAt(i);
			if (child instanceof ViewGroup) {
				int count = ((ViewGroup) child).getChildCount();
				if (count > bestCount) {
					bestCount = count;
					best = (ViewGroup) child;
				}
			}
		}
		return best != null ? best : menuView;
	}

	/**
	 * Builds a "Force close" row styled after the supplied template row. The background drawable is
	 * cloned from the template (constant-state copy, as in NotificationExpander); the label uses the
	 * module's {@code recents_force_close_title} string and the {@code ic_close} drawable.
	 */
	private View buildRow(View template) {
		try {
			final CharSequence label = XPLauncher.moduleResources.getString(R.string.recents_force_close_title);
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
			Object taskView = findTaskView(menuView);
			if (taskView == null) return null;

			// TaskView exposes its Task via getTask() in most builds; fall back to a typed field.
			try {
				Method getTask = findMethodBestMatch(taskView.getClass(), "getTask");
				if (getTask != null) {
					Object task = getTask.invoke(taskView);
					if (task != null) return task;
				}
			}
			catch (Throwable ignored) {}

			return findFieldValueByTypeName(taskView, ".Task");
		}
		catch (Throwable t) {
			log(getClass().getSimpleName() + ": failed to resolve task", t);
			return null;
		}
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
	private void onForceCloseClicked(Object menuView, Object task, String packageName, Object userId) {
		// Force-stop + dismiss are best-effort: the OS may refuse persistent/system apps.
		try {
			callMethod(mContext.getSystemService(Context.ACTIVITY_SERVICE),
					"forceStopPackageAsUser",
					packageName,
					userId);

			dismissTaskTile(menuView, task);
		}
		catch (Throwable t) {
			log(getClass().getSimpleName() + ": force-stop/dismiss failed", t);
		}

		// Close the menu regardless of force-stop outcome.
		try {
			Method close = findMethodBestMatch(menuView.getClass(), "close", boolean.class);
			if (close != null) {
				close.invoke(menuView, true);
			} else {
				Method closeNoArg = findMethodBestMatch(menuView.getClass(), "close");
				if (closeNoArg != null) closeNoArg.invoke(menuView);
			}
		}
		catch (Throwable ignored) {}

		try {
			Toast.makeText(mContext,
					XPLauncher.moduleResources.getString(R.string.recents_force_close_title),
					Toast.LENGTH_SHORT).show();
		}
		catch (Throwable ignored) {}
	}

	/**
	 * Dismisses this task's Recents tile. Reaches RecentsView (the precedent is ClearAllButtonMod's
	 * dismissAllTasks hook) via the menu's TaskView, then calls a single-task dismiss.
	 */
	private void dismissTaskTile(Object menuView, Object task) {
		try {
			Object taskView = findTaskView(menuView);
			if (taskView == null) return;

			// TaskView usually has a parent RecentsView. Resolve RecentsView via its known class.
			ReflectedClass RecentsViewClass = ReflectedClass.ofIfPossible("com.android.quickstep.views.RecentsView");
			Object recentsView = findRecentsView(taskView, RecentsViewClass);

			if (recentsView != null && RecentsViewClass.getClazz() != null) {
				// RecentsView#dismissTask(TaskView, boolean animate, boolean removeTask)
				Method dismissTask = findMethodBestMatch(RecentsViewClass.getClazz(), "dismissTask",
						taskView.getClass(), boolean.class, boolean.class);
				if (dismissTask != null) {
					dismissTask.invoke(recentsView, taskView, true, true);
					return;
				}
			}

			// Fall back to a TaskView-level dismiss if RecentsView path is unavailable.
			Method dismiss = findMethodBestMatch(taskView.getClass(), "dismiss");
			if (dismiss != null) {
				dismiss.invoke(taskView);
			}
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

package sh.siava.pixelxpert.xposed.utils.toolkit;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * Central module logger. All log lines are tagged with {@link #TAG} and routed to the LSPosed
 * module log (via {@link XposedInterface#log}) with an {@code android.util.Log} fallback for the
 * window before the module is fully loaded.
 *
 * <p>Levels: {@code logError}/{@code logWarn}/{@code logInfo} (and the legacy {@code log(...)}
 * overloads, which map to INFO) ALWAYS emit. {@code logDebug}/{@code logVerbose} emit only when
 * the runtime {@link #setVerbose verbose} flag is on -- toggled by the user's "Verbose logging"
 * preference and synced per-process from {@code XPrefs.loadEverything}. The level is prefixed into
 * the message text ({@code "W/ ..."}) so it stays visible in logcat regardless of whether the
 * underlying log sink honours a non-default priority.
 */
public class Logger {
	public static String TAG = "PixelXpert Lsposed Module";
	private static XposedInterface xposedInterface;

	/** Gate for DEBUG/VERBOSE detail; default off, flipped live by the "Verbose logging" pref. */
	private static volatile boolean verbose = false;

	public static void setXposedInterface(XposedInterface xposedInterface)
	{
		Logger.xposedInterface = xposedInterface;
	}

	public static void setVerbose(boolean verbose)
	{
		Logger.verbose = verbose;
	}

	public static boolean isVerbose()
	{
		return verbose;
	}

	// --- Legacy API (kept for source compatibility): always emit, at INFO level. ---

	/** Logs to logcat, tagged with {@link #TAG}. Always emitted (INFO). */
	public static void log(String text) {
		emit(Log.INFO, text, null);
	}

	/** Logs to logcat with a throwable, tagged with {@link #TAG}. Always emitted (INFO). */
	public static void log(String text, Throwable t) {
		emit(Log.INFO, text, t);
	}

	/** Logs a throwable, tagged with {@link #TAG}. Always emitted (INFO). */
	public static void log(Throwable t) {
		emit(Log.INFO, "", t);
	}

	// --- Leveled API. ---

	/** A feature/hook failed in a way that breaks it. Always emitted. */
	public static void logError(String text) { emit(Log.ERROR, text, null); }
	public static void logError(String text, Throwable t) { emit(Log.ERROR, text, t); }

	/** A hook did not install, a precondition silently aborted a feature, or a recoverable
	 *  load-time failure occurred. Always emitted. */
	public static void logWarn(String text) { emit(Log.WARN, text, null); }
	public static void logWarn(String text, Throwable t) { emit(Log.WARN, text, t); }

	/** Coarse lifecycle information. Always emitted. */
	public static void logInfo(String text) { emit(Log.INFO, text, null); }
	public static void logInfo(String text, Throwable t) { emit(Log.INFO, text, t); }

	/** Detailed diagnostics; emitted only when verbose logging is enabled. */
	public static void logDebug(String text) { if (verbose) emit(Log.DEBUG, text, null); }
	public static void logDebug(String text, Throwable t) { if (verbose) emit(Log.DEBUG, text, t); }

	/** Fine-grained per-hook/per-event tracing; emitted only when verbose logging is enabled. */
	public static void logVerbose(String text) { if (verbose) emit(Log.VERBOSE, text, null); }
	public static void logVerbose(String text, Throwable t) { if (verbose) emit(Log.VERBOSE, text, t); }

	private static synchronized void emit(int priority, String text, Throwable t) {
		String leveled = levelPrefix(priority) + text;
		try {
			// Primary: LSPosed module log. PRIORITY_DEFAULT is the known-good priority; the level is
			// carried in the message prefix so it survives regardless of how the sink treats priority.
			if (t == null) {
				xposedInterface.log(XposedInterface.PRIORITY_DEFAULT, TAG, leveled);
			} else {
				xposedInterface.log(XposedInterface.PRIORITY_DEFAULT, TAG, leveled, t);
			}
		}
		// XposedInterface isn't available before the module is fully loaded -> fall back to logcat.
		catch (Throwable ignored) {
			if (t == null) {
				Log.println(priority, TAG, leveled);
			} else {
				Log.println(priority, TAG, leveled + '\n' + Log.getStackTraceString(t));
			}
		}
	}

	private static String levelPrefix(int priority) {
		switch (priority) {
			case Log.ERROR:   return "E/ ";
			case Log.WARN:    return "W/ ";
			case Log.DEBUG:   return "D/ ";
			case Log.VERBOSE: return "V/ ";
			default:          return "I/ ";
		}
	}
}

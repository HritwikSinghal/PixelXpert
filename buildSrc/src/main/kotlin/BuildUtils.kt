
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File
import java.util.Properties

/**
 * Versioning model and IO helpers for the PixelXpert build.
 *
 * Design intent (why this shape):
 *  - `version.properties` is the single source of truth (VERSION_CODE + VERSION_NAME).
 *  - The READ path ([readCurrentVersion]/[Project.getVersionCode]/[Project.getVersionName]) always reports the
 *    CURRENT persisted version. It must never increment, otherwise the value depends on whether the
 *    increment task has already run and the bump gets double-applied.
 *  - The INCREMENT path ([incrementVersionLogic]) is the only place that computes "next" (code + 1 for
 *    canary), persists it, and propagates it to the derived metadata files.
 *  - Derived files are rewritten with format-aware writers (line-based for .prop, real JSON parser for
 *    .json) so we never corrupt unrelated fields the way an unanchored regex substitution could.
 */

/** Typed snapshot of a single version. Keeps code/name from drifting apart across call sites. */
data class VersionInfo(val code: Int, val name: String)

private const val PROP_VERSION_CODE = "VERSION_CODE"
private const val PROP_VERSION_NAME = "VERSION_NAME"

/**
 * Baseline VERSION_CODE used when the key is missing from version.properties entirely.
 * Shared by the read and increment paths so a fresh checkout (no version.properties yet) bumps to the
 * same first value regardless of which path runs first. Previously the read path defaulted to "1" and
 * the increment path to "0", so a missing key produced inconsistent codes (read saw 1, increment
 * produced 0+1=1 -- coincidentally equal, but the divergent defaults were a latent bug).
 */
private const val DEFAULT_VERSION_CODE = 0

/**
 * Parses VERSION_CODE from already-loaded [props], failing loudly on a present-but-malformed value.
 * `Properties.getProperty(key, default)` only applies the default when the KEY is absent; a present
 * key with a blank/garbage value would slip past it and then blow up with an opaque
 * NumberFormatException deep inside `toInt()`. We trim and use toIntOrNull so the error names the file
 * and the offending value.
 */
private fun parseVersionCode(props: Properties, versionFile: File): Int {
    val raw = props.getProperty(PROP_VERSION_CODE) ?: return DEFAULT_VERSION_CODE
    return raw.trim().toIntOrNull()
        ?: error("Invalid $PROP_VERSION_CODE in ${versionFile.absolutePath}: \"$raw\" is not an integer.")
}

/** Canary names are purely derived from the code, so they are always reproducible. */
fun canaryVersionName(versionCode: Int): String = "canary-$versionCode"

/**
 * Reads the current version from [versionFile] without mutating anything.
 * For stable builds the human-facing name comes from the latest git tag rather than the stored name,
 * so callers pass it in via [stableName] (a git tag); canary derives its name from the code.
 */
fun readCurrentVersion(versionFile: File, isStable: Boolean, stableName: String?): VersionInfo {
    val props = Properties()
    if (versionFile.exists()) {
        versionFile.inputStream().use { props.load(it) }
    }
    val code = parseVersionCode(props, versionFile)
    val name = if (isStable) {
        requireNotNull(stableName) { "Stable builds require a git-tag version name." }
    } else {
        canaryVersionName(code)
    }
    return VersionInfo(code, name)
}

/**
 * Lazily resolves the current [VersionInfo] for use by `app/build.gradle.kts`.
 * Stays a [Provider] so configuration cache stays valid and the git tag is only invoked when needed.
 */
fun Project.getVersionInfoProvider(): Provider<VersionInfo> {
    val versionFile = rootProject.file("version.properties")
    val isStableProvider = providers.gradleProperty("channel").map { it == "stable" }.orElse(false)
    val gitVersionProvider = providers.of(GitTagProvider::class.java) {}

    return isStableProvider.flatMap { isStable ->
        if (isStable) {
            gitVersionProvider.map { tag -> readCurrentVersion(versionFile, true, tag) }
        } else {
            providers.provider { readCurrentVersion(versionFile, false, null) }
        }
    }
}

/** Convenience read-path accessors. These report the CURRENT version and never increment. */
fun Project.getVersionCode(): Int = getVersionInfoProvider().get().code

fun Project.getVersionName(): String = getVersionInfoProvider().get().name

/**
 * Computes the next version, persists it (canary only), and rewrites the derived metadata files.
 *
 * Idempotency: the result is a pure function of the on-disk VERSION_CODE plus the channel, so the
 * derived files always end up describing exactly the version that gets persisted. Within a single task
 * invocation the code is read once and the next value is computed once, so there is no double-bump.
 */
fun incrementVersionLogic(
    isStable: Boolean,
    versionFile: File,
    filesToUpdate: List<File>,
    stableName: String?,
) {
    val props = Properties()
    if (versionFile.exists()) {
        versionFile.inputStream().use { props.load(it) }
    }

    // Same parse contract as the read path: a present-but-malformed VERSION_CODE fails loudly
    // (naming the file and value) instead of throwing an opaque NumberFormatException, and a
    // missing key falls back to the SHARED DEFAULT_VERSION_CODE baseline so read and increment
    // never disagree on the first value for a fresh checkout.
    val currentCode = parseVersionCode(props, versionFile)
    // Stable keeps its code (the git tag drives the name); canary advances by one.
    val nextCode = if (isStable) currentCode else currentCode + 1
    val nextName = if (isStable) {
        requireNotNull(stableName) { "Stable builds require a git-tag version name." }
    } else {
        canaryVersionName(nextCode)
    }
    val next = VersionInfo(nextCode, nextName)

    // Only canary advances the persisted source of truth; stable derives its name from git, not the file.
    if (!isStable) {
        props.setProperty(PROP_VERSION_CODE, next.code.toString())
        props.setProperty(PROP_VERSION_NAME, next.name)
        versionFile.outputStream().use { props.store(it, "Updated via Gradle Task") }
    }

    filesToUpdate.forEach { writeVersionToFile(it, next) }
}

/** Dispatches to the correct format-aware writer based on file extension. */
private fun writeVersionToFile(file: File, version: VersionInfo) {
    if (!file.exists()) {
        // Previously a silently-skipped missing file. A listed-but-absent metadata file is almost
        // always a stale reference (a renamed/deleted descriptor) and silently skipping it means the
        // file never gets the version stamp and nobody notices. Warn loudly to stderr (Gradle
        // surfaces task stderr) instead of swallowing it, so dead references are visible in CI logs.
        System.err.println(
            "WARNING: version metadata file not found, skipping stamp: ${file.absolutePath}"
        )
        return
    }
    when (file.extension.lowercase()) {
        "json" -> writeVersionToJson(file, version)
        "prop" -> writeVersionToProp(file, version)
        else -> error("Unsupported version file type: ${file.name}")
    }
}

/**
 * Rewrites only the `version=` and `versionCode=` lines of a `.prop` file.
 * Line-based and anchored on the exact key so every other line (including its ordering, comments, and
 * the fork's updateJson URL) is preserved verbatim.
 */
private fun writeVersionToProp(file: File, version: VersionInfo) {
    // Rewrite line-by-line but preserve the file's EXACT byte shape otherwise: keep the original
    // line terminator (CRLF vs LF) and the presence/absence of a trailing newline. lineSequence()
    // + joinToString("\n") silently normalized CRLF -> LF and dropped a trailing newline, mutating
    // unrelated bytes and producing noisy diffs / potential tooling churn. We split on the original
    // terminator and re-join with it so only the two value lines change.
    val original = file.readText()
    val terminator = if (original.contains("\r\n")) "\r\n" else "\n"
    val hadTrailingNewline = original.endsWith("\n")
    // Strip a single trailing terminator before splitting so we don't emit a spurious empty final
    // element; we re-append it below only if it was there originally.
    val body = original.removeSuffix("\r\n").removeSuffix("\n")
    val updated = body.split(terminator).joinToString(terminator) { line ->
        when {
            line.startsWith("version=") -> "version=${version.name}"
            line.startsWith("versionCode=") -> "versionCode=${version.code}"
            else -> line
        }
    }
    file.writeText(if (hadTrailingNewline) updated + terminator else updated)
}

/**
 * Rewrites the `version`/`versionCode` fields of a `.json` metadata file via a real JSON parser
 * (Groovy's JsonSlurper/JsonOutput, already on the Gradle classpath) so unrelated fields such as the
 * fork's zipUrl/changelog URLs are preserved with correct types and escaping.
 *
 * Note: the `zipUrl`/`zipUrl_Xposed` fields have been repointed to the HritwikSinghal fork but still use
 * the upstream release-download path shape. The fork publishes CI artifacts rather than GitHub releases,
 * so these URLs will likely need adjustment once the fork's release/artifact URL scheme is finalized.
 */
@Suppress("UNCHECKED_CAST")
private fun writeVersionToJson(file: File, version: VersionInfo) {
    val parsed = JsonSlurper().parseText(file.readText()) as MutableMap<String, Any?>
    parsed["version"] = version.name
    parsed["versionCode"] = version.code
    file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(parsed)) + "\n")
}

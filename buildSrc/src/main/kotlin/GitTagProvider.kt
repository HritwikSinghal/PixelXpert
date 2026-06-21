import org.gradle.api.GradleException
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Resolves the intended version tag, used as the version name for stable builds.
 *
 * Selection contract (deterministic -- never adopts an arbitrary tag):
 *  1. If GITHUB_REF_NAME is set AND this is a tag push (GITHUB_REF starts with "refs/tags/"), use
 *     that exact triggering tag. On a tag-triggered CI run the tag that fired the build IS the
 *     version, so honor it directly.
 *  2. Otherwise take the nearest VERSION tag reachable from HEAD via
 *     `git describe --tags --abbrev=0 --match 'v*'`. The fork's real release tags are `v*`
 *     (v1.0.0, v.2.4.1, v5.1.1, ...); the CI artifact tags `fork-v*` and assorted junk
 *     (`test_tag`, `canary_builds`, `1.0.0-beta-02`) do NOT start with `v`, so the `v*` glob
 *     excludes them automatically and we never silently pick up a CI/test tag.
 *
 * The previous implementation used `git rev-list --tags --max-count=1` + `git describe`, which
 * returns whatever tag sits on the globally-newest tagged commit ANYWHERE in the repo -- that is
 * currently a CI tag like `fork-v5.1.1-2`, and could just as easily be `test_tag`. That is not the
 * intended version and is exactly the bug being fixed here.
 *
 * Fails loudly: a missing tag or a failing git command throws a [GradleException] instead of
 * returning a sentinel string. The old "Error" fallback silently became the published version name,
 * producing builds tagged literally "Error" -- a stable build with no tag is unrecoverable.
 */
abstract class GitTagProvider : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        // Prefer the triggering tag on a CI tag push -- it is unambiguously the version being built.
        val refName = System.getenv("GITHUB_REF_NAME")?.trim().orEmpty()
        val ref = System.getenv("GITHUB_REF")?.trim().orEmpty()
        if (refName.isNotEmpty() && ref.startsWith("refs/tags/")) {
            return refName
        }

        // Otherwise resolve the nearest version (`v*`) tag reachable from HEAD. --abbrev=0 yields the
        // bare tag name (no commit suffix); --match restricts to the version namespace.
        val described = git("describe", "--tags", "--abbrev=0", "--match", "v*", "HEAD")
        if (described.isEmpty()) {
            throw GradleException(
                "No reachable version tag (matching 'v*') found from HEAD; a stable build requires a " +
                    "version-tagged commit to derive its version name."
            )
        }
        return described
    }

    private fun git(vararg args: String): String {
        val stdout = ByteArrayOutputStream()
        try {
            execOperations.exec {
                commandLine(listOf("git") + args)
                standardOutput = stdout
            }
        } catch (e: Exception) {
            throw GradleException("git ${args.joinToString(" ")} failed: ${e.message}", e)
        }
        return stdout.toString().trim()
    }
}

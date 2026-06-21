import org.gradle.api.GradleException
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Resolves the most recent git tag, used as the version name for stable builds.
 *
 * Fails loudly: a missing tag or a failing git command throws a [GradleException] instead of
 * returning a sentinel string. The previous "Error" fallback silently became the published version
 * name, producing builds tagged literally "Error" -- a stable build with no tag is unrecoverable and
 * must stop the build.
 */
abstract class GitTagProvider : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String {
        val lastRev = git("rev-list", "--tags", "--max-count=1")
        if (lastRev.isEmpty()) {
            throw GradleException(
                "No git tags found; a stable build requires a tagged commit to derive its version name."
            )
        }
        return git("describe", "--tags", lastRev)
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

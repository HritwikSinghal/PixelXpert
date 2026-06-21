import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

/**
 * Bumps the version and propagates it to the derived metadata files.
 *
 * The task deliberately does NOT accept a pre-computed canary name: the canary name is a function of
 * the freshly-incremented code, so deriving it inside [incrementVersionLogic] avoids a stale value
 * captured at configuration time. [stableName] is supplied lazily (from the git tag) and only used for
 * stable builds.
 */
abstract class IncrementVersionTask : DefaultTask() {
	@get:InputFile
	abstract val versionFile: RegularFileProperty

	@get:OutputFiles
	abstract val filesToUpdate: ConfigurableFileCollection

	@get:Input
	abstract val stable: Property<Boolean>

	/** Git-tag-derived name for stable builds; absent (and unused) for canary. */
	@get:[Input Optional]
	abstract val stableName: Property<String>

	@TaskAction
	fun increment() {
		incrementVersionLogic(
			stable.get(),
			versionFile.get().asFile,
			filesToUpdate.files.toList(),
			stableName.orNull,
		)
	}
}

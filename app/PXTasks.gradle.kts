val taskGroup = "pixelxpert"

// ORDERING CONTRACT (fix for "finalizedBy does not order the bump before assembleRelease"):
// The previous chained .finalizedBy("incrementCanaryVersion").finalizedBy("assembleRelease")
// .finalizedBy("createZip") attached three UNORDERED finalizers to the aggregator, so Gradle was
// free to assemble the APK before the version bump ran -- shipping a stale version.
//
// We now express a real ordered pipeline:
//   incrementVersion  ->  assembleRelease  ->  createZip
// using dependsOn (to pull the whole chain into the task graph) + mustRunAfter (to ORDER it). This
// is the "increment-then-stamp-everything" contract: the bump persists + stamps the NEXT version
// first, then assembleRelease produces an APK whose lazy output.versionCode/versionName (see
// app/build.gradle.kts) resolve to that same just-stamped version, then createZip packages it.
tasks.register("buildCanary") {
	group = taskGroup
	description = "Builds a Canary APK with count-based versioning"

	dependsOn("incrementCanaryVersion", "assembleRelease", "renameReleaseApk", "createZip")
}

tasks.register("buildStable") {
	group = taskGroup
	description = "Builds a Stable APK with Git Tag versioning"

	dependsOn("incrementStableVersion", "assembleRelease", "renameReleaseApk", "createZip")
}

tasks.register<IncrementVersionTask>("incrementCanaryVersion") {
	group = taskGroup
	description = "Increments the canary version and saves it to the related files"

	versionFile.set(rootProject.file("version.properties"))

	stable.set(false)

	filesToUpdate.from(
		rootProject.file("MagiskModBase/module.prop"),
		rootProject.file("latestCanary.json"),
		rootProject.file("MagiskModuleUpdate_Xposed.json"),
		rootProject.file("MagiskModuleUpdate_Full.json")
	)
	// Canary names are derived from the incremented code inside the task; no name is passed in.
}

tasks.register<IncrementVersionTask>("incrementStableVersion") {
	group = taskGroup
	description = "Increments the stable version and saves it to the related files"

	versionFile.set(rootProject.file("version.properties"))
	stable.set(true)
	filesToUpdate.from(
		rootProject.file("MagiskModBase/module.prop"),
		rootProject.file("latestStable.json"),
		rootProject.file("MagiskModuleUpdate.json"),
		rootProject.file("MagiskModuleUpdate_Full.json"),
		rootProject.file("MagiskModuleUpdate_Xposed.json"),
	)
	// Stable name comes from the latest git tag; resolved lazily via the value source.
	stableName.set(providers.of(GitTagProvider::class.java) {})
}

// AGP 9 removed the legacy applicationVariants API that let us set output.outputFileName, so the
// assembled APK keeps AGP's default name (app-release.apk). Both createZip (below) and the CI
// staging step expect a stable build/outputs/apk/release/PixelXpert.apk -- the lazy-stamp refactor
// (commit 8c099810) dropped the rename, so the zip silently shipped without an APK and the CI
// PixelXpert.apk copy failed. Restore the stable name by copying the assembled release APK after
// assembleRelease and before createZip.
tasks.register<Copy>("renameReleaseApk") {
	group = taskGroup
	description = "Copies the assembled release APK to a stable PixelXpert.apk name"

	dependsOn("assembleRelease")
	mustRunAfter("assembleRelease")

	// Copy into a dedicated dir (NOT AGP's managed outputs/apk/release) to avoid Gradle's
	// overlapping-output validation against AGP's own release tasks.
	from(layout.buildDirectory.dir("outputs/apk/release")) {
		include("*-release.apk")
	}
	rename { "PixelXpert.apk" }
	into(layout.buildDirectory.dir("distApk"))
}

tasks.register<Zip>("createZip") {
	group = taskGroup
	description = "Creates the final Magisk module file"

	mustRunAfter("incrementCanaryVersion")
	mustRunAfter("incrementStableVersion")
	mustRunAfter("assembleRelease")

	from(file("../MagiskModBase"))
	// Consume renameReleaseApk's output directly (the task provider, not a hardcoded path): this
	// wires a REAL task dependency + input tracking, so createZip pulls renameReleaseApk into the
	// graph and can never run before the APK is staged -- regardless of entry point. The old
	// from(file(...)) + mustRunAfter pair only ordered the two IF something else already pulled
	// renameReleaseApk in, so `./gradlew createZip` alone silently shipped a zip with no APK.
	from(tasks.named<Copy>("renameReleaseApk")) { into("system/priv-app/PixelXpert") }

	destinationDirectory.set(file("../output"))
	archiveFileName.set("PixelXpert.zip")
}
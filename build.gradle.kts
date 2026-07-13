plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.gradle.publish) apply false
	alias(libs.plugins.vanniktech.maven) apply false // https://github.com/vanniktech/gradle-maven-publish-plugin/issues/670#issuecomment-1839097676
	alias(libs.plugins.kotlin.dokka)
}

buildscript {
	repositories {
		google()
		mavenCentral()
	}
}

allprojects {
	group = "com.github.guilhe.kmp"
	version = "2.4.10-1.11.1"
}

dependencies {
	dokka(project(":kmp-composeuiviewcontroller-annotations"))
	dokka(project(":kmp-composeuiviewcontroller-gradle-plugin"))
}

tasks.register("publishLibraryModules") {
	description = "Publishes all library modules to Maven Central"
	dependsOn(":kmp-composeuiviewcontroller-common:publishToMavenCentral")
	dependsOn(":kmp-composeuiviewcontroller-annotations:publishToMavenCentral")
	finalizedBy(":kmp-composeuiviewcontroller-ksp:publishToMavenCentral")
}

tasks.register("publishLibraryModulesLocally") {
	description = "Publishes all library modules to the local Maven repository (~/.m2) for local testing (no signing required)"
	dependsOn(":kmp-composeuiviewcontroller-common:publishToMavenLocal")
	dependsOn(":kmp-composeuiviewcontroller-annotations:publishToMavenLocal")
	dependsOn(":kmp-composeuiviewcontroller-ksp:publishToMavenLocal")
	dependsOn(":kmp-composeuiviewcontroller-gradle-plugin:publishToMavenLocal")
}

tasks.register("serveDokka") {
	description = "Serves the generated Dokka documentation on a local web server. Use -Pport=PORT to specify a custom port (default: 8080)."
	dependsOn("dokkaGenerate")
	doLast {
		val docsDir = file("${rootProject.layout.buildDirectory.asFile.get().path}/dokka/html")
		val port = (project.findProperty("port") as String?)?.toIntOrNull() ?: 8080
		try {
			java.net.ServerSocket(port).use { true }
		} catch (_: Exception) {
			println("📖 Already serving Dokka docs at http://localhost:$port")
			return@doLast
		}

		val process = ProcessBuilder("python3", "-m", "http.server", "$port")
			.directory(docsDir)
			.redirectErrorStream(true)
			.start()
		println("📖 Serving Dokka docs at http://localhost:$port")
		Thread { process.inputStream.bufferedReader().forEachLine { println(it) } }.start()
		process.waitFor()
	}
}

tasks.register("buildAllSamples") {
	description = "Cleans (--no-build-cache), pre-generates KMP frameworks/Swift export packages, then builds all samples via xcodebuild"
	doLast {
		val samples = listOf(
			"sample-objc-export",
			"sample-objc-export-legacy",
			"sample-swift-export",
			"sample-swift-export-legacy"
		)

		val swiftExportSamples = setOf("sample-swift-export", "sample-swift-export-legacy")

		fun printSection(title: String) {
			println("\n" + "=".repeat(60))
			println(title)
			println("=".repeat(60))
		}

		val simulatorUDID: String by lazy {
			val proc = ProcessBuilder("xcrun", "simctl", "list", "devices", "available")
				.redirectErrorStream(true).start()
			val output = proc.inputStream.bufferedReader().readText()
			proc.waitFor()

			val iosSection = Regex("""-- iOS ([\d.]+) --\n((?:.*\n)*?)(?=--|\z)""").findAll(output)
				.maxByOrNull { match ->
					val parts = match.groupValues[1].split(".").map { it.toIntOrNull() ?: 0 }
					parts.getOrElse(0) { 0 } * 10_000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
				}
				?.groupValues?.get(2)
				?: throw GradleException("No iOS Simulator runtime found. Install an iOS simulator runtime in Xcode.")

			val udidPattern = Regex("""[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}""")
			iosSection.lineSequence()
				.firstOrNull { it.contains("iPhone") }
				?.let { udidPattern.find(it)?.value }
				?: throw GradleException("No available iPhone Simulator found. Install a simulator runtime in Xcode.")
		}

		val simulatorSdkVersion: String by lazy {
			val proc = ProcessBuilder("xcrun", "--sdk", "iphonesimulator", "--show-sdk-version")
				.redirectErrorStream(true).start()
			val out = proc.inputStream.bufferedReader().readText().trim()
			proc.waitFor()
			out.ifBlank { throw GradleException("Unable to determine iphonesimulator SDK version via xcrun.") }
		}

		fun iosDeploymentTarget(sample: String): String {
			val pbxproj = file("$sample/iosApp/Gradient.xcodeproj/project.pbxproj").readText()
			return Regex("""IPHONEOS_DEPLOYMENT_TARGET\s*=\s*([\d.]+);""").find(pbxproj)?.groupValues?.get(1) ?: "16.0"
		}

		data class BuildResult(val sample: String, val passed: Boolean, val reason: String? = null)
		val results = mutableListOf<BuildResult>()

		printSection("🧹 Cleaning all samples (--no-build-cache)")
		val cleanFailures = mutableSetOf<String>()
		for (sample in samples) {
			println("\n🧹 [$sample] Cleaning (no build cache)...")
			val cleanProcess = ProcessBuilder(file("$sample/gradlew").absolutePath, "clean", "--no-build-cache", "--no-daemon")
				.directory(file(sample))
				.redirectErrorStream(true)
				.start()
			cleanProcess.inputStream.bufferedReader().forEachLine { println("[$sample] $it") }
			val cleanExitCode = cleanProcess.waitFor()
			if (cleanExitCode != 0) {
				println("❌ [$sample] Clean failed — will be skipped in the build phase.")
				cleanFailures.add(sample)
				results.add(BuildResult(sample, false, "clean --no-build-cache failed with exit code $cleanExitCode"))
			}
		}

		printSection("⚙️  Pre-generating KMP frameworks / Swift export packages")
		val prepareFailures = mutableSetOf<String>()
		for (sample in samples) {
			if (sample in cleanFailures) continue

			println("\n⚙️  [$sample] Pre-generating (outside Xcode, so Package.swift is stable before xcodebuild runs)...")
			val embedTask = if (sample in swiftExportSamples) ":shared:embedSwiftExportForXcode" else ":shared:embedAndSignAppleFrameworkForXcode"
			val scratchDir = file("$sample/build/XcodeEmbedPrep/Debug-iphonesimulator")
			file("$scratchDir/Gradient.app").mkdirs()

			val prepareProcessBuilder = ProcessBuilder(file("$sample/gradlew").absolutePath, embedTask, "--no-daemon")
				.directory(file(sample))
				.redirectErrorStream(true)
			val env = prepareProcessBuilder.environment()
			env["CONFIGURATION"] = "Debug"
			env["SDK_NAME"] = "iphonesimulator$simulatorSdkVersion"
			env["PLATFORM_NAME"] = "iphonesimulator"
			env["ARCHS"] = "arm64"
			env["TARGET_BUILD_DIR"] = scratchDir.absolutePath
			env["BUILT_PRODUCTS_DIR"] = scratchDir.absolutePath
			env["FRAMEWORKS_FOLDER_PATH"] = "Gradient.app/Frameworks"
			env["UNLOCALIZED_RESOURCES_FOLDER_PATH"] = "Gradient.app"
			env["CONTENTS_FOLDER_PATH"] = "Gradient.app"
			env["EXECUTABLE_FOLDER_PATH"] = "Gradient.app"
			env.remove("EXPANDED_CODE_SIGN_IDENTITY")
			if (sample in swiftExportSamples) {
				env["DEPLOYMENT_TARGET_SETTING_NAME"] = "IPHONEOS_DEPLOYMENT_TARGET"
				env["IPHONEOS_DEPLOYMENT_TARGET"] = iosDeploymentTarget(sample)
			}

			val prepareProcess = prepareProcessBuilder.start()
			prepareProcess.inputStream.bufferedReader().forEachLine { println("[$sample] $it") }
			val prepareExitCode = prepareProcess.waitFor()
			if (prepareExitCode != 0) {
				println("❌ [$sample] Pre-generation failed — will be skipped in the build phase.")
				prepareFailures.add(sample)
				results.add(BuildResult(sample, false, "$embedTask failed with exit code $prepareExitCode"))
			}
		}

		printSection("🔨 Building all samples")
		for (sample in samples) {
			if (sample in cleanFailures || sample in prepareFailures) continue

			println("\n🔨 [$sample] Starting build...")

			val args = mutableListOf(
				"xcodebuild",
				"-project", file("$sample/iosApp/Gradient.xcodeproj").absolutePath,
				"-scheme", "Gradient",
				"-configuration", "Debug",
				"-derivedDataPath", file("$sample/build/DerivedData").absolutePath,
				"ARCHS=arm64",
				"CODE_SIGNING_ALLOWED=NO",
				"build"
			)
			if (sample in swiftExportSamples) {
				args.addAll(listOf("-destination", "id=$simulatorUDID"))
			} else {
				args.addAll(listOf("-sdk", "iphonesimulator"))
			}

			try {
				val pb = ProcessBuilder(args)
					.directory(file(sample))
					.redirectErrorStream(true)

				val process = pb.start()
				val outputLines = mutableListOf<String>()
				val reader = Thread {
					process.inputStream.bufferedReader().forEachLine { line ->
						println("[$sample] $line")
						outputLines.add(line)
					}
				}
				reader.start()
				val exitCode = process.waitFor()
				reader.join()

				if (exitCode != 0) {
					val errorLine = outputLines.lastOrNull { it.contains("error:", ignoreCase = true) }
						?: outputLines.lastOrNull { it.isNotBlank() }
						?: "exit code $exitCode"
					println("❌ [$sample] Build failed — continuing to next sample.")
					results.add(BuildResult(sample, false, errorLine.trim()))
				} else {
					println("✅ [$sample] Done!")
					results.add(BuildResult(sample, true))
				}
			} catch (e: Exception) {
				println("❌ [$sample] Exception during build — continuing to next sample.")
				results.add(BuildResult(sample, false, e.message ?: "Unknown exception"))
			}
		}

		printSection("📋 Build Summary")
		val passed = results.filter { it.passed }
		val failed = results.filter { !it.passed }
		if (passed.isNotEmpty()) {
			println("\n✅ Passed (${passed.size}):")
			passed.forEach { println("   • ${it.sample}") }
		}
		if (failed.isNotEmpty()) {
			println("\n❌ Failed (${failed.size}):")
			failed.forEach { println("   • ${it.sample}: ${it.reason}") }
		}
		println("\n" + "=".repeat(60))

		if (failed.isNotEmpty()) {
			throw GradleException("${failed.size} sample(s) failed to build. See summary above.")
		}
	}
}

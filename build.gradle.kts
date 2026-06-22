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
	version = "2.4.0-1.11.1-4"
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
	description = "Builds all samples via xcodebuild (full pipeline: KSP + plugin + framework), streaming output to this console"
	doLast {
		val samples = listOf(
 			"sample-objc-export",
 			"sample-objc-export-spm",
 			"sample-swift-export",
 			"sample-swift-export-spm"
		)

		val swiftExportSamples = setOf("sample-swift-export", "sample-swift-export-spm")

		val simulatorUDID: String by lazy {
			val proc = ProcessBuilder("xcrun", "simctl", "list", "devices", "available", "--json")
				.redirectErrorStream(true).start()
			val output = proc.inputStream.bufferedReader().readText()
			proc.waitFor()
			Regex(""""udid"\s*:\s*"([A-F0-9-]{36})"""").find(output)?.groupValues?.get(1)
				?: throw GradleException("No available iOS Simulator found. Install a simulator runtime in Xcode.")
		}

		data class BuildResult(val sample: String, val passed: Boolean, val reason: String? = null)
		val results = mutableListOf<BuildResult>()

		for (sample in samples) {
			println("\n🔨 [$sample] Starting build...")

			val args = mutableListOf(
				"xcodebuild",
				"-project", file("$sample/iosApp/Gradient.xcodeproj").absolutePath,
				"-scheme", "Gradient",
				"-configuration", "Debug",
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

		println("\n" + "=".repeat(60))
		println("📋 Build Summary")
		println("=".repeat(60))
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

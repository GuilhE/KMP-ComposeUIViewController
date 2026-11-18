plugins {
    alias(global.plugins.kotlin.multiplatform) apply false
    alias(local.plugins.compose.multiplatform) apply false
    alias(local.plugins.compose.compiler) apply false
}

private val xcodeConfiguration = providers.environmentVariable("CONFIGURATION").orElse("Debug").get()
require(xcodeConfiguration in listOf("Debug", "Release")) { "Unsupported CONFIGURATION: $xcodeConfiguration" }

// SwiftPM dependency paths can't vary per configuration, so the package is synced to a fixed folder.
private val syncSwiftPackageForXcode =
	tasks.register<Sync>("syncSwiftPackageForXcode") {
		description = "Syncs the exported Swift package for the current Xcode CONFIGURATION into SwiftPackage"
		val swiftPackages = layout.projectDirectory.dir("SwiftPackage")
		dependsOn("export${xcodeConfiguration}SwiftPackage")
		from(swiftPackages.dir(xcodeConfiguration))
	}

tasks.register("exportFrameworkForXcode") {
	description = "Exports the Swift package for Xcode (Debug/Release from CONFIGURATION, defaults to Debug)"
	dependsOn(syncSwiftPackageForXcode)
}
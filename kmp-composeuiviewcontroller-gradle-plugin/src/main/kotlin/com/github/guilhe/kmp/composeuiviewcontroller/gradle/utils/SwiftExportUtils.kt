@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension

internal data class SwiftExportConfig(val moduleName: String?, val flattenPackage: String?)

/**
 * The directory name receiving `export<BuildType>SwiftPackage`'s output (`exportDebugSwiftPackage`/
 * `exportReleaseSwiftPackage`), relative to the module's project directory. Matches the Kotlin Gradle
 * Plugin's own default for `export { swift { swiftPackageIntegration { } } }` (`outputDirectory` defaults
 * to `projectDirectory.dir("SwiftPackage")`), used here as the fallback when `outputDirectory` isn't
 * overridden in the build script.
 */
internal const val DEFAULT_SWIFT_PACKAGE_OUTPUT_DIRECTORY = "SwiftPackage"

internal data class SwiftPackageIntegrationConfig(val outputDirectoryName: String)

internal object SwiftExportUtils {

	/**
	 * Retrieves this project's `swiftPackageIntegration` configuration from its own `export { swift { } }`
	 * block, if declared. Like [getOwnNewDslSwiftExportConfig], this has no public typed API to read from, so
	 * it falls back to scanning the build script text. Unlike it, this scans the whole file rather than just
	 * the first `swift { }` match, since `swiftPackageIntegration { outputDirectory = ... }` can be nested
	 * past the point where a naive single-level brace match would stop.
	 */
	fun Project.getSwiftPackageIntegrationConfig(): SwiftPackageIntegrationConfig? {
		return try {
			if (!buildFile.exists()) return null
			val text = buildFile.readText()
			if (!Regex("""swiftPackageIntegration\s*[({]""").containsMatchIn(text)) return null
			val outputDirectoryName = Regex("""outputDirectory\s*=\s*layout\.projectDirectory\.dir\(\s*["']([^"']+)["']\s*\)""")
				.find(text)?.groupValues?.get(1)
				?: DEFAULT_SWIFT_PACKAGE_OUTPUT_DIRECTORY
			SwiftPackageIntegrationConfig(outputDirectoryName)
		} catch (e: Exception) {
			logger.warn("\t> Exception while parsing 'swiftPackageIntegration' config: ${e.message}")
			null
		}
	}

	/**
	 * Retrieves this project's own SwiftExport configuration when declared with the replacement
	 * `export { swift { } }` DSL (the legacy `swiftExport { }` DSL is read directly off
	 * [org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension] instead, since it exposes
	 * `moduleName`/`flattenPackage` as public properties).
	 *
	 * The replacement DSL's configuration (`ExportExtension.swiftExportConfiguration`) is internal to the Kotlin
	 * Gradle Plugin, so there is no typed API to read `moduleName`/`rootPackage` back from it. This falls back to
	 * scanning the build script text instead, the same way [getSwiftExportConfigForProject] already does for a
	 * module exported by a sibling project.
	 */
	fun Project.getOwnNewDslSwiftExportConfig(): SwiftExportConfig? {
		return try {
			if (!buildFile.exists()) return null
			val swiftBlock = Regex("""swift\s*\{([\s\S]*?)\}""").find(buildFile.readText())?.groupValues?.get(1) ?: return null
			val moduleName = Regex("""moduleName\s*=\s*["']([^"']+)["']""").find(swiftBlock)?.groupValues?.get(1) ?: return null
			val rootPackage = Regex("""rootPackage\s*=\s*["']([^"']+)["']""").find(swiftBlock)?.groupValues?.get(1)
			SwiftExportConfig(moduleName, rootPackage)
		} catch (e: Exception) {
			logger.warn("\t> Exception while parsing 'export { swift { } }' config: ${e.message}")
			null
		}
	}

	/**
	 * Retrieves the specific SwiftExport configuration for the current project.
	 * Since SwiftExport doesn't expose the exports collection, we need to look
	 * in the root project or parent projects for the SwiftExport configuration
	 * that references this project.
	 */
	fun Project.getSwiftExportConfigForProject(): SwiftExportConfig? {
		return try {
			logger.debug("\t> Looking for SwiftExport config that references project: ${this.name}")

			val rootSwiftExportConfig = rootProject.findSwiftExportConfigForProject(this)
			if (rootSwiftExportConfig != null) {
				return rootSwiftExportConfig
			}

			var currentProject = this.parent
			while (currentProject != null) {
				val parentSwiftExportConfig = currentProject.findSwiftExportConfigForProject(this)
				if (parentSwiftExportConfig != null) {
					return parentSwiftExportConfig
				}
				currentProject = currentProject.parent
			}

			rootProject.allprojects.forEach { project ->
				if (project != this) {
					val projectSwiftExportConfig = project.findSwiftExportConfigForProject(this)
					if (projectSwiftExportConfig != null) {
						return projectSwiftExportConfig
					}
				}
			}
			null
		} catch (e: Exception) {
			logger.warn("\t> Exception while searching for SwiftExport config: ${e.message}")
			null
		}
	}

	/**
	 * Looks for SwiftExport configuration in this project that references the target project
	 */
	private fun Project.findSwiftExportConfigForProject(targetProject: Project): SwiftExportConfig? {
		return try {
			val kmp = extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return null
			kmp.extensions.findByType(SwiftExportExtension::class.java) ?: return null

			if (!buildFile.exists()) return null

			val buildFileContent = buildFile.readText()

			// Extract the export blocks that reference a dependency, from either DSL:
			// - legacy: swiftExport { export(projects.something) { moduleName = ...; flattenPackage = ... } }
			// - replacement: export { swift { xcodeIntegration { configure(projects.something) { moduleName = ...; rootPackage = ... } } } }
			val patterns = listOf(
				Regex(
					"""export\s*\(\s*(?:projects\.([a-zA-Z0-9_-]+)|project\(\s*["']([^"']*)["']\s*\))\s*\)\s*\{([^}]*)\}""",
					RegexOption.DOT_MATCHES_ALL
				) to "flattenPackage",
				Regex(
					"""configure\s*\(\s*(?:projects\.([a-zA-Z0-9_-]+)|project\(\s*["']([^"']*)["']\s*\))\s*\)\s*\{([^}]*)\}""",
					RegexOption.DOT_MATCHES_ALL
				) to "rootPackage"
			)

			for ((exportPattern, packageProperty) in patterns) {
				for (match in exportPattern.findAll(buildFileContent)) {
					val projectNameWithAccessors = match.groupValues[1] // projects.sharedModels
					val projectNameDirect = match.groupValues[2] // shared-models (with or without :)
					val configBlock = match.groupValues[3] // the content inside the braces

					val projectName = projectNameWithAccessors.ifEmpty { projectNameDirect.removePrefix(":") }

					if (isProjectNameMatch(projectName, targetProject.name)) {
						val moduleNamePattern = Regex("""moduleName\s*=\s*["']([^"']+)["']""")
						val moduleNameMatch = moduleNamePattern.find(configBlock)
						val moduleName = moduleNameMatch?.groupValues?.get(1) ?: continue

						val packageValuePattern = Regex("""$packageProperty\s*=\s*["']([^"']+)["']""")
						val packageValueMatch = packageValuePattern.find(configBlock)
						val packageValue = packageValueMatch?.groupValues?.get(1)

						return SwiftExportConfig(moduleName, packageValue)
					}
				}
			}
			null
		} catch (_: Exception) {
			null
		}
	}

	/**
	 * Checks if the project name from the export configuration matches the target project name.
	 * Handles conversions between camelCase and kebab-case.
	 */
	private fun isProjectNameMatch(configProjectName: String, targetProjectName: String): Boolean {
		if (configProjectName == targetProjectName) return true

		val camelToKebab = configProjectName.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()
		if (camelToKebab == targetProjectName.lowercase()) return true

		val kebabToCamel = targetProjectName
			.split("-")
			.joinToString("") { segment -> segment.replaceFirstChar { it.uppercaseChar() } }
			.replaceFirstChar { it.lowercaseChar() }
		if (kebabToCamel == configProjectName) return true

		if (configProjectName.replace("-", "").equals(targetProjectName.replace("-", ""), ignoreCase = true)) return true

		return false
	}
}

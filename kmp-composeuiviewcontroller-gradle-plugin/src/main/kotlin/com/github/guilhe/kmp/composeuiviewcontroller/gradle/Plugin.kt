@file:Suppress("SpellCheckingInspection", "LoggingSimilarMessage")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.PackageResolver
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.SwiftExportUtils.getSwiftExportConfigForProject
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.configureTaskToFinalizeByCopyFilesToXcode
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.configureTaskToRegisterCopyFilesToXcode
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.configureTaskToRegisterExportToSpm
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.configureTaskToRegisterSetupSpmPackage
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.configureTaskToRegisterSwiftFormat
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils.configureTaskToRegisterValidateRepresentables
import com.google.devtools.ksp.gradle.KspExtension
import java.io.File
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginInstantiationException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension
import org.jetbrains.kotlin.konan.target.Family

public class PluginConfigurationException(message: String, cause: Throwable? = null) : GradleException(message, cause)

/**
 * Heavy lifts Gradle configurations when using [KMP-ComposeUIViewController](https://github.com/GuilhE/KMP-ComposeUIViewController) library.
 *
 * Exposes the following tasks under the `composeuiviewcontroller` group:
 * - `copyFilesToXcode` — syncs KSP-generated Swift Representables to `iosApp/Representables/` and
 *   updates the Xcode project references. Triggered automatically after `embedAndSignAppleFrameworkForXcode`,
 *   `embedSwiftExportForXcode`, or `syncFramework` when [PluginParameters.autoExport] is `true`.
 *   Not registered when [PluginParameters.experimentalSpmExport] is `true`.
 * - `exportToSpm` — experimental. Creates and maintains a local SPM package at `iosApp/Representables/`
 *   (folder names driven by [PluginParameters.iosAppFolderName] and [PluginParameters.exportFolderName]).
 *   Triggered automatically after `embedSwiftExportForXcode` when [PluginParameters.experimentalSpmExport] is `true`.
 *   Requires Swift Export to be configured.
 * - `formatSwiftFiles` — formats generated `.swift` files with `swiftformat` default rules (if available).
 * - `validateRepresentables` — inspects the full Representables pipeline without triggering a build.
 *   Useful for diagnosing why Xcode cannot find generated `UIViewControllerRepresentable` files.
 *   Run it with `./gradlew validateRepresentables`. Reports `OK`, `WARN`, or `FAIL` for each check:
 *
 *   | Check       | What it verifies                                       | Fails when                             |
 *   |-------------|--------------------------------------------------------|----------------------------------------|
 *   | KSP output  | `.swift` files exist in `build/generated/ksp/`         | KSP never ran or failed mid-generation |
 *   | Destination | `.swift` files exist in `iosApp/Representables/`       | `copyFilesToXcode` did not run         |
 *   | Sync        | KSP output and destination contain the same files      | Files are stale or missing             |
 *   | xcodeproj   | All Representables are referenced in `project.pbxproj` | `rebuild_file_references` failed       |
 *
 *   `WARN` entries (stale destination files, xcodeproj not found) do not fail the task.
 *   `FAIL` entries throw a [GradleException] listing all errors. The most common fix is `./gradlew clean`.
 *   The xcodeproj check is skipped when [PluginParameters.autoExport] is `false`.
 */
public class KmpComposeUIViewControllerPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		project.logger.info("> $LOG_TAG: Applying plugin to project '${project.name}'")
		with(project) {
			if (!plugins.hasPlugin(PLUGIN_KMP)) {
				throw PluginInstantiationException(ERROR_MISSING_KMP)
			}

			if (!plugins.hasPlugin(PLUGIN_KSP)) {
				logger.info("\t> Auto-applying KSP plugin")
				project.pluginManager.apply(PLUGIN_KSP)
			}

			val tempFolder = File(rootProject.layout.buildDirectory.asFile.get().path, TEMP_FILES_FOLDER).apply { mkdirs() }
			configureCleanTempFilesLogic(tempFolder)

			setupTargets()

			val extension = extensions.create(EXTENSION_PLUGIN, PluginParameters::class.java)
			configureTaskToRegisterSwiftFormat(project = project)
			configureTaskToRegisterValidateRepresentables(project = project, extensionParameters = extension)

			val packageResolver = PackageResolver(this, logger)
			project.afterEvaluate {
				try {
					validateExtensionParameters(extension)
					val packageNames = packageResolver.resolvePackages()
					val (frameworkNames, swiftExport, flattenPackage) = retrieveFrameworkBaseNamesFromIosTargets(packageNames)

					if (extension.experimentalSpmExport && !swiftExport) {
						throw PluginConfigurationException(
							"experimentalSpmExport requires Swift Export to be configured. " +
								"Add swiftExport { moduleName = \"...\" } to the KMP module."
						)
					}

					writeModuleMetadataToDisk(
						swiftExportEnabled = swiftExport,
						flattenPackageConfigured = flattenPackage,
						args = buildFrameworkPackages(packageNames, frameworkNames)
					)
					configureKspTasksForCacheInvalidation()

					if (extension.experimentalSpmExport) {
						configureTaskToRegisterSetupSpmPackage(
							project = project,
							extensionParameters = extension,
							spmModuleName = frameworkNames.first()
						)
						configureTaskToRegisterExportToSpm(
							project = project,
							extensionParameters = extension,
							tempFolder = tempFolder,
							spmModuleName = frameworkNames.first()
						)
						configureCleanSpmStub(extension)
					} else {
						configureTaskToRegisterCopyFilesToXcode(project = project, extensionParameters = extension, tempFolder = tempFolder)
					}
					configureTaskToFinalizeByCopyFilesToXcode(extension)
				} catch (e: PluginConfigurationException) {
					throw e
				} catch (e: Exception) {
					throw PluginConfigurationException("Failed to configure plugin: ${e.message}", e)
				}
			}
		}
	}

	private fun Project.configureCleanTempFilesLogic(tempFolder: File) {
		val cleanupTaskProvider = tasks.register(TASK_CLEAN_TEMP_FILES_FOLDER) { task ->
			task.group = "composeuiviewcontroller"
			task.description = "Cleans up temporary files created by the plugin"
			task.outputs.upToDateWhen { !tempFolder.exists() }
			task.doLast {
				if (tempFolder.exists()) {
					val deleted = tempFolder.deleteRecursively()
					logger.info("\n> $LOG_TAG:\n\t> Temp folder deleted: $deleted")
				}
			}
		}

		tasks.named("clean").configure { it.finalizedBy(cleanupTaskProvider) }

		// Note: This is deprecated but necessary until FlowProviders API is stable and available
		@Suppress("DEPRECATION")
		gradle.buildFinished { result ->
			if (result.failure != null && tempFolder.exists()) {
				val deleted = tempFolder.deleteRecursively()
				logger.info("\n> $LOG_TAG:\n\t> Build failed - Temp folder deleted: $deleted")
			}
		}
	}

	// Resets Package.swift to stub on clean so Xcode can open the project without resolution errors
	// even before the next build regenerates the full package.
	private fun Project.configureCleanSpmStub(extensionParameters: PluginParameters) {
		tasks.named(TASK_CLEAN_TEMP_FILES_FOLDER).configure { task ->
			task.doLast {
				val packageSwiftFile = rootDir.resolve(
					"${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}/Package.swift"
				)
				if (packageSwiftFile.exists()) {
					packageSwiftFile.writeText(stubPackageSwift(extensionParameters.exportFolderName))
					logger.info("\n> $LOG_TAG:\n\t> Package.swift reset to stub")
				}
			}
		}
	}

	private fun Project.validateExtensionParameters(parameters: PluginParameters) {
		require(parameters.iosAppFolderName.isNotBlank()) {
			"iosAppFolderName cannot be blank. Current value: '${parameters.iosAppFolderName}'"
		}
		require(parameters.iosAppName.isNotBlank()) {
			"iosAppName cannot be blank. Current value: '${parameters.iosAppName}'"
		}
		require(parameters.targetName.isNotBlank()) {
			"targetName cannot be blank. Current value: '${parameters.targetName}'"
		}
		require(parameters.exportFolderName.isNotBlank()) {
			"exportFolderName cannot be blank. Current value: '${parameters.exportFolderName}'"
		}
		logger.debug("\t> Extension parameters validated successfully")
	}

	private fun Project.setupTargets() {
		val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
		val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
		configurations.getByName(commonMainSourceSet.implementationConfigurationName).dependencies.apply {
			add(dependencies.create(LIB_ANNOTATION))
			logger.info("\t> Adding $LIB_ANNOTATION to ${commonMainSourceSet.implementationConfigurationName}")
		}

		kmp.targets.configureEach { target ->
			if (!target.fromIosFamily()) return@configureEach
			val kspConfigName = "ksp${target.targetName.replaceFirstChar { it.uppercaseChar() }}"
			dependencies.add(kspConfigName, LIB_KSP)
			logger.info("\t> Adding $LIB_KSP to $kspConfigName")
		}
	}

	private fun Project.retrieveFrameworkBaseNamesFromIosTargets(packageNames: Set<String>): Triple<Set<String>, Boolean, Boolean> {
		val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)

		// Priority 1: Framework baseName (Objective-C/Swift interoperability)
		val frameworkNames = mutableSetOf<String>()
		kmp.targets.configureEach { target ->
			if (target.fromIosFamily()) {
				(target as KotlinNativeTarget).binaries.withType(Framework::class.java).configureEach { framework ->
					frameworkNames += framework.baseName
				}
			}
		}
		if (frameworkNames.isNotEmpty()) {
			logger.info("\t> $INFO_MODULE_NAME_BY_FRAMEWORK $frameworkNames")
			return Triple(frameworkNames, false, false)
		}

		// Priority 2: SwiftExport for current Project
		val swiftExport = kmp.extensions.getByType(SwiftExportExtension::class.java)
		val moduleName = swiftExport.moduleName.orNull
		val flattenPackage = swiftExport.flattenPackage.orNull
		val flattenConfigured = flattenPackage?.let { flatten ->
			packageNames.any { it.startsWith("$flatten.") } || packageNames.contains(flatten)
		} ?: false
		if (flattenConfigured) {
			logger.info("\t> Info: flattenPackage '$flattenPackage' matches sourceSet.")
		}
		if (!moduleName.isNullOrBlank()) {
			logger.info("\t> $INFO_MODULE_NAME_BY_SWIFT_EXPORT [$moduleName]")
			return Triple(setOf(moduleName), true, flattenConfigured)
		}

		// Priority 3: SwiftExport in all Projects
		getSwiftExportConfigForProject()?.let { (moduleName, flattenPackage) ->
			if (!moduleName.isNullOrBlank()) {
				logger.info("\t> $INFO_MODULE_NAME_BY_SWIFT_EXPORT [$moduleName]")
				val flattenConfigured = flattenPackage?.let { flatten ->
					packageNames.any { it.startsWith("$flatten.") } || packageNames.contains(flatten)
				} ?: false
				if (flattenConfigured) {
					logger.info("\t> Info: flattenPackage '$flattenPackage' matches sourceSet.")
				} else {
					logger.warn("\t> Warning: flattenPackage '$flattenPackage' does NOT match sourceSet. Typealias will be generated")
				}
				return Triple(setOf(moduleName), true, flattenConfigured)
			}
		}

		throw PluginConfigurationException(ERROR_MISSING_FRAMEWORK_CONFIG_FULL)
	}

	private fun Project.buildFrameworkPackages(packageNames: Set<String>, frameworkNames: Set<String>): Map<String, Set<String>> {
		packageNames.ifEmpty { return emptyMap() }
		frameworkNames.ifEmpty { return emptyMap() }
		val frameworkBaseName = frameworkNames.first() // let's assume for now all targets will have the same frameworkBaseName
		val map = mutableMapOf<String, Set<String>>()
		extensions.getByType(KotlinMultiplatformExtension::class.java).run {
			targets.configureEach { target ->
				if (target.fromIosFamily()) {
					map[frameworkBaseName] = packageNames
				}
			}
		}
		return map
	}

	private fun Project.writeModuleMetadataToDisk(swiftExportEnabled: Boolean, flattenPackageConfigured: Boolean, args: Map<String, Set<String>>) {
		val file = rootProject.layout.buildDirectory.file("$TEMP_FILES_FOLDER/$FILE_NAME_ARGS").get().asFile
		file.parentFile?.mkdirs()

		val moduleMetadata = if (file.exists() && file.length() > 0) {
			try {
				Json.decodeFromString<MutableSet<ModuleMetadata>>(file.readText())
			} catch (e: Exception) {
				logger.warn("\t> Failed to parse existing metadata, starting fresh: ${e.message}")
				mutableSetOf()
			}
		} else {
			mutableSetOf()
		}

		val newMetadata = args.map { (key, value) ->
			ModuleMetadata(
				name = name,
				packageNames = value,
				frameworkBaseName = key,
				swiftExportEnabled = swiftExportEnabled,
				flattenPackageConfigured = flattenPackageConfigured
			)
		}

		moduleMetadata.removeIf { it.name == name }
		val changed = moduleMetadata.addAll(newMetadata)
		if (changed || !file.exists()) {
			file.writeText(Json.encodeToString(moduleMetadata))
			logger.debug("\t> Module metadata written to: ${file.absolutePath}")
		} else {
			logger.debug("\t> Module metadata unchanged, skipping write")
		}
	}

	/**
	 * Registers the shared metadata file ([FILE_NAME_ARGS]) as a declared Gradle input on every
	 * task whose name starts with "ksp", and propagates a content hash as a KSP processor argument.
	 *
	 * **Why this matters – two independent caching layers that can cause false UP-TO-DATE results:**
	 *
	 * 1. **Gradle task-level cache** – Gradle only re-runs a task when at least one of its *declared*
	 *    inputs has changed.  The metadata file is written at configuration time (in `afterEvaluate`)
	 *    and consumed by the KSP `Processor` at execution time, but it was never declared as a task input.
	 *    Registering it here makes Gradle aware of the file so the KSP task is considered
	 *    OUT-OF-DATE whenever the metadata content changes.
	 *
	 * 2. **KSP internal incremental layer** – Even after Gradle decides to run the KSP task, KSP's
	 *    own incremental engine may skip re-processing if no `.kt` source files changed.  Passing the
	 *    metadata content hash as a processor argument forces KSP to treat the options map as stale
	 *    (KSP serialises `apOptions` as part of its own input fingerprint), triggering a full
	 *    re-processing round whenever the metadata changes.
	 */
	private fun Project.configureKspTasksForCacheInvalidation() {
		val metadataFile = rootProject.layout.buildDirectory.file("$TEMP_FILES_FOLDER/$FILE_NAME_ARGS").get().asFile

		// Gradle level
		tasks.matching { it.name.startsWith("ksp") }.configureEach { task ->
			task.inputs.file(metadataFile).optional()
			logger.info("\t> Registered $FILE_NAME_ARGS as input for task '${task.name}'")
		}

		// KSP level
		if (metadataFile.exists()) {
			val metadataHash = metadataFile.readText().hashCode().toString()
			extensions.findByType(KspExtension::class.java)?.arg(KSP_ARG_METADATA_HASH, metadataHash)
			logger.info("\t> Passed metadata hash ($metadataHash) to KSP processor as '$KSP_ARG_METADATA_HASH'")
		}
	}

	private fun KotlinTarget.fromIosFamily(): Boolean = this is KotlinNativeTarget && konanTarget.family == Family.IOS
}
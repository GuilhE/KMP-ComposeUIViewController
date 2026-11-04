@file:Suppress("SpellCheckingInspection", "LoggingSimilarMessage")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils

import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_COPY_SCRIPT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_COPY_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_DELETE_SPM_SCRIPT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_DELETE_SPM_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_FORMAT_SCRIPT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_FORMAT_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_SETUP_SPM_SCRIPT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_SETUP_SPM_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_SPM_SCRIPT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_SPM_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_APP_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_GROUP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_IOS_DEPLOYMENT_TARGET
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_KEEP_FILE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_KMP_MODULE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_SPM_MODULE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_SWIFT_PACKAGE_DIR
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_SWIFT_TOOLS_VERSION
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_TARGET
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PluginConfigurationException
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PluginParameters
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_COPY_FILES_TO_XCODE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_DELETE_SPM_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EMBED_SWIFT_EXPORT_FOR_XCODE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EXPORT_DEBUG_SWIFT_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EXPORT_RELEASE_SWIFT_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EXPORT_TO_SPM
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_FORMAT_SWIFT_FILES
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_SETUP_SPM_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_SYNC_FRAMEWORK
import java.io.BufferedReader
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.Exec

/**
 * Task actions must not capture [Project] (directly or through its implicit receiver), otherwise they can't be
 * serialized by the Gradle Configuration Cache. Everything they need is therefore resolved here, at configuration
 * time, into plain values.
 *
 * The script is written to [scriptFile] right before the task runs (instead of at configuration time) so that a
 * configuration cache hit, which skips the configuration phase, still finds it.
 */
private fun Exec.configureScript(script: String, scriptFile: File) {
	doFirst {
		scriptFile.parentFile?.mkdirs()
		scriptFile.writeText(script)
		scriptFile.setExecutable(true)
	}
	commandLine("bash", "-c", scriptFile.absolutePath)
}

private fun readScriptResource(fileName: String): String =
	KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$fileName")?.use { stream ->
		stream.bufferedReader().use(BufferedReader::readText)
	} ?: throw PluginConfigurationException("Unable to read resource file: $fileName. Ensure the plugin is correctly packaged.")

private fun Project.tempScriptFile(fileName: String): File =
	File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/$fileName")

private fun String.withCommonParameters(moduleName: String, extensionParameters: PluginParameters): String = this
	.replace("${PARAM_KMP_MODULE}=\"shared\"", "${PARAM_KMP_MODULE}=\"$moduleName\"")
	.replace("${PARAM_FOLDER}=\"iosApp\"", "${PARAM_FOLDER}=\"${extensionParameters.iosAppFolderName}\"")
	.replace("${PARAM_APP_NAME}=\"iosApp\"", "${PARAM_APP_NAME}=\"${extensionParameters.iosAppName}\"")
	.replace("${PARAM_TARGET}=\"iosApp\"", "${PARAM_TARGET}=\"${extensionParameters.targetName}\"")
	.replace("${PARAM_GROUP}=\"Representables\"", "${PARAM_GROUP}=\"${extensionParameters.exportFolderName}\"")

internal fun Project.configureTaskToRegisterSwiftFormat(project: Project) {
	val moduleName = project.name
	val workingDirectory = project.rootDir
	val tempFile = tempScriptFile(FILE_NAME_FORMAT_SCRIPT_TEMP)
	val script = readScriptResource(FILE_NAME_FORMAT_SCRIPT)
		.replace("${PARAM_KMP_MODULE}=\"shared\"", "${PARAM_KMP_MODULE}=\"$moduleName\"")

	tasks.register(TASK_FORMAT_SWIFT_FILES, Exec::class.java) { task ->
		task.group = "composeuiviewcontroller"
		task.description = "Formats generated Swift files using swiftformat"
		task.outputs.upToDateWhen { false } // Always execute this task - don't cache it
		task.doFirst { execTask -> execTask.logger.info("\t> Formatting Swift files for module: $moduleName") }
		task.workingDir = workingDirectory
		task.configureScript(script, tempFile)
		task.doLast { tempFile.delete() }
	}
}

internal fun Project.configureTaskToRegisterCopyFilesToXcode(
	project: Project,
	extensionParameters: PluginParameters
) {
	val moduleName = project.name
	val workingDirectory = project.rootDir
	val keepScriptFile = project.hasProperty(PARAM_KEEP_FILE) && project.property(PARAM_KEEP_FILE) == "true"
	val tempFile = tempScriptFile(FILE_NAME_COPY_SCRIPT_TEMP)
	val script = readScriptResource(FILE_NAME_COPY_SCRIPT).withCommonParameters(moduleName, extensionParameters)
	val parameters = extensionParameters.toList()

	tasks.register(TASK_COPY_FILES_TO_XCODE, Exec::class.java) { task ->
		task.group = "composeuiviewcontroller"
		task.description = "Copies generated files to Xcode project"
		task.outputs.upToDateWhen { false } // Always execute this task - don't cache it
		task.doFirst { execTask -> execTask.logger.info("\t> Extension parameters: $parameters") }
		task.workingDir = workingDirectory
		task.configureScript(script, tempFile)
		if (!keepScriptFile) {
			// Only the script: modules.json, in the same folder, is an input of the ksp* tasks and of the Configuration Cache
			task.doLast { tempFile.delete() }
		}
	}
}

internal fun Project.configureTaskToRegisterCreateRepresentablesPackage(
	project: Project,
	extensionParameters: PluginParameters,
	spmModuleName: String
) {
	val moduleName = project.name
	val workingDirectory = project.rootDir
	val tempFile = tempScriptFile(FILE_NAME_SETUP_SPM_SCRIPT_TEMP)
	val script = readScriptResource(FILE_NAME_SETUP_SPM_SCRIPT)
		.withCommonParameters(moduleName, extensionParameters)
		.replace("${PARAM_SPM_MODULE}=\"Composables\"", "${PARAM_SPM_MODULE}=\"$spmModuleName\"")
		.replace("${PARAM_SWIFT_TOOLS_VERSION}=\"6.2\"", "${PARAM_SWIFT_TOOLS_VERSION}=\"${extensionParameters.swiftToolsVersion}\"")

	tasks.register(TASK_SETUP_SPM_PACKAGE, Exec::class.java) { task ->
		task.group = "composeuiviewcontroller"
		task.description = "One-time setup: creates the local SPM package stub and adds it to the Xcode project. Re-run after ./gradlew clean."
		task.outputs.upToDateWhen { false }
		task.doFirst { execTask -> execTask.logger.info("\t> Setting up SPM package for module: $moduleName") }
		task.workingDir = workingDirectory
		task.configureScript(script, tempFile)
		task.doLast { tempFile.delete() }
	}
}

internal fun Project.configureTaskToRegisterDeleteRepresentablesPackage(
	project: Project,
	extensionParameters: PluginParameters
) {
	val moduleName = project.name
	val workingDirectory = project.rootDir
	val tempFile = tempScriptFile(FILE_NAME_DELETE_SPM_SCRIPT_TEMP)
	val script = readScriptResource(FILE_NAME_DELETE_SPM_SCRIPT).withCommonParameters(moduleName, extensionParameters)

	tasks.register(TASK_DELETE_SPM_PACKAGE, Exec::class.java) { task ->
		task.group = "composeuiviewcontroller"
		task.description =
			"Removes the local SPM package and its Xcode project reference. Run setupRepresentablesSpmPackage to set up again."
		task.outputs.upToDateWhen { false }
		task.doFirst { execTask -> execTask.logger.info("\t> Deleting Representables SPM package for module: $moduleName") }
		task.workingDir = workingDirectory
		task.configureScript(script, tempFile)
		task.doLast { tempFile.delete() }
	}
}

internal fun Project.configureTaskToRegisterExportToSpm(
	project: Project,
	extensionParameters: PluginParameters,
	spmModuleName: String,
	swiftPackageIntegration: SwiftPackageIntegrationConfig?
) {
	val moduleName = project.name
	val workingDirectory = project.rootDir
	val keepScriptFile = project.hasProperty(PARAM_KEEP_FILE) && project.property(PARAM_KEEP_FILE) == "true"
	val tempFile = tempScriptFile(FILE_NAME_SPM_SCRIPT_TEMP)
	val parameters = extensionParameters.toSpmList()
	val script = readScriptResource(FILE_NAME_SPM_SCRIPT)
		.withCommonParameters(moduleName, extensionParameters)
		.replace("${PARAM_IOS_DEPLOYMENT_TARGET}=\"26\"", "${PARAM_IOS_DEPLOYMENT_TARGET}=\"${extensionParameters.iosDeploymentTarget}\"")
		.replace("${PARAM_SWIFT_TOOLS_VERSION}=\"6.2\"", "${PARAM_SWIFT_TOOLS_VERSION}=\"${extensionParameters.swiftToolsVersion}\"")
		.replace("${PARAM_SPM_MODULE}=\"__SPM_MODULE_NAME__\"", "${PARAM_SPM_MODULE}=\"$spmModuleName\"")
		.replace(
			"${PARAM_SWIFT_PACKAGE_DIR}=\"SwiftPackage\"",
			"${PARAM_SWIFT_PACKAGE_DIR}=\"${swiftPackageIntegration?.outputDirectoryName ?: "SwiftPackage"}\""
		)

	tasks.register(TASK_EXPORT_TO_SPM, Exec::class.java) { task ->
		task.group = "composeuiviewcontroller"
		task.description = "Creates and maintains a local SPM package with KSP-generated Swift Representables"
		task.outputs.upToDateWhen { false }
		task.doFirst { execTask -> execTask.logger.info("\t> Extension parameters: $parameters") }

		// The task graph can't be queried while the task runs (Configuration Cache), so the environment is resolved
		// as soon as the graph is ready (which is part of the cached state).
		gradle.taskGraph.whenReady { graph ->
			val taskNames = graph.allTasks.map { it.name }.toSet()

			// Only the real embedAndSignAppleFrameworkForXcode/embedSwiftExportForXcode/syncFramework/
			// exportDebugSwiftPackage/exportReleaseSwiftPackage finalizer chain can race with the KMP build
			// output it depends on — a standalone/manual/CI invocation (e.g. this task run directly) has no
			// such race, so the script must not apply its build-output wait/fail gate in that case.
			val isFinalizerRun = taskNames.any {
				it in setOf(
					TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE,
					TASK_EMBED_SWIFT_EXPORT_FOR_XCODE,
					TASK_SYNC_FRAMEWORK,
					TASK_EXPORT_DEBUG_SWIFT_PACKAGE,
					TASK_EXPORT_RELEASE_SWIFT_PACKAGE
				)
			}
			task.environment("XCODE_BUILD_FINALIZER_RUN", isFinalizerRun.toString())

			// exportDebugSwiftPackage/exportReleaseSwiftPackage run outside an Xcode build, so there's no
			// $CONFIGURATION env var from Xcode telling the script which output folder (SwiftPackage/Debug vs
			// /Release) to read — the triggering task's own name already says exactly that.
			if (TASK_EXPORT_RELEASE_SWIFT_PACKAGE in taskNames) {
				task.environment("CONFIGURATION", "Release")
			} else if (TASK_EXPORT_DEBUG_SWIFT_PACKAGE in taskNames) {
				task.environment("CONFIGURATION", "Debug")
			}
		}

		task.workingDir = workingDirectory
		task.configureScript(script, tempFile)
		if (!keepScriptFile) {
			// Only the script: modules.json, in the same folder, is an input of the ksp* tasks and of the Configuration Cache
			task.doLast { tempFile.delete() }
		}
	}
}

internal fun Project.configureTaskToFinalizeByCopyFilesToXcode(extensionParameters: PluginParameters) {
	val exportTask = if (extensionParameters.legacyMode) TASK_COPY_FILES_TO_XCODE else TASK_EXPORT_TO_SPM
	val triggerTasks = listOf(
		TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE,
		TASK_EMBED_SWIFT_EXPORT_FOR_XCODE,
		TASK_SYNC_FRAMEWORK,
		TASK_EXPORT_DEBUG_SWIFT_PACKAGE,
		TASK_EXPORT_RELEASE_SWIFT_PACKAGE
	)
	tasks.matching { it.name in triggerTasks }.configureEach { task ->
		if (extensionParameters.autoExport) {
			logger.info(
				"\n> " +
					"KmpComposeUIViewControllerPlugin:\n\t> Task '${task.name}' will be finalized by '${TASK_FORMAT_SWIFT_FILES}' -> '$exportTask'"
			)
			task.finalizedBy(TASK_FORMAT_SWIFT_FILES)
		}
	}
	tasks.named(TASK_FORMAT_SWIFT_FILES).configure {
		it.finalizedBy(exportTask)
	}
}

internal fun PluginParameters.toList() =
	listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)

internal fun PluginParameters.toSpmList() =
	listOf(iosAppFolderName, exportFolderName, autoExport, legacyMode)
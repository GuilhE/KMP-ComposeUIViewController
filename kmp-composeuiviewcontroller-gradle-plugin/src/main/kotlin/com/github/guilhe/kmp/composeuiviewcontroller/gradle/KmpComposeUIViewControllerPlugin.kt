@file:Suppress("SpellCheckingInspection", "LoggingSimilarMessage")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.SwiftExportUtils.getSwiftExportConfigForProject
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension
import org.jetbrains.kotlin.konan.target.Family
import java.io.BufferedReader
import java.io.File

public class PluginConfigurationException(message: String, cause: Throwable? = null) : GradleException(message, cause)

/**
 * Heavy lifts gradle configurations when using [KMP-ComposeUIViewController](https://github.com/GuilhE/KMP-ComposeUIViewController) library.
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

            val extension = extensions.create(EXTENSION_PLUGIN, ComposeUiViewControllerParameters::class.java)
            with(extension) {
                configureTaskToRegisterCopyFilesToXcode(project = project, extensionParameters = this, tempFolder = tempFolder)
                configureTaskToFinalizeByCopyFilesToXcode(this)
            }

            val packageResolver = PackageResolver(this, logger)
            project.afterEvaluate {
                try {
                    validateExtensionParameters(extension)
                    val packageNames = packageResolver.resolvePackages()
                    val (frameworkNames, swiftExport, flattenPackage) = retrieveFrameworkBaseNamesFromIosTargets(packageNames)
                    writeModuleMetadataToDisk(
                        swiftExportEnabled = swiftExport,
                        flattenPackageConfigured = flattenPackage,
                        args = buildFrameworkPackages(packageNames, frameworkNames)
                    )
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
            task.inputs.dir(tempFolder).optional()
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

    private fun Project.validateExtensionParameters(parameters: ComposeUiViewControllerParameters) {
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

        // Priority 4: Project name (fallback)
        val projectModuleName = name.toPascalCase()
        logger.info("\t> $INFO_MODULE_NAME_BY_PROJECT [$projectModuleName]")
        return Triple(setOf(projectModuleName), true, flattenConfigured)
    }

    private fun Project.buildFrameworkPackages(packageNames: Set<String>, frameworkNames: Set<String>): Map<String, Set<String>> {
        packageNames.ifEmpty { return emptyMap() }
        frameworkNames.ifEmpty { return emptyMap() }
        val frameworkBaseName = frameworkNames.first() //let's assume for now all targets will have the same frameworkBaseName
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

        val changed = moduleMetadata.addAll(newMetadata)
        if (changed || !file.exists()) {
            file.writeText(Json.encodeToString(moduleMetadata))
            logger.debug("\t> Module metadata written to: ${file.absolutePath}")
        } else {
            logger.debug("\t> Module metadata unchanged, skipping write")
        }
    }

    private fun Project.configureTaskToRegisterCopyFilesToXcode(
        project: Project,
        extensionParameters: ComposeUiViewControllerParameters,
        tempFolder: File
    ) {
        tasks.register(TASK_COPY_FILES_TO_XCODE, Exec::class.java) { task ->
            task.group = "composeuiviewcontroller"
            task.description = "Copies generated files to Xcode project"
            task.outputs.upToDateWhen { false } // Always execute this task - don't cache it
            task.doFirst { logger.info("\t> Extension parameters: ${extensionParameters.toList()}") }

            val keepScriptFile = project.hasProperty(PARAM_KEEP_FILE) && project.property(PARAM_KEEP_FILE) == "true"
            val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$FILE_NAME_SCRIPT")
            val script = inputStream?.use { stream ->
                stream.bufferedReader().use(BufferedReader::readText)
            } ?: throw PluginConfigurationException("Unable to read resource file: $FILE_NAME_SCRIPT. Ensure the plugin is correctly packaged.")

            val modifiedScript = script
                .replace("$PARAM_KMP_MODULE=\"shared\"", "$PARAM_KMP_MODULE=\"${project.name}\"")
                .replace("$PARAM_FOLDER=\"iosApp\"", "$PARAM_FOLDER=\"${extensionParameters.iosAppFolderName}\"")
                .replace("$PARAM_APP_NAME=\"iosApp\"", "$PARAM_APP_NAME=\"${extensionParameters.iosAppName}\"")
                .replace("$PARAM_TARGET=\"iosApp\"", "$PARAM_TARGET=\"${extensionParameters.targetName}\"")
                .replace("$PARAM_GROUP=\"Representables\"", "$PARAM_GROUP=\"${extensionParameters.exportFolderName}\"")

            val tempFile = File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/${FILE_NAME_SCRIPT_TEMP}")
                .also { it.createNewFile() }

            if (tempFile.exists()) {
                tempFile.writeText(modifiedScript)
                tempFile.setExecutable(true)
                task.workingDir = project.rootDir

                try {
                    task.commandLine("bash", "-c", tempFile.absolutePath)
                    if (!keepScriptFile) {
                        task.doLast {
                            if (tempFolder.exists()) {
                                tempFolder.deleteRecursively()
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw PluginConfigurationException(
                        "Failed to configure script execution for task '$TASK_COPY_FILES_TO_XCODE'. Script path: ${tempFile.absolutePath}",
                        e
                    )
                }
            } else {
                throw PluginConfigurationException("Failed to create temporary script file: $FILE_NAME_SCRIPT_TEMP at ${tempFile.absolutePath}")
            }
        }
    }

    private fun Project.configureTaskToFinalizeByCopyFilesToXcode(extensionParameters: ComposeUiViewControllerParameters) {
        tasks.matching {
            it.name == TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE ||
                    it.name == TASK_EMBED_SWIFT_EXPORT_FOR_XCODE ||
                    it.name == TASK_SYNC_FRAMEWORK
        }.configureEach { task ->
            if (extensionParameters.autoExport) {
                logger.info("\n> $LOG_TAG:\n\t> Task '${task.name}' will be finalized by '$TASK_COPY_FILES_TO_XCODE' task")
                task.finalizedBy(TASK_COPY_FILES_TO_XCODE)
            }
        }
    }

    private fun String.toPascalCase(): String {
        return split("-")
            .joinToString("") { segment ->
                segment.replaceFirstChar { it.uppercaseChar() }
            }
    }

    private fun KotlinTarget.fromIosFamily(): Boolean = this is KotlinNativeTarget && konanTarget.family == Family.IOS

    private fun ComposeUiViewControllerParameters.toList() =
        listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)

    internal companion object {
        private const val VERSION_LIBRARY = "2.3.20-Beta1-1.10.0"
        private const val LOG_TAG = "KmpComposeUIViewControllerPlugin"
        internal const val PLUGIN_KMP = "org.jetbrains.kotlin.multiplatform"
        internal const val PLUGIN_KSP = "com.google.devtools.ksp"
        internal const val LIB_GROUP = "com.github.guilhe.kmp"
        internal const val LIB_KSP_NAME = "kmp-composeuiviewcontroller-ksp"
        private const val LIB_KSP = "$LIB_GROUP:$LIB_KSP_NAME:$VERSION_LIBRARY"
        internal const val LIB_ANNOTATIONS_NAME = "kmp-composeuiviewcontroller-annotations"
        private const val LIB_ANNOTATION = "$LIB_GROUP:$LIB_ANNOTATIONS_NAME:$VERSION_LIBRARY"
        private const val EXTENSION_PLUGIN = "ComposeUiViewController"
        internal const val TASK_CLEAN_TEMP_FILES_FOLDER = "cleanTempFilesFolder"
        internal const val TASK_COPY_FILES_TO_XCODE = "copyFilesToXcode"
        internal const val TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE = "embedAndSignAppleFrameworkForXcode"
        internal const val TASK_EMBED_SWIFT_EXPORT_FOR_XCODE = "embedSwiftExportForXcode"
        internal const val TASK_SYNC_FRAMEWORK = "syncFramework"
        private const val FILE_NAME_SCRIPT = "exportToXcode.sh"
        internal const val FILE_NAME_SCRIPT_TEMP = "temp.sh"
        internal const val PARAM_KEEP_FILE = "keepScriptFile"
        internal const val PARAM_KMP_MODULE = "kmp_module"
        internal const val PARAM_FOLDER = "iosApp_project_folder"
        internal const val PARAM_APP_NAME = "iosApp_name"
        internal const val PARAM_TARGET = "iosApp_target_name"
        internal const val PARAM_GROUP = "group_name"
        internal const val ERROR_MISSING_KMP = "$LOG_TAG requires the Kotlin Multiplatform plugin to be applied."
        internal const val ERROR_MISSING_PACKAGE = "Could not determine project's package"
        internal const val INFO_MODULE_NAME_BY_FRAMEWORK =
            "SwiftExport is NOT configured, will use all iOS targets' framework baseName as frameworkBaseName:"
        internal const val INFO_MODULE_NAME_BY_SWIFT_EXPORT = "SwiftExport is configured, will use its moduleName:"
        internal const val INFO_MODULE_NAME_BY_PROJECT = "No configurations found for moduleName. Fallback to project module name:"
    }
}
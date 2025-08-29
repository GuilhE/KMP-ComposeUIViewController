@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.SwiftExportUtils.getSwiftExportConfigForProject
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
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

/**
 * Heavy lifts gradle configurations when using [KMP-ComposeUIViewController](https://github.com/GuilhE/KMP-ComposeUIViewController) library.
 */
public class KmpComposeUIViewControllerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            if (!plugins.hasPlugin(PLUGIN_KMP)) {
                throw PluginInstantiationException(ERROR_MISSING_KMP)
            }

            if (!plugins.hasPlugin(PLUGIN_KSP)) {
                throw PluginInstantiationException(ERROR_MISSING_KSP)
            }

            val tempFolder = File(rootProject.layout.buildDirectory.asFile.get().path, TEMP_FILES_FOLDER).apply { mkdirs() }
            configureCleanTempFilesLogic(tempFolder)

            println("> $LOG_TAG:")
            setupTargets()
            with(extensions.create(EXTENSION_PLUGIN, ComposeUiViewControllerParameters::class.java)) {
                configureTaskToRegisterCopyFilesToXcode(project = project, extensionParameters = this, tempFolder = tempFolder)
                configureTaskToFinalizeByCopyFilesToXcode(this)
                project.afterEvaluate {
                    val packageNames = retrieveModulePackagesFromCommonMain()
                    val (frameworkNames, swiftExport, flattenPackage) = retrieveFrameworkBaseNamesFromIosTargets(packageNames)
                    writeModuleMetadataToDisk(
                        swiftExportEnabled = swiftExport,
                        flattenPackageConfigured = flattenPackage,
                        args = buildFrameworkPackages(packageNames, frameworkNames)
                    )
                }
            }
        }
    }

    private fun Project.configureCleanTempFilesLogic(tempFolder: File) {
        tasks.register(TASK_CLEAN_TEMP_FILES_FOLDER) { it.doLast { deleteTempFolder(tempFolder) } }
        tasks.named("clean").configure { it.finalizedBy(TASK_CLEAN_TEMP_FILES_FOLDER) }
        gradle.addBuildListener(object : org.gradle.BuildListener {
            override fun settingsEvaluated(settings: org.gradle.api.initialization.Settings) {}
            override fun projectsLoaded(gradle: Gradle) {}
            override fun projectsEvaluated(gradle: Gradle) {}
            override fun buildFinished(result: org.gradle.BuildResult) {
                if (result.failure != null) {
                    deleteTempFolder(tempFolder)
                }
            }
        })
    }

    private fun deleteTempFolder(folder: File) {
        if (folder.exists()) {
            println("\n> $LOG_TAG:\n\t> Temp folder deleted: ${folder.deleteRecursively()}")
        } else {
            println("\n> $LOG_TAG:\n\t> Temp folder already deleted")
        }
    }

    private fun Project.setupTargets() {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        configurations.getByName(commonMainSourceSet.implementationConfigurationName).dependencies.apply {
            add(dependencies.create(LIB_ANNOTATION))
            println("\t> Adding $LIB_ANNOTATION to ${commonMainSourceSet.implementationConfigurationName}")
        }

        kmp.targets.configureEach { target ->
            if (!target.fromIosFamily()) return@configureEach
            val kspConfigName = "ksp${target.targetName.replaceFirstChar { it.uppercaseChar() }}"
            dependencies.add(kspConfigName, LIB_KSP)
            println("\t> Adding $LIB_KSP to $kspConfigName")
        }
    }

    private fun Project.retrieveFrameworkBaseNamesFromIosTargets(packageNames: Set<String>): Triple<Set<String>, Boolean, Boolean> {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)

        // Priority 1: Framework baseName (Obcjective-C/Swift interoperability)
        val frameworkNames = mutableSetOf<String>()
        kmp.targets.configureEach { target ->
            if (target.fromIosFamily()) {
                (target as KotlinNativeTarget).binaries.withType(Framework::class.java).configureEach { framework ->
                    frameworkNames += framework.baseName
                }
            }
        }
        if (frameworkNames.isNotEmpty()) {
            println("\t> $INFO_MODULE_NAME_BY_FRAMEWORK $frameworkNames")
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
            println("\t> Info: flattenPackage '$flattenPackage' matches sourceSet.")
        }
        if (!moduleName.isNullOrBlank()) {
            println("\t> $INFO_MODULE_NAME_BY_SWIFT_EXPORT [$moduleName]")
            return Triple(setOf(moduleName), true, flattenConfigured)
        }

        // Priority 3: SwiftExport in all Projects
        getSwiftExportConfigForProject()?.let { (moduleName, flattenPackage) ->
            if (!moduleName.isNullOrBlank()) {
                println("\t> $INFO_MODULE_NAME_BY_SWIFT_EXPORT [$moduleName]")
                val flattenConfigured = flattenPackage?.let { flatten ->
                    packageNames.any { it.startsWith("$flatten.") } || packageNames.contains(flatten)
                } ?: false
                if (flattenConfigured) {
                    println("\t> Info: flattenPackage '$flattenPackage' matches sourceSet.")
                } else {
                    println("\t> Warning: flattenPackage '$flattenPackage' does NOT match sourceSet. Typealias will be generated")
                }
                return Triple(setOf(moduleName), true, flattenConfigured)
            }
        }

        // Priority 4: Project name (fallback)
        val projectModuleName = name.toPascalCase()
        println("\t> $INFO_MODULE_NAME_BY_PROJECT [$projectModuleName]")
        return Triple(setOf(projectModuleName), true, flattenConfigured)
    }

    private fun Project.retrieveModulePackagesFromCommonMain(): Set<String> {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        val packages = mutableSetOf<String>()
        commonMainSourceSet.kotlin.srcDirs.forEach { dir ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    val relativePath = file.relativeTo(dir).parentFile?.path ?: ""
                    val packagePath = relativePath.replace(File.separator, ".")
                    if (packagePath.isNotEmpty()) {
                        packages.add(packagePath)
                    }
                }
            }
        }
        println("\t> $INFO_MODULE_PACKAGES $packages")
        return packages.ifEmpty { throw GradleException(ERROR_MISSING_PACKAGE) }
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
        val moduleMetadata = try {
            Json.decodeFromString<MutableSet<ModuleMetadata>>(file.readText())
        } catch (_: Exception) {
            mutableSetOf()
        }
        args.forEach { (key, value) ->
            moduleMetadata.add(
                ModuleMetadata(
                    name = name,
                    packageNames = value,
                    frameworkBaseName = key,
                    swiftExportEnabled = swiftExportEnabled,
                    flattenPackageConfigured = flattenPackageConfigured
                )
            )
        }
        file.writeText(Json.encodeToString(moduleMetadata))
    }

    private fun Project.configureTaskToRegisterCopyFilesToXcode(
        project: Project,
        extensionParameters: ComposeUiViewControllerParameters,
        tempFolder: File
    ) {
        tasks.register(TASK_COPY_FILES_TO_XCODE, Exec::class.java) { task ->
            val keepScriptFile = project.hasProperty(PARAM_KEEP_FILE) && project.property(PARAM_KEEP_FILE) == "true"
            println("\t> parameters: ${extensionParameters.toList()}")
            val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$FILE_NAME_SCRIPT")
            val script = inputStream?.use { stream ->
                stream.bufferedReader().use(BufferedReader::readText)
            } ?: throw GradleException("Unable to read resource file")

            val modifiedScript = script
                .replace(
                    oldValue = "$PARAM_KMP_MODULE=\"shared\"",
                    newValue = "$PARAM_KMP_MODULE=\"${project.name}\""
                )
                .replace(
                    oldValue = "$PARAM_FOLDER=\"iosApp\"",
                    newValue = "$PARAM_FOLDER=\"${extensionParameters.iosAppFolderName}\""
                )
                .replace(
                    oldValue = "$PARAM_APP_NAME=\"iosApp\"",
                    newValue = "$PARAM_APP_NAME=\"${extensionParameters.iosAppName}\""
                )
                .replace(
                    oldValue = "$PARAM_TARGET=\"iosApp\"",
                    newValue = "$PARAM_TARGET=\"${extensionParameters.targetName}\""
                )
                .replace(
                    oldValue = "$PARAM_GROUP=\"Representables\"",
                    newValue = "$PARAM_GROUP=\"${extensionParameters.exportFolderName}\""
                )

            val tempFile = File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/${FILE_NAME_SCRIPT_TEMP}")
                .also { it.createNewFile() }
            if (tempFile.exists()) {
                with(tempFile) {
                    writeText(modifiedScript)
                    setExecutable(true)
                    task.workingDir = project.rootDir
                    try {
                        task.commandLine("bash", "-c", tempFile.absolutePath)
                        if (!keepScriptFile) {
                            task.doLast { deleteTempFolder(tempFolder) }
                        }
                    } catch (e: Exception) {
                        println("\t> Error running script: ${e.message}")
                    }
                }
            } else {
                println("\t> Error creating $FILE_NAME_SCRIPT_TEMP")
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
                println("\n> $LOG_TAG:\n\t> ${task.name} will be finalizedBy $TASK_COPY_FILES_TO_XCODE task")
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
        private const val VERSION_LIBRARY = "2.3.0-dev-4778-1.9.0-beta03-1"
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
        internal const val ERROR_MISSING_KSP = "$LOG_TAG requires the KSP plugin to be applied."
        internal const val INFO_MODULE_PACKAGES = "Module packages found:"
        internal const val ERROR_MISSING_PACKAGE = "Cloud not determine project's package"
        internal const val INFO_MODULE_NAME_BY_FRAMEWORK =
            "SwiftExport is NOT configured, will use all iOS targets' framework baseName as frameworkBaseName:"
        internal const val INFO_MODULE_NAME_BY_SWIFT_EXPORT = "SwiftExport is configured, will use its moduleName:"
        internal const val INFO_MODULE_NAME_BY_PROJECT = "No configurations found for moduleName. Fallback to project module name:"
    }
}
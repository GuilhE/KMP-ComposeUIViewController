@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
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

    private fun KotlinTarget.fromIosFamily(): Boolean = this is KotlinNativeTarget && konanTarget.family == Family.IOS

    private fun ComposeUiViewControllerParameters.toList() =
        listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)

    override fun apply(project: Project) {
        with(project) {
            if (!plugins.hasPlugin(PLUGIN_KMP)) {
                throw PluginInstantiationException(ERROR_MISSING_KMP)
            }

            if (!plugins.hasPlugin(PLUGIN_KSP)) {
                throw PluginInstantiationException(ERROR_MISSING_KSP)
            }

            val tempFolder = File(rootProject.layout.buildDirectory.asFile.get().path, TEMP_FILES_FOLDER).apply { mkdirs() }
            registerCleanTempFilesFolderTask(tempFolder)

            println("> $LOG_TAG:")
            setupTargets()
            with(extensions.create(EXTENSION_PLUGIN, ComposeUiViewControllerParameters::class.java)) {
                registerCopyFilesToXcodeTask(project, this)
                finalizeFrameworkTasks(this)
                afterEvaluate {
                    writeModuleMetadataToDisk(
                        args = buildFrameworkPackages(
                            retrieveModulePackagesFromCommonMain(),
                            retrieveFrameworkBaseNamesFromIosTargets(this)
                        )
                    )
                }
            }
        }
    }

    private fun Project.registerCleanTempFilesFolderTask(tempFolder: File) {
        tasks.register(TASK_CLEAN_TEMP_FILES_FOLDER) {
            it.doLast {
                if (tempFolder.exists()) {
                    println("\n> $LOG_TAG:\n\t> Temp folder deleted: ${tempFolder.deleteRecursively()}")
                } else {
                    println("\n> $LOG_TAG:\n\t> Temp folder already deleted")
                }
            }
        }
        tasks.named("clean").configure { it.finalizedBy(TASK_CLEAN_TEMP_FILES_FOLDER) }
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

    private fun Project.retrieveFrameworkBaseNamesFromIosTargets(extensionParameters: ComposeUiViewControllerParameters): Set<String> {
        val frameworkNames = mutableSetOf<String>()
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        if (isSwiftExportModuleNameConfigured()) {
            val swiftExport = kmp.extensions.getByType(SwiftExportExtension::class.java)
            frameworkNames += swiftExport.moduleName.orNull ?: ""
            println("\t> SwiftExport is configured, will use its moduleName as frameworkBaseName: $frameworkNames")
        } else if (extensionParameters.moduleName != null) {
            frameworkNames += extensionParameters.moduleName ?: ""
            println("\t> Extension Parameter moduleName is configured, will it as frameworkBaseName: $frameworkNames")
        } else {
            kmp.targets.configureEach { target ->
                if (target.fromIosFamily()) {
                    (target as KotlinNativeTarget).binaries.withType(Framework::class.java).configureEach { framework ->
                        frameworkNames += framework.baseName
                    }
                }
            }
            println("\t> SwiftExport is NOT configured, will use all iOS targets' framework baseName as frameworkBaseName: $frameworkNames")
        }
        return frameworkNames.ifEmpty { throw GradleException("Cloud not determine framework's module name") }
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
        return packages.ifEmpty { throw GradleException("Cloud not determine project's package") }
    }

    private fun Project.isSwiftExportModuleNameConfigured(): Boolean {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        return try {
            kmp.extensions.getByType(SwiftExportExtension::class.java).moduleName.orNull != null
        } catch (_: UnknownDomainObjectException) {
            false
        }
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

    private fun Project.writeModuleMetadataToDisk(args: Map<String, Set<String>>) {
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
                    swiftExport = true
                )
            )
        }
        file.writeText(Json.encodeToString(moduleMetadata))
    }

    private fun Project.registerCopyFilesToXcodeTask(project: Project, extensionParameters: ComposeUiViewControllerParameters) {
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
                            task.doLast {
                                delete()
                            }
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

    private fun Project.finalizeFrameworkTasks(extensionParameters: ComposeUiViewControllerParameters) {
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

    internal companion object {
        private const val VERSION_LIBRARY = "2.2.20-RC-1.9.0-beta03"
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
    }
}
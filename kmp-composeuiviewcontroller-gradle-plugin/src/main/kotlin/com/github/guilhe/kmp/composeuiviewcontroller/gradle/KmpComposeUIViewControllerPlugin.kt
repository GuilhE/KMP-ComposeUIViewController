package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.Module
import kotlinx.serialization.encodeToString
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
import org.jetbrains.kotlin.konan.target.Family
import java.io.BufferedReader
import java.io.File

public class KmpComposeUIViewControllerPlugin : Plugin<Project> {

    private fun KotlinTarget.fromIosFamily(): Boolean = this is KotlinNativeTarget && konanTarget.family == Family.IOS

    private fun ComposeUiViewControllerParameters.toList() = listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)

    override fun apply(project: Project) {
        with(project) {
            if (!plugins.hasPlugin(PLUGIN_KMP)) {
                throw PluginInstantiationException(ERROR_MISSING_KMP)
            }

            if (!plugins.hasPlugin(PLUGIN_KSP)) {
                throw PluginInstantiationException(ERROR_MISSING_KSP)
            }

            println("> $LOG_TAG:")
            setupTargets()
            with(extensions.create(EXTENSION_PLUGIN, ComposeUiViewControllerParameters::class.java)) {
                registerCopyFilesToXcodeTask(project, this)
                finalizeFrameworkTasks(this)
            }
            afterEvaluate {
                writeArgsToDisk(configureModuleJson(retrievePackage(), retrieveFrameworkBaseNames()))
            }
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

    private fun Project.retrieveFrameworkBaseNames(): Set<String> {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val frameworkNames = mutableSetOf<String>()
        kmp.targets.configureEach { target ->
            if (target.fromIosFamily()) {
                (target as KotlinNativeTarget).binaries.withType(Framework::class.java).configureEach { framework ->
                    frameworkNames += framework.baseName
                }
            }
        }
        return frameworkNames
    }

    private fun Project.retrievePackage(): String {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        val srcDirs = commonMainSourceSet.kotlin.srcDirs
        for (srcDir in srcDirs) {
            srcDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension == "kt") {
                    val relativePath = file.relativeTo(srcDir).parentFile?.path ?: ""
                    val packagePath = relativePath.replace(File.separator, ".")
                    if (packagePath.isNotEmpty()) {
                        return packagePath
                    }
                }
            }
        }
        if (group.toString().isNotEmpty()) {
            return group.toString()
        }
        throw IllegalStateException("Cloud not determine project's package nor group")
    }

    private fun Project.configureModuleJson(packageName: String, frameworkNames: Set<String>): Map<String, String> {
        packageName.ifEmpty { return emptyMap() }
        frameworkNames.ifEmpty { return emptyMap() }
        val args = mutableMapOf<String, String>()
        val frameworkBaseName = frameworkNames.first() //let's assume for now all targets will have the same frameworkBaseName
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.targets.configureEach { target ->
            if (target.fromIosFamily()) {
                args[frameworkBaseName] = packageName
            }
        }
        return args
    }

    private fun Project.writeArgsToDisk(args: Map<String, String>) {
        val file = rootProject.layout.buildDirectory.file(FILE_NAME_ARGS).get().asFile
        val modules = try {
            Json.decodeFromString<MutableSet<Module>>(file.readText())
        } catch (e: Exception) {
            mutableSetOf()
        }
        args.forEach { (key, value) -> modules.add(Module(name = name.toString(), packageName = value, frameworkBaseName = key)) }
        file.writeText(Json.encodeToString(modules))
    }

    private fun Project.finalizeFrameworkTasks(extensionParameters: ComposeUiViewControllerParameters) {
        tasks.matching { it.name == TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE || it.name == TASK_SYNC_FRAMEWORK }.configureEach { task ->
            if (extensionParameters.autoExport) {
                println("\n> $LOG_TAG:\n\t> ${task.name}will be finalizedBy $TASK_COPY_FILES_TO_XCODE task")
                task.finalizedBy(TASK_COPY_FILES_TO_XCODE)
            }
        }
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

            with(File("${rootProject.layout.buildDirectory.asFile.get().path}/${FILE_NAME_SCRIPT_TEMP}")) {
                writeText(modifiedScript)
                setExecutable(true)
                task.workingDir = project.rootDir
                task.commandLine("bash", "-c", "./build/$FILE_NAME_SCRIPT_TEMP")
                if (!keepScriptFile) {
                    task.doLast { delete() }
                }
            }
        }
    }

    internal companion object {
        private const val VERSION_LIBRARY = "2.0.20-Beta1-1.6.11-BETA-4"
        private const val LOG_TAG = "KmpComposeUIViewControllerPlugin"
        internal const val PLUGIN_KMP = "org.jetbrains.kotlin.multiplatform"
        internal const val PLUGIN_KSP = "com.google.devtools.ksp"
        internal const val LIB_GROUP = "com.github.guilhe.kmp"
        internal const val LIB_KSP_NAME = "kmp-composeuiviewcontroller-ksp"
        private const val LIB_KSP = "$LIB_GROUP:$LIB_KSP_NAME:$VERSION_LIBRARY"
        internal const val LIB_ANNOTATIONS_NAME = "kmp-composeuiviewcontroller-annotations"
        private const val LIB_ANNOTATION = "$LIB_GROUP:$LIB_ANNOTATIONS_NAME:$VERSION_LIBRARY"
        private const val EXTENSION_PLUGIN = "ComposeUiViewController"
        internal const val TASK_COPY_FILES_TO_XCODE = "copyFilesToXcode"
        internal const val TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE = "embedAndSignAppleFrameworkForXcode"
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
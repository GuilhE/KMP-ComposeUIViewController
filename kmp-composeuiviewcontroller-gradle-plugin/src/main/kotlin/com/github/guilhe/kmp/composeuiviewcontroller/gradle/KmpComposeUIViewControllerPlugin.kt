package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.google.devtools.ksp.gradle.KspExtension
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
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.konan.target.Family
import java.io.BufferedReader
import java.io.File

private const val VERSION = "2.0.20-Beta1-1.6.11-BETA-1"

public class KmpComposeUIViewControllerPlugin : Plugin<Project> {
    private fun KotlinTarget.fromIosFamily(): Boolean = this is KotlinNativeTarget && konanTarget.family == Family.IOS

    override fun apply(project: Project) {
        with(project) {
            if (!plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                throw PluginInstantiationException("KmpComposeUIViewControllerPlugin requires the Kotlin Multiplatform plugin to be applied.")
            }

            if (!plugins.hasPlugin("com.google.devtools.ksp")) {
                throw PluginInstantiationException("KmpComposeUIViewControllerPlugin requires the KSP plugin to be applied.")
            }

            println("> KmpComposeUIViewControllerPlugin:")
            setupTargets()
            with(extensions.create("ComposeUiViewController", ComposeUiViewControllerParameters::class.java)) {
                registerCopyFilesToXcodeTask(project, this)
                finalizeFrameworkTasks(this)
            }
            afterEvaluate {
                val frameworkNames = collectFrameworkBaseNames()
                configureCompileArgs(frameworkNames)
            }
        }
    }

    private fun Project.setupTargets() {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        configurations.getByName(commonMainSourceSet.implementationConfigurationName).dependencies.apply {
            add(dependencies.create("com.github.guilhe.kmp:kmp-composeuiviewcontroller-annotations:$VERSION"))
            println(
                "\t> Adding com.github.guilhe.kmp:kmp-composeuiviewcontroller-annotations:$VERSION" +
                        " to ${commonMainSourceSet.implementationConfigurationName}"
            )
        }

        kmp.targets.configureEach { target ->
            if (!target.fromIosFamily()) return@configureEach
            val kspConfigName = "ksp${target.targetName.replaceFirstChar { it.uppercaseChar() }}"
            dependencies.add(kspConfigName, "com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:$VERSION")
            println("\t> Adding com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:$VERSION to $kspConfigName")
        }
    }

    private fun Project.collectFrameworkBaseNames(): Set<String> {
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        val frameworkNames = mutableSetOf<String>()
        kotlin.targets.configureEach { target ->
            if (target.fromIosFamily()) {
                (target as KotlinNativeTarget).binaries.withType(Framework::class.java).configureEach { framework ->
                    frameworkNames += framework.baseName
                }
            }
        }
        return frameworkNames
    }

    private fun Project.configureCompileArgs(frameworkNames: Set<String>) {
        frameworkNames.ifEmpty { return }
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.targets.configureEach { target ->
            if (target.fromIosFamily()) {
                tasks.withType(KotlinNativeCompile::class.java).configureEach {
                    val ksp = extensions.getByType(KspExtension::class.java)
                    ksp.apply {
                        if(!arguments.containsKey("frameworkBaseName")) {
                            arg("frameworkBaseName", frameworkNames.first())
                        }
                    }
                }
            }
        }
    }

    private fun Project.finalizeFrameworkTasks(extensionParameters: ComposeUiViewControllerParameters) {
        tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" || it.name == "syncFramework" }.configureEach { task ->
            if (extensionParameters.autoExport) {
                println("> KmpComposeUIViewControllerPlugin: ${task.name} will be finalizedBy CopyFilesToXcodeTask")
                task.finalizedBy("CopyFilesToXcode")
            }
        }
    }

    private fun Project.registerCopyFilesToXcodeTask(project: Project, extensionParameters: ComposeUiViewControllerParameters) {
        tasks.register("CopyFilesToXcode", Exec::class.java) { task ->
            val keepScriptFile = project.hasProperty("keepScriptFile") && project.property("keepScriptFile") == "true"
            println("\t> parameters: ${extensionParameters.toList()}")
            val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/exportToXcode.sh")
            val script = inputStream?.use { stream ->
                stream.bufferedReader().use(BufferedReader::readText)
            } ?: throw GradleException("Unable to read resource file")

            val modifiedScript = script
                .replace(
                    oldValue = "kmp_module=\"shared\"",
                    newValue = "kmp_module=\"${project.name}\""
                )
                .replace(
                    oldValue = "iosApp_project_folder=\"iosApp\"",
                    newValue = "iosApp_project_folder=\"${extensionParameters.iosAppFolderName}\""
                )
                .replace(
                    oldValue = "iosApp_name=\"iosApp\"",
                    newValue = "iosApp_name=\"${extensionParameters.iosAppName}\""
                )
                .replace(
                    oldValue = "iosApp_target_name=\"iosApp\"",
                    newValue = "iosApp_target_name=\"${extensionParameters.targetName}\""
                )
                .replace(
                    oldValue = "group_name=\"SharedRepresentables\"",
                    newValue = "group_name=\"${extensionParameters.exportFolderName}\""
                )

            val scriptFile = File("${project.rootDir}/exportToXcode.sh")
            scriptFile.writeText(modifiedScript)
            scriptFile.setExecutable(true)
            task.workingDir = project.rootDir
            task.commandLine("bash", "-c", "./exportToXcode.sh")
            if (!keepScriptFile) {
                task.doLast { scriptFile.delete() }
            }
        }
    }

    private fun ComposeUiViewControllerParameters.toList(): List<*> =
        listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)
}
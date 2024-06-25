package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import java.io.BufferedReader
import java.io.File

public class KMPComposeUIViewControllerPlugin : Plugin<Project> {
    private fun KotlinTarget.isKmpNativeCoroutinesTarget(): Boolean = this is KotlinNativeTarget && konanTarget.family == Family.IOS

    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            throw PluginInstantiationException("KMPComposeUIViewControllerPlugin requires the Kotlin Multiplatform plugin to be applied.")
        }

        if (!project.plugins.hasPlugin("com.google.devtools.ksp")) {
            throw PluginInstantiationException("KMPComposeUIViewControllerPlugin requires the KSP plugin to be applied.")
        }

        with(project) {
            println("> KMPComposeUIViewControllerPlugin:")
            setupTargets()
            with(extensions.create("ComposeUiViewController", ComposeUiViewControllerParameters::class.java)) {
                copyFilesToXcodeTask(project, this)
                finalizeFrameworksTasks(this)
            }
        }
    }

    private fun Project.setupTargets() {
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val commonMainSourceSet = kotlin.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
            configurations.getByName(commonMainSourceSet.implementationConfigurationName).dependencies.apply {
                add(dependencies.create("com.github.guilhe.kmp:kmp-composeuiviewcontroller-annotations:$VERSION"))
                println(
                    "\t> Adding com.github.guilhe.kmp:kmp-composeuiviewcontroller-annotations:$VERSION" +
                            " to ${commonMainSourceSet.implementationConfigurationName}"
                )
            }

            kotlin.targets.configureEach { kotlinTarget ->
                if (!kotlinTarget.isKmpNativeCoroutinesTarget()) return@configureEach
                val kspConfigName = "ksp${kotlinTarget.targetName.replaceFirstChar { it.uppercaseChar() }}"
                dependencies.add(kspConfigName, "com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:$VERSION")
                println("\t> Adding com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:$VERSION to $kspConfigName")
            }
        }
    }

    private fun Project.finalizeFrameworksTasks(extensionParameters: ComposeUiViewControllerParameters) {
        tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" || it.name == "syncFramework" }.configureEach { task ->
            if (extensionParameters.autoExport) {
                println("> KMPComposeUIViewControllerPlugin: ${task.name} will be finalizedBy copyFilesToXcode")
                task.finalizedBy("copyFilesToXcode")
            }
        }
    }

    private fun Project.copyFilesToXcodeTask(project: Project, extensionParameters: ComposeUiViewControllerParameters) {
        tasks.register("copyFilesToXcode", Exec::class.java) { task ->
            val keepScriptFile = project.hasProperty("keepScriptFile") && project.property("keepScriptFile") == "true"
            println("\t> parameters: ${extensionParameters.toList()}")
            val inputStream = KMPComposeUIViewControllerPlugin::class.java.getResourceAsStream("/exportToXcode.sh")
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
}
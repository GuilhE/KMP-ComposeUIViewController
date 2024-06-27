package com.github.guilhe.kmp.composeuiviewcontroller.gradle

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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.konan.target.Family
import java.io.BufferedReader
import java.io.File

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
            addFrameworkBaseNameToKsp()
            setupTargets()
            with(extensions.create("ComposeUiViewController", ComposeUiViewControllerParameters::class.java)) {
                finalizeFrameworksTasks(this)
                copyFilesToXcodeTask(project, this)
            }
        }
    }

    private fun Project.addFrameworkBaseNameToKsp() {
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.targets.configureEach { target ->
            if (target.fromIosFamily()) {
                val frameworkNames = mutableSetOf<String>()
                (target as KotlinNativeTarget).binaries.withType(Framework::class.java).configureEach { framework ->
                    println(">>>>>>  ${target.name} ${framework.name} ${framework.baseName}")
                    frameworkNames += framework.baseName
                }.also {
                    tasks.withType(KotlinCompilationTask::class.java).configureEach { task ->
                        task.compilerOptions {
                            if (!freeCompilerArgs.get().any { it.contains("-Pplugin:com.google.devtools.ksp:frameworkBaseName=") }) {
                                freeCompilerArgs.addAll(
                                    "-Pplugin:com.google.devtools.ksp:frameworkBaseName=${frameworkNames.joinToString(",")}"
                                )
                            }
                        }
                    }
                }
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

            kotlin.targets.configureEach { target ->
                if (!target.fromIosFamily()) return@configureEach
                val kspConfigName = "ksp${target.targetName.replaceFirstChar { it.uppercaseChar() }}"
                dependencies.add(kspConfigName, "com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:$VERSION")
                println("\t> Adding com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:$VERSION to $kspConfigName")
            }
        }
    }

    private fun Project.finalizeFrameworksTasks(extensionParameters: ComposeUiViewControllerParameters) {
        tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" || it.name == "syncFramework" }.configureEach { task ->
            if (extensionParameters.autoExport) {
                println("> KmpComposeUIViewControllerPlugin: ${task.name} will be finalizedBy copyFilesToXcode")
                task.finalizedBy("copyFilesToXcode")
            }
        }
    }

    private fun Project.copyFilesToXcodeTask(project: Project, extensionParameters: ComposeUiViewControllerParameters) {
        tasks.register("copyFilesToXcode", Exec::class.java) { task ->
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
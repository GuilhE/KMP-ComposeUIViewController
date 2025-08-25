@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension

/**
 * Utility functions for handling SwiftExport configurations
 */
internal object SwiftExportUtils {

    /**
     * Retrieves the specific SwiftExport module name for the current project.
     * Since SwiftExport doesn't expose the exports collection, we need to look
     * in the root project or parent projects for the SwiftExport configuration
     * that references this project.
     */
    fun Project.getSwiftExportModuleNameForProject(): String? {
        return try {
            println("\t> Looking for SwiftExport config that references project: ${this.name}")

            val rootSwiftExportModuleName = rootProject.findSwiftExportModuleNameForProject(this)
            if (rootSwiftExportModuleName != null) {
                println("\t> Found SwiftExport module name in root project: $rootSwiftExportModuleName")
                return rootSwiftExportModuleName
            }

            var currentProject = this.parent
            while (currentProject != null) {
                val parentSwiftExportModuleName = currentProject.findSwiftExportModuleNameForProject(this)
                if (parentSwiftExportModuleName != null) {
                    println("\t> Found SwiftExport module name in parent project ${currentProject.name}: $parentSwiftExportModuleName")
                    return parentSwiftExportModuleName
                }
                currentProject = currentProject.parent
            }

            rootProject.allprojects.forEach { project ->
                if (project != this) {
                    val projectSwiftExportModuleName = project.findSwiftExportModuleNameForProject(this)
                    if (projectSwiftExportModuleName != null) {
                        println("\t> Found SwiftExport module name in project ${project.name}: $projectSwiftExportModuleName")
                        return projectSwiftExportModuleName
                    }
                }
            }
            null
        } catch (e: Exception) {
            println("\t> Exception while searching for SwiftExport module name: ${e.message}")
            null
        }
    }

    /**
     * Looks for SwiftExport configuration in this project that references the target project
     */
    private fun Project.findSwiftExportModuleNameForProject(targetProject: Project): String? {
        return try {
            val kmp = extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return null
            kmp.extensions.findByType(SwiftExportExtension::class.java) ?: return null

            if (!buildFile.exists()) return null
            // ex: export(projects.sharedModels) { moduleName = "Models" }
            // ex: export(":shared-models") { moduleName = "Models" }
            val exportPattern =
                Regex("""export\s*\(\s*(?:projects\.(\w+)|["']?:?([^"':)]+)["']?)\s*\)\s*\{\s*moduleName\s*=\s*["']([^"']+)["']\s*\}""")
            val matches = exportPattern.findAll(buildFile.readText())

            for (match in matches) {
                val projectNameWithAccessors = match.groupValues[1] // projects.sharedModels
                val projectNameDirect = match.groupValues[2] // shared-models (with or without :)
                val moduleName = match.groupValues[3] // Models
                val projectName = projectNameWithAccessors.ifEmpty { projectNameDirect.removePrefix(":") }
                if (isProjectNameMatch(projectName, targetProject.name)) {
                    return moduleName
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

        if (configProjectName.replace("-", "").lowercase() == targetProjectName.replace("-", "").lowercase()) return true

        return false
    }
}

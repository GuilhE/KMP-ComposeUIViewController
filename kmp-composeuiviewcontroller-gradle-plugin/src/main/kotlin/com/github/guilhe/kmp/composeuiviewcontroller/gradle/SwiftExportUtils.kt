@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension

internal data class SwiftExportConfig(
    val moduleName: String?,
    val flattenPackage: String?
)

/**
 * Utility functions for handling SwiftExport configurations
 */
internal object SwiftExportUtils {

    /**
     * Retrieves the specific SwiftExport configuration for the current project.
     * Since SwiftExport doesn't expose the exports collection, we need to look
     * in the root project or parent projects for the SwiftExport configuration
     * that references this project.
     */
    fun Project.getSwiftExportConfigForProject(): SwiftExportConfig? {
        return try {
            println("\t> Looking for SwiftExport config that references project: ${this.name}")

            val rootSwiftExportConfig = rootProject.findSwiftExportConfigForProject(this)
            if (rootSwiftExportConfig != null) {
//                println("\t> Found SwiftExport config in root project: $rootSwiftExportConfig")
                return rootSwiftExportConfig
            }

            var currentProject = this.parent
            while (currentProject != null) {
                val parentSwiftExportConfig = currentProject.findSwiftExportConfigForProject(this)
                if (parentSwiftExportConfig != null) {
//                    println("\t> Found SwiftExport config in parent project ${currentProject.name}: $parentSwiftExportConfig")
                    return parentSwiftExportConfig
                }
                currentProject = currentProject.parent
            }

            rootProject.allprojects.forEach { project ->
                if (project != this) {
                    val projectSwiftExportConfig = project.findSwiftExportConfigForProject(this)
                    if (projectSwiftExportConfig != null) {
//                        println("\t> Found SwiftExport config in project ${project.name}: $projectSwiftExportConfig")
                        return projectSwiftExportConfig
                    }
                }
            }
            null
        } catch (e: Exception) {
            println("\t> Exception while searching for SwiftExport config: ${e.message}")
            null
        }
    }

    /**
     * Looks for SwiftExport configuration in this project that references the target project
     */
    private fun Project.findSwiftExportConfigForProject(targetProject: Project): SwiftExportConfig? {
        return try {
            val kmp = extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return null
            kmp.extensions.findByType(SwiftExportExtension::class.java) ?: return null

            if (!buildFile.exists()) return null

            val buildFileContent = buildFile.readText()

            // Extract only the export blocks from swiftExport
            // Look for patterns like: export(projects.something) { ... } or export(project(":something")) { ... }
            val exportPattern = Regex(
                """export\s*\(\s*(?:projects\.([a-zA-Z0-9_-]+)|project\(\s*["']([^"']*)["']\s*\))\s*\)\s*\{([^}]*)\}""",
                RegexOption.DOT_MATCHES_ALL
            )

            val exportMatches = exportPattern.findAll(buildFileContent)

            for (match in exportMatches) {
                val projectNameWithAccessors = match.groupValues[1] // projects.sharedModels
                val projectNameDirect = match.groupValues[2] // shared-models (with or without :)
                val configBlock = match.groupValues[3] // the content inside the braces

                val projectName = projectNameWithAccessors.ifEmpty { projectNameDirect.removePrefix(":") }

                if (isProjectNameMatch(projectName, targetProject.name)) {
                    val moduleNamePattern = Regex("""moduleName\s*=\s*["']([^"']+)["']""")
                    val moduleNameMatch = moduleNamePattern.find(configBlock)
                    val moduleName = moduleNameMatch?.groupValues?.get(1) ?: continue

                    val flattenPackagePattern = Regex("""flattenPackage\s*=\s*["']([^"']+)["']""")
                    val flattenPackageMatch = flattenPackagePattern.find(configBlock)
                    val flattenPackage = flattenPackageMatch?.groupValues?.get(1)

                    return SwiftExportConfig(moduleName, flattenPackage)
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

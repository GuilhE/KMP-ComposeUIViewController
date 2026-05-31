@file:Suppress("SpellCheckingInspection", "LoggingSimilarMessage")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.TASK_VALIDATE_REPRESENTABLES
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

internal fun Project.configureTaskToRegisterValidateRepresentables(
    project: Project,
    extensionParameters: ComposeUiViewControllerParameters
) {
    tasks.register(TASK_VALIDATE_REPRESENTABLES) { task ->
        task.group = "composeuiviewcontroller"
        task.description = "Validates that Representables are correctly generated, synced to Xcode destination, and referenced in xcodeproj"
        task.outputs.upToDateWhen { false }
        task.doLast {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            val kspDir = project.layout.buildDirectory.dir("generated/ksp").get().asFile
            val kspFiles = if (kspDir.exists()) {
                kspDir.walkTopDown().filter { it.isFile && it.extension == "swift" }.toList()
            } else {
                emptyList()
            }

            if (kspFiles.isEmpty()) {
                errors += "No Swift files in KSP output (${kspDir.relativeTo(project.rootDir)}). Run a build first or ./gradlew clean."
            } else {
                logger.lifecycle("\t> [OK] KSP output: ${kspFiles.size} Swift file(s)")
            }

            if (extensionParameters.experimentalSpmExport) {
                val sourcesDir = File(
                    project.rootDir,
                    "${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}/Sources/${extensionParameters.exportFolderName}"
                )
                val packageSwift = File(
                    project.rootDir,
                    "${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}/Package.swift"
                )

                if (!packageSwift.exists()) {
                    warnings += "Package.swift not found at ${packageSwift.relativeTo(project.rootDir)}. Run the build once to generate it."
                } else {
                    logger.lifecycle("\t> [OK] Package.swift exists")
                }

                val destFiles = if (sourcesDir.exists()) {
                    sourcesDir.listFiles { f -> f.isFile && f.extension == "swift" }?.toList() ?: emptyList()
                } else {
                    emptyList()
                }

                if (kspFiles.isNotEmpty() && destFiles.isEmpty()) {
                    errors += "KSP has " +
						"${kspFiles.size} file(s) but Sources dir is empty (${sourcesDir.relativeTo(project.rootDir)}). Run exportToSpm."
                } else if (destFiles.isNotEmpty()) {
                    logger.lifecycle("\t> [OK] Sources: ${destFiles.size} Swift file(s)")
                }

                if (kspFiles.isNotEmpty() && destFiles.isNotEmpty()) {
                    val kspNames = kspFiles.map { it.name }.toSet()
                    val destNames = destFiles.map { it.name }.toSet()
                    (kspNames - destNames).forEach { errors += "In KSP output but missing from Sources: $it" }
                    (destNames - kspNames).forEach { warnings += "In Sources but not in KSP output (stale): $it" }
                    if ((kspNames - destNames).isEmpty()) logger.lifecycle("\t> [OK] KSP output and Sources are in sync")
                }
            } else {
                val destDir = File(project.rootDir, "${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}")
                val destFiles = if (destDir.exists()) {
                    destDir.listFiles { f -> f.isFile && f.extension == "swift" }?.toList() ?: emptyList()
                } else {
                    emptyList()
                }

                if (kspFiles.isNotEmpty() && destFiles.isEmpty()) {
                    errors += "KSP has " +
						"${kspFiles.size} file(s) but destination is empty (${destDir.relativeTo(project.rootDir)}). Run copyFilesToXcode."
                } else if (destFiles.isNotEmpty()) {
                    logger.lifecycle("\t> [OK] Destination: ${destFiles.size} Swift file(s)")
                }

                if (kspFiles.isNotEmpty() && destFiles.isNotEmpty()) {
                    val kspNames = kspFiles.map { it.name }.toSet()
                    val destNames = destFiles.map { it.name }.toSet()
                    (kspNames - destNames).forEach { errors += "In KSP output but missing from destination: $it" }
                    (destNames - kspNames).forEach { warnings += "In destination but not in KSP output (stale): $it" }
                    if ((kspNames - destNames).isEmpty()) logger.lifecycle("\t> [OK] KSP output and destination are in sync")
                }

                if (extensionParameters.autoExport) {
                    val pbxprojFile = File(
                        project.rootDir,
                        "${extensionParameters.iosAppFolderName}/${extensionParameters.iosAppName}.xcodeproj/project.pbxproj"
                    )
                    if (pbxprojFile.exists()) {
                        val pbxContent = pbxprojFile.readText()
                        val kspNames = kspFiles.map { it.name }.toSet()
                        val expectedFiles = destFiles.filter { it.name in kspNames }
                        val unreferenced = expectedFiles.filter { f -> !pbxContent.contains(f.name) }
                        if (unreferenced.isNotEmpty()) {
                            unreferenced.forEach { errors += "Not referenced in xcodeproj: ${it.name}" }
                        } else if (expectedFiles.isNotEmpty()) {
                            logger.lifecycle("\t> [OK] All ${expectedFiles.size} Representable(s) referenced in xcodeproj")
                        }
                    } else {
                        warnings += "xcodeproj not found at ${pbxprojFile.relativeTo(project.rootDir)} — skipping reference check"
                    }
                }
            }

            warnings.forEach { logger.warn("\t> [WARN] $it") }
            if (errors.isNotEmpty()) {
                errors.forEach { logger.error("\t> [FAIL] $it") }
                throw GradleException("Representables validation failed with ${errors.size} error(s). See above for details.")
            }
            logger.lifecycle("\t> Validation passed")
        }
    }
}
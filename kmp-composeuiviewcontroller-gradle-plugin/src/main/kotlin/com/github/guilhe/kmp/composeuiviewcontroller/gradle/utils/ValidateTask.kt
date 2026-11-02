@file:Suppress("SpellCheckingInspection", "LoggingSimilarMessage")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils

import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PluginParameters
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_VALIDATE_REPRESENTABLES
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

internal fun Project.configureTaskToRegisterValidateRepresentables(
    project: Project,
    extensionParameters: PluginParameters
) {
    // Task actions must not capture Project (Configuration Cache): resolve what they need into plain values/providers.
    // The extension values are only final after the build script is evaluated, so they're read when the task runs
    // (from the extension, which is serialized with the final values).
    val rootDirFile = project.rootDir
    val kspDirProvider = project.layout.buildDirectory.dir("generated/ksp")

    tasks.register(TASK_VALIDATE_REPRESENTABLES) { task ->
        task.group = "composeuiviewcontroller"
        task.description = "Validates that Representables are correctly generated, synced to Xcode destination, and referenced in xcodeproj"
        task.outputs.upToDateWhen { false }
        task.doLast { validateTask ->
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()

            val kspDir = kspDirProvider.get().asFile
            val kspFiles = if (kspDir.exists()) {
                kspDir.walkTopDown().filter { it.isFile && it.extension == "swift" }.toList()
            } else {
                emptyList()
            }

            if (kspFiles.isEmpty()) {
                errors += "No Swift files in KSP output (${kspDir.relativeTo(rootDirFile)}). Run a build first or ./gradlew clean."
            } else {
                validateTask.logger.lifecycle("\t> [OK] KSP output: ${kspFiles.size} Swift file(s)")
            }

            if (!extensionParameters.legacyMode) {
                val sourcesDir = File(
                    rootDirFile,
                    "${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}/Sources/${extensionParameters.exportFolderName}"
                )
                val packageSwift = File(
                    rootDirFile,
                    "${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}/Package.swift"
                )

                if (!packageSwift.exists()) {
                    warnings += "Package.swift not found at ${packageSwift.relativeTo(rootDirFile)}. Run the build once to generate it."
                } else {
                    validateTask.logger.lifecycle("\t> [OK] Package.swift exists")
                }

                val destFiles = if (sourcesDir.exists()) {
					// Placeholder.swift is kept permanently to prevent SPM "empty target" errors.
                    sourcesDir.listFiles { f -> f.isFile && f.extension == "swift" && f.name != "Placeholder.swift" }?.toList() ?: emptyList()
                } else {
                    emptyList()
                }

                if (kspFiles.isNotEmpty() && destFiles.isEmpty()) {
                    errors += "KSP has " +
						"${kspFiles.size} file(s) but Sources dir is empty (${sourcesDir.relativeTo(rootDirFile)}). Run exportToSpm."
                } else if (destFiles.isNotEmpty()) {
                    validateTask.logger.lifecycle("\t> [OK] Sources: ${destFiles.size} Swift file(s)")
                }

                if (kspFiles.isNotEmpty() && destFiles.isNotEmpty()) {
                    val kspNames = kspFiles.map { it.name }.toSet()
                    val destNames = destFiles.map { it.name }.toSet()
                    (kspNames - destNames).forEach { errors += "In KSP output but missing from Sources: $it" }
                    (destNames - kspNames).forEach { warnings += "In Sources but not in KSP output (stale): $it" }
                    if ((kspNames - destNames).isEmpty()) validateTask.logger.lifecycle("\t> [OK] KSP output and Sources are in sync")
                }
            } else {
                val destDir = File(rootDirFile, "${extensionParameters.iosAppFolderName}/${extensionParameters.exportFolderName}")
                val destFiles = if (destDir.exists()) {
                    destDir.listFiles { f -> f.isFile && f.extension == "swift" }?.toList() ?: emptyList()
                } else {
                    emptyList()
                }

                if (kspFiles.isNotEmpty() && destFiles.isEmpty()) {
                    errors += "KSP has " +
						"${kspFiles.size} file(s) but destination is empty (${destDir.relativeTo(rootDirFile)}). Run copyFilesToXcode."
                } else if (destFiles.isNotEmpty()) {
                    validateTask.logger.lifecycle("\t> [OK] Destination: ${destFiles.size} Swift file(s)")
                }

                if (kspFiles.isNotEmpty() && destFiles.isNotEmpty()) {
                    val kspNames = kspFiles.map { it.name }.toSet()
                    val destNames = destFiles.map { it.name }.toSet()
                    (kspNames - destNames).forEach { errors += "In KSP output but missing from destination: $it" }
                    (destNames - kspNames).forEach { warnings += "In destination but not in KSP output (stale): $it" }
                    if ((kspNames - destNames).isEmpty()) validateTask.logger.lifecycle("\t> [OK] KSP output and destination are in sync")
                }

                if (extensionParameters.autoExport) {
                    val pbxprojFile = File(
                        rootDirFile,
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
                            validateTask.logger.lifecycle("\t> [OK] All ${expectedFiles.size} Representable(s) referenced in xcodeproj")
                        }
                    } else {
                        warnings += "xcodeproj not found at ${pbxprojFile.relativeTo(rootDirFile)} — skipping reference check"
                    }
                }
            }

            warnings.forEach { validateTask.logger.warn("\t> [WARN] $it") }
            if (errors.isNotEmpty()) {
                errors.forEach { validateTask.logger.error("\t> [FAIL] $it") }
                throw GradleException("Representables validation failed with ${errors.size} error(s). See above for details.")
            }
            validateTask.logger.lifecycle("\t> Validation passed")
        }
    }
}
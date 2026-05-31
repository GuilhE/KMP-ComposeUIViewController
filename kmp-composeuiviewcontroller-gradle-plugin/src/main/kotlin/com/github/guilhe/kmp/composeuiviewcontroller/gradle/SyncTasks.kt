@file:Suppress("SpellCheckingInspection", "LoggingSimilarMessage")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import java.io.BufferedReader
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.Exec

internal fun Project.configureTaskToRegisterSwiftFormat(project: Project) {
    tasks.register(TASK_FORMAT_SWIFT_FILES, Exec::class.java) { task ->
        task.group = "composeuiviewcontroller"
        task.description = "Formats generated Swift files using swiftformat"
        task.outputs.upToDateWhen { false } // Always execute this task - don't cache it
        task.doFirst { logger.info("\t> Formatting Swift files for module: ${project.name}") }

        val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$FILE_NAME_FORMAT_SCRIPT")
        val script = inputStream?.use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        }
            ?: throw PluginConfigurationException("Unable to read resource file: $FILE_NAME_FORMAT_SCRIPT. Ensure the plugin is correctly packaged.")

        val modifiedScript = script.replace("$PARAM_KMP_MODULE=\"shared\"", "$PARAM_KMP_MODULE=\"${project.name}\"")
        val tempFile = File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/$FILE_NAME_FORMAT_SCRIPT_TEMP")
            .also {
                it.parentFile?.mkdirs()
                it.createNewFile()
            }

        if (tempFile.exists()) {
            tempFile.writeText(modifiedScript)
            tempFile.setExecutable(true)
            task.workingDir = project.rootDir

            try {
                task.commandLine("bash", "-c", tempFile.absolutePath)
                task.doLast {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                throw PluginConfigurationException(
                    "Failed to configure script execution for task '$TASK_FORMAT_SWIFT_FILES'. Script path: ${tempFile.absolutePath}",
                    e
                )
            }
        } else {
            throw PluginConfigurationException("Failed to create temporary script file: $FILE_NAME_FORMAT_SCRIPT_TEMP at ${tempFile.absolutePath}")
        }
    }
}

internal fun Project.configureTaskToRegisterCopyFilesToXcode(
    project: Project,
    extensionParameters: ComposeUiViewControllerParameters,
    tempFolder: File
) {
    tasks.register(TASK_COPY_FILES_TO_XCODE, Exec::class.java) { task ->
        task.group = "composeuiviewcontroller"
        task.description = "Copies generated files to Xcode project"
        task.outputs.upToDateWhen { false } // Always execute this task - don't cache it
        task.doFirst { logger.info("\t> Extension parameters: ${extensionParameters.toList()}") }

        val keepScriptFile = project.hasProperty(PARAM_KEEP_FILE) && project.property(PARAM_KEEP_FILE) == "true"
        val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$FILE_NAME_COPY_SCRIPT")
        val script = inputStream?.use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        } ?: throw PluginConfigurationException("Unable to read resource file: $FILE_NAME_COPY_SCRIPT. Ensure the plugin is correctly packaged.")

        val modifiedScript = script
            .replace("$PARAM_KMP_MODULE=\"shared\"", "$PARAM_KMP_MODULE=\"${project.name}\"")
            .replace("$PARAM_FOLDER=\"iosApp\"", "$PARAM_FOLDER=\"${extensionParameters.iosAppFolderName}\"")
            .replace("$PARAM_APP_NAME=\"iosApp\"", "$PARAM_APP_NAME=\"${extensionParameters.iosAppName}\"")
            .replace("$PARAM_TARGET=\"iosApp\"", "$PARAM_TARGET=\"${extensionParameters.targetName}\"")
            .replace("$PARAM_GROUP=\"Representables\"", "$PARAM_GROUP=\"${extensionParameters.exportFolderName}\"")

        val tempFile = File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/${FILE_NAME_COPY_SCRIPT_TEMP}")
            .also { it.createNewFile() }

        if (tempFile.exists()) {
            tempFile.writeText(modifiedScript)
            tempFile.setExecutable(true)
            task.workingDir = project.rootDir

            try {
                task.commandLine("bash", "-c", tempFile.absolutePath)
                if (!keepScriptFile) {
                    task.doLast {
                        if (tempFolder.exists()) {
                            tempFolder.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                throw PluginConfigurationException(
                    "Failed to configure script execution for task '$TASK_COPY_FILES_TO_XCODE'. Script path: ${tempFile.absolutePath}",
                    e
                )
            }
        } else {
            throw PluginConfigurationException("Failed to create temporary script file: $FILE_NAME_COPY_SCRIPT_TEMP at ${tempFile.absolutePath}")
        }
    }
}

internal fun Project.configureTaskToRegisterExportToSpm(
    project: Project,
    extensionParameters: ComposeUiViewControllerParameters,
    tempFolder: File,
    spmModuleName: String
) {
    tasks.register(TASK_EXPORT_TO_SPM, Exec::class.java) { task ->
        task.group = "composeuiviewcontroller"
        task.description = "Creates and maintains a local SPM package with KSP-generated Swift Representables"
        task.outputs.upToDateWhen { false }
        task.doFirst { logger.info("\t> Extension parameters: ${extensionParameters.toSpmList()}") }

        val keepScriptFile = project.hasProperty(PARAM_KEEP_FILE) && project.property(PARAM_KEEP_FILE) == "true"
        val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$FILE_NAME_SPM_SCRIPT")
        val script = inputStream?.use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        } ?: throw PluginConfigurationException("Unable to read resource file: $FILE_NAME_SPM_SCRIPT. Ensure the plugin is correctly packaged.")

        val modifiedScript = script
            .replace("$PARAM_KMP_MODULE=\"shared\"", "$PARAM_KMP_MODULE=\"${project.name}\"")
            .replace("$PARAM_FOLDER=\"iosApp\"", "$PARAM_FOLDER=\"${extensionParameters.iosAppFolderName}\"")
            .replace("$PARAM_GROUP=\"Representables\"", "$PARAM_GROUP=\"${extensionParameters.exportFolderName}\"")
            .replace("$PARAM_SPM_MODULE=\"Composables\"", "$PARAM_SPM_MODULE=\"$spmModuleName\"")

        val tempFile = File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/$FILE_NAME_SPM_SCRIPT_TEMP")
            .also { it.createNewFile() }

        if (tempFile.exists()) {
            tempFile.writeText(modifiedScript)
            tempFile.setExecutable(true)
            task.workingDir = project.rootDir

            try {
                task.commandLine("bash", "-c", tempFile.absolutePath)
                if (!keepScriptFile) {
                    task.doLast {
                        if (tempFolder.exists()) {
                            tempFolder.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                throw PluginConfigurationException(
                    "Failed to configure script execution for task '$TASK_EXPORT_TO_SPM'. Script path: ${tempFile.absolutePath}",
                    e
                )
            }
        } else {
            throw PluginConfigurationException("Failed to create temporary script file: $FILE_NAME_SPM_SCRIPT_TEMP at ${tempFile.absolutePath}")
        }
    }
}

internal fun Project.configureTaskToFinalizeByCopyFilesToXcode(extensionParameters: ComposeUiViewControllerParameters) {
    val exportTask = if (extensionParameters.experimentalSpmExport) TASK_EXPORT_TO_SPM else TASK_COPY_FILES_TO_XCODE
    val triggerTasks = if (extensionParameters.experimentalSpmExport) {
        listOf(TASK_EMBED_SWIFT_EXPORT_FOR_XCODE)
    } else {
        listOf(TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE, TASK_EMBED_SWIFT_EXPORT_FOR_XCODE, TASK_SYNC_FRAMEWORK)
    }
    tasks.matching { it.name in triggerTasks }.configureEach { task ->
        if (extensionParameters.autoExport) {
            logger.info("\n> " +
				"KmpComposeUIViewControllerPlugin:\n\t> Task '${task.name}' will be finalized by '$TASK_FORMAT_SWIFT_FILES' -> '$exportTask'")
            task.finalizedBy(TASK_FORMAT_SWIFT_FILES)
        }
    }
    tasks.named(TASK_FORMAT_SWIFT_FILES).configure {
        it.finalizedBy(exportTask)
    }
}

internal fun Project.configureTaskToRegisterSetupSpmPackage(
    project: Project,
    extensionParameters: ComposeUiViewControllerParameters,
    spmModuleName: String
) {
    tasks.register(TASK_SETUP_SPM_PACKAGE, Exec::class.java) { task ->
        task.group = "composeuiviewcontroller"
        task.description = "One-time setup: creates the local SPM package stub and adds it to the Xcode project. Re-run after ./gradlew clean."
        task.outputs.upToDateWhen { false }
        task.doFirst { logger.info("\t> Setting up SPM package for module: ${project.name}") }

        val inputStream = KmpComposeUIViewControllerPlugin::class.java.getResourceAsStream("/$FILE_NAME_SETUP_SPM_SCRIPT")
        val script = inputStream?.use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        } ?: throw PluginConfigurationException("Unable to read resource file: $FILE_NAME_SETUP_SPM_SCRIPT. Ensure the plugin is correctly packaged.")

        val modifiedScript = script
            .replace("$PARAM_KMP_MODULE=\"shared\"", "$PARAM_KMP_MODULE=\"${project.name}\"")
            .replace("$PARAM_FOLDER=\"iosApp\"", "$PARAM_FOLDER=\"${extensionParameters.iosAppFolderName}\"")
            .replace("$PARAM_APP_NAME=\"iosApp\"", "$PARAM_APP_NAME=\"${extensionParameters.iosAppName}\"")
            .replace("$PARAM_TARGET=\"iosApp\"", "$PARAM_TARGET=\"${extensionParameters.targetName}\"")
            .replace("$PARAM_GROUP=\"Representables\"", "$PARAM_GROUP=\"${extensionParameters.exportFolderName}\"")
            .replace("$PARAM_SPM_MODULE=\"Composables\"", "$PARAM_SPM_MODULE=\"$spmModuleName\"")

        val tempFile = File("${rootProject.layout.buildDirectory.asFile.get().path}/$TEMP_FILES_FOLDER/$FILE_NAME_SETUP_SPM_SCRIPT_TEMP")
            .also {
                it.parentFile?.mkdirs()
                it.createNewFile()
            }

        if (tempFile.exists()) {
            tempFile.writeText(modifiedScript)
            tempFile.setExecutable(true)
            task.workingDir = project.rootDir

            try {
                task.commandLine("bash", "-c", tempFile.absolutePath)
                task.doLast {
                    if (tempFile.exists()) tempFile.delete()
                }
            } catch (e: Exception) {
                throw PluginConfigurationException(
                    "Failed to configure script execution for task '$TASK_SETUP_SPM_PACKAGE'. Script path: ${tempFile.absolutePath}",
                    e
                )
            }
        } else {
            throw PluginConfigurationException("Failed to create temporary script file: $FILE_NAME_SETUP_SPM_SCRIPT_TEMP at ${tempFile.absolutePath}")
        }
    }
}

internal fun ComposeUiViewControllerParameters.toList() =
    listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)

internal fun ComposeUiViewControllerParameters.toSpmList() =
    listOf(iosAppFolderName, exportFolderName, autoExport, experimentalSpmExport)
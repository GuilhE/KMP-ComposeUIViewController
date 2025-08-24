@file:Suppress("SpellCheckingInspection")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KSP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_MODULE_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.FILE_NAME_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.INFO_MODULE_NAME_BY_FRAMEWORK
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.INFO_MODULE_NAME_BY_SWIFT_EXPORT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.INFO_MODULE_PACKAGES
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.LIB_ANNOTATIONS_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.LIB_GROUP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.LIB_KSP_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PARAM_APP_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PARAM_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PARAM_GROUP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PARAM_KEEP_FILE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PARAM_KMP_MODULE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PARAM_TARGET
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PLUGIN_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.PLUGIN_KSP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.TASK_COPY_FILES_TO_XCODE
import kotlinx.serialization.json.Json
import org.gradle.api.internal.project.DefaultProject
import org.gradle.internal.impldep.junit.framework.TestCase.assertNotNull
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginTest {

    private val project = ProjectBuilder.builder().build()
    private lateinit var projectDir: File

    @BeforeTest
    fun setup() {
        val tempDir = File(project.layout.buildDirectory.asFile.get(), TEMP_FILES_FOLDER).apply { mkdirs() }
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
        project.pluginManager.apply(PLUGIN_KMP)
        project.pluginManager.apply(PLUGIN_KSP)
        project.pluginManager.apply(PLUGIN_ID)
    }

    @AfterTest
    fun cleanupTestKitDirectories() {
        val testKitDir = project.layout.buildDirectory.asFile.get()
        if (testKitDir.exists()) {
            testKitDir.deleteRecursively()
        }
    }

    @Test
    fun `Plugin is applied correctly`() {
        assertTrue(project.plugins.hasPlugin(PLUGIN_ID))
    }

    @Test
    fun `Plugin throws exception if KSP plugin is not applied`() {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("$PLUGIN_KMP") 
                id("$PLUGIN_ID")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains(ERROR_MISSING_KSP))
    }

    @Test
    fun `Plugin throws exception if Kotlin Multiplatform plugin is not applied`() {
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("$PLUGIN_KSP")
                id("$PLUGIN_ID")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains(ERROR_MISSING_KMP))
    }

    @Test
    fun `Method setupTargets only adds KSP dependencies to iOS targets`() {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.apply {
            jvm()
            iosX64()
            iosArm64()
        }

        kotlin.targets.forEach { target ->
            if (target is KotlinNativeTarget && target.konanTarget.family == Family.IOS) {
                val kspConfigName = "ksp${target.targetName.replaceFirstChar { it.uppercaseChar() }}"
                val config = project.configurations.findByName(kspConfigName)
                assertNotNull(config)
                assertTrue(config!!.dependencies.any { it.group == LIB_GROUP && it.name == LIB_KSP_NAME })
            }
        }

        val jvmKspConfig = project.configurations.findByName("kspJvm")
        jvmKspConfig?.let { config ->
            assertFalse(config.dependencies.any { it.group == LIB_GROUP && it.name == LIB_KSP_NAME })
        }
    }

    @Test
    fun `Method setupTargets configures dependencies and targets correctly`() {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.apply {
            jvm()
            iosX64()
            iosArm64()
            iosSimulatorArm64()
        }

        val commonMainSourceSet = kotlin.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        val implementationConfiguration = project.configurations.getByName(commonMainSourceSet.implementationConfigurationName)
        assertTrue(implementationConfiguration.dependencies.any { it.group == LIB_GROUP && it.name == LIB_ANNOTATIONS_NAME })

        kotlin.targets.forEach { kotlinTarget ->
            if (kotlinTarget is KotlinNativeTarget && kotlinTarget.konanTarget.family == Family.IOS) {
                val kspConfigName = "ksp${kotlinTarget.targetName.replaceFirstChar { it.uppercaseChar() }}"
                val dependencies = project.configurations.getByName(kspConfigName).dependencies
                assertNotNull(dependencies)
                assertTrue(dependencies.any { it.group == LIB_GROUP && it.name == LIB_KSP_NAME })
            }
        }
    }

    @Test
    fun `Method configureModuleJson creates and saves in disk modules metadata`() {
        with(project) {
            extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
                jvm()
                iosSimulatorArm64().binaries.framework { baseName = "ComposablesFramework" }
            }

            val folder = File(projectDir.path, "src/commonMain/kotlin/com/composables/module").apply { mkdirs() }
            val classFile = File(folder, "File.kt")
            classFile.writeText(
                """
                com.composables.module
                class Test()
                """.trimIndent()
            )
            assertTrue(classFile.exists())

            println("> $state")
            (this as DefaultProject).evaluate()
            println("> $state")

            val file = rootProject.layout.buildDirectory.file("$TEMP_FILES_FOLDER/$FILE_NAME_ARGS").get().asFile
            assertTrue(file.exists())

            val moduleMetadata = Json.decodeFromString<List<ModuleMetadata>>(file.readText()).first()
            assertTrue(moduleMetadata.frameworkBaseName == "ComposablesFramework")
            assertTrue(moduleMetadata.packageNames.any { p -> p.startsWith("com.composables.module") })
        }
    }

    @Test
    fun `Method finalizeFrameworksTasks correctly finalizes embedAndSignAppleFrameworkForXcode or embedSwiftExportForXcode or syncFramework with copyFilesToXcode task`() {
        with(KmpComposeUIViewControllerPlugin.Companion) {
            val embedObjCTask = project.tasks.register(TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE) {}
            val embedSwiftTask = project.tasks.register(TASK_EMBED_SWIFT_EXPORT_FOR_XCODE) {}
            val syncTask = project.tasks.register(TASK_SYNC_FRAMEWORK) {}
            assertTrue(embedObjCTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size == 1)
            assertTrue(embedSwiftTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size == 1)
            assertTrue(syncTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size == 1)
        }
    }

    @Test
    fun `Method finalizeFrameworkTasks does not finalize when autoExport is false`() {
        with(project) {
            val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.apply {
                jvm()
                iosSimulatorArm64()
            }

            val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
            val classFile = File(folder, "Test.kt")
            classFile.writeText(
                """
                package com.test
                class Test()
                """.trimIndent()
            )

            val buildFile = File(projectDir, "build.gradle.kts")
            buildFile.writeText(
                """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    jvm()
                    iosSimulatorArm64 {
                        binaries.framework {
                            baseName = "TestFramework"
                        }
                    }
                }

                ComposeUiViewController {
                    autoExport = false
                }
                """.trimIndent()
            )

            val settingsFile = File(projectDir, "settings.gradle.kts")
            settingsFile.writeText("rootProject.name = \"testProject\"")
            assertTrue(settingsFile.exists())

            val result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("help", "--stacktrace")
                .build()

            // When autoExport is false, the finalization message should not appear
            assertFalse(result.output.contains("will be finalizedBy"))
        }
    }

    @Test
    fun `Task copyFilesToXcode will inject the extension parameters into exportToXcode file`() {
        val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
        val classFile = File(folder, "File.kt")
        classFile.writeText(
            """
            package com.test
            class Test()
            """.trimIndent()
        )
        assertTrue(classFile.exists())

        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("$PLUGIN_KMP")
                id("$PLUGIN_KSP")
                id("$PLUGIN_ID")
            }

            ComposeUiViewController {
                iosAppFolderName = "iosFolder"
                iosAppName = "iosApp"
                targetName = "iosTarget"
                exportFolderName = "Composables"
                moduleName = "Test"
            }
        """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(TASK_COPY_FILES_TO_XCODE, "-P$PARAM_KEEP_FILE=true", "--stacktrace")
            .forwardOutput()
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val script = File("$projectDir/build/$TEMP_FILES_FOLDER/$FILE_NAME_SCRIPT_TEMP")
        assertTrue(script.exists())

        val modifiedScriptContent = script.readText()
        assertTrue(modifiedScriptContent.contains("$PARAM_KMP_MODULE=\"${projectDir.name}\""))
        assertTrue(modifiedScriptContent.contains("$PARAM_FOLDER=\"iosFolder\""))
        assertTrue(modifiedScriptContent.contains("$PARAM_APP_NAME=\"iosApp\""))
        assertTrue(modifiedScriptContent.contains("$PARAM_TARGET=\"iosTarget\""))
        assertTrue(modifiedScriptContent.contains("$PARAM_GROUP=\"Composables\""))
    }

    @Test
    fun `Task cleanTempFilesFolder is registered and configured correctly`() {
        with(KmpComposeUIViewControllerPlugin.Companion) {
            assertTrue(project.tasks.names.contains(TASK_CLEAN_TEMP_FILES_FOLDER))

            val cleanTask = project.tasks.getByName("clean")
            val cleanTempTask = project.tasks.getByName(TASK_CLEAN_TEMP_FILES_FOLDER)

            assertTrue(cleanTask.finalizedBy.getDependencies(cleanTempTask).isNotEmpty())
        }
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets throws exception when no framework names found`() {
        val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
        val classFile = File(folder, "File.kt")
        classFile.writeText(
            """
            package com.test
            class Test()
            """.trimIndent()
        )
        assertTrue(classFile.exists())

        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }
                """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains(ERROR_MISSING_MODULE_NAME))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles Obj-C export`() {
        val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
        val classFile = File(folder, "File.kt")
        classFile.writeText(
            """
            package com.test
            class Test()
            """.trimIndent()
        )
        assertTrue(classFile.exists())

        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64 {
                        binaries.framework {
                            baseName = "DefaultFramework"
                        }
                    }
                }

                ComposeUiViewController {
                    moduleName = "CustomModuleName"
                }
                """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_FRAMEWORK [DefaultFramework]"))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles SwiftExport with moduleName`() {
        val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
        val classFile = File(folder, "File.kt")
        classFile.writeText(
            """
            package com.test
            class Test()
            """.trimIndent()
        )
        assertTrue(classFile.exists())

        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                    swiftExport {
                        moduleName = "CustomModuleName"
                    }
                }
                """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [CustomModuleName]"))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles SwiftExport with exported moduleName`() {
        val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
        val classFile = File(folder, "File.kt")
        classFile.writeText(
            """
            package com.test
            class Test()
            """.trimIndent()
        )
        assertTrue(classFile.exists())

        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                }

                kotlin {
                    iosSimulatorArm64()
                    swiftExport {
                        moduleName = "CustomModuleName"
                        export(projects.testProject) {
                            moduleName = "ExportedModuleName"
                        }
                    }
                }
                """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [CustomModuleName]"))
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [ExportedModuleName]"))
    }

    @Test
    fun `Method retrieveModulePackagesFromCommonMain throws exception when package not found`() {
        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }
                
                kotlin {
                    iosSimulatorArm64()
                    swiftExport {
                        moduleName = "DefaultModule"
                    }
                }
                """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains(ERROR_MISSING_PACKAGE))
    }

    @Test
    fun `Method retrieveModulePackagesFromCommonMain successfuly retrieves package information`() {
        val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
        val classFile = File(folder, "File.kt")
        classFile.writeText(
            """
            package com.test
            class Test()
            """.trimIndent()
        )
        assertTrue(classFile.exists())

        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }
                
                kotlin {
                    iosSimulatorArm64()
                    swiftExport {
                        moduleName = "DefaultModule"
                    }
                }
                """.trimIndent()
        )
        assertTrue(buildFile.exists())

        val settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("rootProject.name = \"testProject\"")
        assertTrue(settingsFile.exists())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("$INFO_MODULE_PACKAGES [com.test]"))
    }

    private companion object {
        private const val PLUGIN_ID = "io.github.guilhe.kmp.plugin-composeuiviewcontroller"
    }
}
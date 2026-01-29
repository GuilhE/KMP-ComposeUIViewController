@file:Suppress("SpellCheckingInspection")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_FRAMEWORK_CONFIG
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.FILE_NAME_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.INFO_MODULE_NAME_BY_FRAMEWORK
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.INFO_MODULE_NAME_BY_SWIFT_EXPORT
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
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `Plugin throws exception if Kotlin Multiplatform plugin is not applied`() {
        Templates.writeBuildGradle(
            projectDir,
            """
            plugins {
                id("$PLUGIN_KSP")
                id("$PLUGIN_ID")
            }
            """
        )

        val result = Templates.runGradle(projectDir, expectFailure = true)
        assertTrue(result.output.contains(ERROR_MISSING_KMP))
    }

    @Test
    fun `Plugin build failure will clear temp files`() {
        val buildFile = Templates.writeBuildGradle(
            projectDir,
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }
                
                kotlin {
                    iosSimulatorArm64()
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.ensureSettings(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir, expectFailure = true)
        assertTrue(result.output.contains(ERROR_MISSING_PACKAGE))

        val tempFolder = File("$projectDir/build/$TEMP_FILES_FOLDER")
        assertFalse(tempFolder.exists())
    }

    @Test
    fun `Method setupTargets only adds KSP dependencies to iOS targets`() {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.apply {
            jvm()
            iosArm64()
        }

        kotlin.targets.forEach { target ->
            if (target is KotlinNativeTarget && target.konanTarget.family == Family.IOS) {
                val kspConfigName = "ksp${target.targetName.replaceFirstChar { it.uppercaseChar() }}"
                val config = project.configurations.findByName(kspConfigName)
                assertNotNull(config)
                assertTrue(config.dependencies.any { it.group == LIB_GROUP && it.name == LIB_KSP_NAME })
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
            assertEquals( "ComposablesFramework", moduleMetadata.frameworkBaseName)
            assertTrue(moduleMetadata.packageNames.any { p -> p.startsWith("com.composables.module") })
        }
    }

    @Test
    fun `Method finalizeFrameworksTasks correctly finalizes embedAndSignAppleFrameworkForXcode or embedSwiftExportForXcode or syncFramework with copyFilesToXcode task`() {
        with(KmpComposeUIViewControllerPlugin.Companion) {
            val embedObjCTask = project.tasks.register(TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE) {}
            val embedSwiftTask = project.tasks.register(TASK_EMBED_SWIFT_EXPORT_FOR_XCODE) {}
            val syncTask = project.tasks.register(TASK_SYNC_FRAMEWORK) {}
            assertEquals(1, embedObjCTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size)
            assertEquals(1, embedSwiftTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size)
            assertEquals(1, syncTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size)
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

            Templates.createCommonMainSource(projectDir, packageName = "com.test")

            Templates.writeBuildGradle(
                projectDir,
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
                """
            )

            val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
            assertTrue(settingsFile.exists())

            val result = Templates.runGradle(projectDir, args = listOf("help", "--stacktrace"))

            // When autoExport is false, the finalization message should not appear
            assertFalse(result.output.contains("will be finalizedBy"))
        }
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles Obj-C export`() {
        Templates.createCommonMainSource(projectDir, packageName = "com.test")
        val buildFile = Templates.writeBuildGradle(
            projectDir,
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64 {
                        binaries.framework {
                            baseName = "FrameworkName"
                        }
                    }
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir)
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_FRAMEWORK [FrameworkName]"))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles SwiftExport with moduleName`() {
        Templates.createCommonMainSource(projectDir, packageName = "com.test")

        val buildFile = Templates.writeBuildGradle(
            projectDir,
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
                        flattenPackage = "com.test.abc"
                    }
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir)
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [CustomModuleName]"))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets throws exception when no moduleName exists`() {
        Templates.createCommonMainSource(projectDir, packageName = "com.test")

        val buildFile = Templates.writeBuildGradle(
            projectDir,
            """
                import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir, expectFailure = true)
        assertTrue(result.output.contains(ERROR_MISSING_FRAMEWORK_CONFIG))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles SwiftExport with exported moduleName`() {
        val commonFile = Templates.createCommonMainSource(projectDir, packageName = "com.test")
        assertTrue(commonFile.exists())

        val abcProjectDir = File(projectDir, "abc").apply { mkdirs() }
        val abcCommonFile = Templates.createCommonMainSource(abcProjectDir, packageName = "com.abc", className = "AbcTest")
        assertTrue(abcCommonFile.exists())

        val abcBuildFile = Templates.writeBuildGradle(
            abcProjectDir,
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                }
                """
        )
        assertTrue(abcBuildFile.exists())

        val buildFile = Templates.writeBuildGradle(
            projectDir,
            """
                import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                    @OptIn(ExperimentalSwiftExportDsl::class)
                    swiftExport {
                        moduleName = "DefaultModule"
                        export(project(":abc")) {
                            moduleName = "ExportedModule"
                            flattenPackage = "com.exported"
                        }
                    }
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject", extra = "include(\":abc\")")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir)
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [DefaultModule]"))
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [ExportedModule]"))
    }

    @Test
    fun `Method retrieveFrameworkBaseNamesFromIosTargets handles SwiftExport with exported module with explicit moduleName`() {
        val commonFile = Templates.createCommonMainSource(projectDir, packageName = "com.test")
        assertTrue(commonFile.exists())

        val abcProjectDir = File(projectDir, "abc").apply { mkdirs() }
        val abcCommonFile = Templates.createCommonMainSource(abcProjectDir, packageName = "com.abc", className = "AbcTest")
        assertTrue(abcCommonFile.exists())

        val abcBuildFile = Templates.writeBuildGradle(
            abcProjectDir,
            """
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                }
                """
        )
        assertTrue(abcBuildFile.exists())

        val buildFile = Templates.writeBuildGradle(
            projectDir,
            """
                import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl
                plugins {
                    id("$PLUGIN_KMP")
                    id("$PLUGIN_KSP")
                    id("$PLUGIN_ID")
                }

                kotlin {
                    iosSimulatorArm64()
                    @OptIn(ExperimentalSwiftExportDsl::class)
                    swiftExport {
                        moduleName = "DefaultModule"
                        export(projects.abc) {
                            moduleName = "AbcModule"
                            flattenPackage = "com.abc"
                        }
                    }
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.ensureSettings(
            projectDir,
            rootProjectName = "testProject",
            includes = listOf(":abc"),
            typesafeAccessors = true
        )
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir)
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [DefaultModule]"))
        assertTrue(result.output.contains("$INFO_MODULE_NAME_BY_SWIFT_EXPORT [AbcModule]"))
    }

    @Test
    fun `Method PackageResolver throws exception when package not found`() {
        val buildFile = Templates.writeBuildGradle(
            projectDir,
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
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir, expectFailure = true)
        assertTrue(result.output.contains(ERROR_MISSING_PACKAGE))
    }

    @Test
    fun `Method PackageResolver successfuly retrieves package information`() {
        val classFile = Templates.createCommonMainSource(projectDir, packageName = "com.test")
        assertTrue(classFile.exists())

        val buildFile = Templates.writeBuildGradle(
            projectDir,
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
                }
                
                kotlin {
                    iosSimulatorArm64()
                    swiftExport {
                        moduleName = "DefaultModule"
                    }
                }
                """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(
            projectDir,
            args = listOf(TASK_COPY_FILES_TO_XCODE, "-P$PARAM_KEEP_FILE=true", "--stacktrace")
        )
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
    fun `Task copyFilesToXcode will clear temp files after success`() {
        val classFile = Templates.createCommonMainSource(projectDir, packageName = "com.test")
        assertTrue(classFile.exists())

        val buildFile = Templates.writeBuildGradle(
            projectDir,
            """
            plugins {
                id("$PLUGIN_KMP")
                id("$PLUGIN_KSP")
                id("$PLUGIN_ID")
            }
            
            kotlin {
                iosSimulatorArm64()
                swiftExport {
                    moduleName = "TestModule"
                }
            }
        """
        )
        assertTrue(buildFile.exists())

        val settingsFile = Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
        assertTrue(settingsFile.exists())

        val result = Templates.runGradle(projectDir)
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val tempFile = File("$projectDir/build/$TEMP_FILES_FOLDER/$FILE_NAME_SCRIPT_TEMP")
        assertFalse(tempFile.exists())
    }

    private companion object {
        private const val PLUGIN_ID = "io.github.guilhe.kmp.plugin-composeuiviewcontroller"
    }
}
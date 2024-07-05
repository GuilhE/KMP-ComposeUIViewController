package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.Module
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KSP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.FILE_NAME_SCRIPT_TEMP
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class PluginTest {

    private val project = ProjectBuilder.builder().build()
    private lateinit var projectDir: File

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        File(project.layout.buildDirectory.asFile.get(), TEMP_FILES_FOLDER).apply { mkdirs() }
        project.pluginManager.apply(PLUGIN_KMP)
        project.pluginManager.apply(PLUGIN_KSP)
        project.pluginManager.apply(PLUGIN_ID)
    }

    @Test
    fun `Plugin is applied correctly`() {
        assertTrue(project.plugins.hasPlugin(PLUGIN_ID))
    }

    @Test
    fun `Plugin throws exception if KSP plugin is not applied`(@TempDir tempDir: File) {
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
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
    fun `Plugin throws exception if Kotlin Multiplatform plugin is not applied`(@TempDir tempDir: File) {
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
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
                group = "com.composables.module"
                jvm()
                iosSimulatorArm64().binaries.framework { baseName = "ComposablesFramework" }
            }

            println("> $state")
            (this as DefaultProject).evaluate()
            println("> $state")

            val file = rootProject.layout.buildDirectory.file("$TEMP_FILES_FOLDER/$FILE_NAME_ARGS").get().asFile
            assertTrue(file.exists())

            val module = Json.decodeFromString<List<Module>>(file.readText()).first()
            assertTrue(module.frameworkBaseName == "ComposablesFramework")
            assertTrue(module.packageName == "com.composables.module")
        }
    }

    @Test
    fun `Method finalizeFrameworksTasks correctly finalizes embedAndSignAppleFrameworkForXcode or syncFramework with CopyFilesToXcode task`() {
        with(KmpComposeUIViewControllerPlugin.Companion) {
            val embedTask = project.tasks.register(TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE) {}
            val syncTask = project.tasks.register(TASK_SYNC_FRAMEWORK) {}
            assertTrue(embedTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size == 1)
            assertTrue(syncTask.get().finalizedBy.getDependencies(project.tasks.getByName(TASK_COPY_FILES_TO_XCODE)).size == 1)
        }
    }

    @Test
    fun `Task CopyFilesToXcode will inject the extension parameters into exportToXcode file`(@TempDir tempDir: File) {
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
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
                autoExport = true
            }
            
            group = "com.test"
        """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(TASK_COPY_FILES_TO_XCODE, "-P$PARAM_KEEP_FILE=true", "--stacktrace")
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

    private companion object {
        private const val PLUGIN_ID = "io.github.guilhe.kmp.plugin-composeuiviewcontroller"
    }
}
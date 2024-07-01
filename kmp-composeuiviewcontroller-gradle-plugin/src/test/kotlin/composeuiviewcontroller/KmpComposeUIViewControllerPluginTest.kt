package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ARG_KSP_FRAMEWORK_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.ERROR_MISSING_KSP
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
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.SCRIPT_FILE_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.SCRIPT_TEMP_FILE_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin.Companion.TASK_COPY_FILES_TO_XCODE
import com.google.devtools.ksp.gradle.KspExtension
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

class KmpComposeUIViewControllerPluginTest {

    private val project = ProjectBuilder.builder().build()
    private lateinit var projectDir: File

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
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
    fun `Method configureCompileArgs adds frameworkBaseName as a KSP parameter`() {
        with(project) {
            extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
                group = "com.composables.module"
                jvm()
                iosSimulatorArm64().binaries.framework { baseName = "ComposablesFramework" }
            }

            println("> $state")
            (project as DefaultProject).evaluate()
            println("> $state")
            println("> ${extensions.getByType(KspExtension::class.java).arguments}")

            assertTrue(extensions.getByType(KspExtension::class.java).arguments.containsKey("$ARG_KSP_FRAMEWORK_NAME-ComposablesFramework"))
            assertTrue(extensions.getByType(KspExtension::class.java).arguments.containsValue("com.composables.module"))
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

        val modifiedScriptContent = File(projectDir, SCRIPT_TEMP_FILE_NAME).readText()
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
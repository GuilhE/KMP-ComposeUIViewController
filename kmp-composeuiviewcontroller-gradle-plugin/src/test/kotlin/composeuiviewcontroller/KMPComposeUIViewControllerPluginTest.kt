package composeuiviewcontroller

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

class KMPComposeUIViewControllerPluginTest {

    private val project = ProjectBuilder.builder().build()
    private lateinit var projectDir: File

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.google.devtools.ksp")
        project.pluginManager.apply("com.github.guilhe.kmp.composeuiviewcontroller")
    }

    @Test
    fun `Plugin throws exception if KSP plugin is not applied`(@TempDir tempDir: File) {
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.github.guilhe.kmp.composeuiviewcontroller")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains("KMPComposeUIViewControllerPlugin requires the KSP plugin to be applied."))
    }

    @Test
    fun `Plugin throws exception if Kotlin Multiplatform plugin is not applied`(@TempDir tempDir: File) {
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("com.google.devtools.ksp")
                id("com.github.guilhe.kmp.composeuiviewcontroller")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains("KMPComposeUIViewControllerPlugin requires the Kotlin Multiplatform plugin to be applied."))
    }

    @Test
    fun `Plugin is applied correctly`() {
        assertTrue(project.plugins.hasPlugin("com.github.guilhe.kmp.composeuiviewcontroller"))
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

        assertTrue(implementationConfiguration.dependencies.any {
            it.group == "com.github.guilhe.kmp" && it.name == "kmp-composeuiviewcontroller-annotations"// && it.version == $VERSION
        })

        kotlin.targets.forEach { kotlinTarget ->
            if (kotlinTarget is KotlinNativeTarget && kotlinTarget.konanTarget.family == Family.IOS) {
                val kspConfigName = "ksp${kotlinTarget.targetName.replaceFirstChar { it.uppercaseChar() }}"
                val dependencies = project.configurations.getByName(kspConfigName).dependencies
                assertNotNull(dependencies)
                assertTrue(dependencies.any {
                    it.group == "com.github.guilhe.kmp" && it.name == "kmp-composeuiviewcontroller-ksp"// && it.version == $VERSION
                })
            }
        }
    }

    @Test
    fun `Method finalizeFrameworksTasks correctly finalizes embedAndSignAppleFrameworkForXcode or syncFramework with copyFilesToXcode task`() {
        val embedTask = project.tasks.register("embedAndSignAppleFrameworkForXcode") {}
        val syncTask = project.tasks.register("syncFramework") {}
        assertTrue(embedTask.get().finalizedBy.getDependencies(project.tasks.getByName("copyFilesToXcode")).size == 1)
        assertTrue(syncTask.get().finalizedBy.getDependencies(project.tasks.getByName("copyFilesToXcode")).size == 1)
    }

    @Test
    fun `Task copyFilesToXcode will inject the extension parameters into exportToXcode file`(@TempDir tempDir: File) {
        projectDir = File(tempDir, "testProject").apply { mkdirs() }
        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.google.devtools.ksp")
                id("com.github.guilhe.kmp.composeuiviewcontroller")
            }

            ComposeUiViewController {
                iosAppFolderName = "iosFolder"
                iosAppName = "iosApp"
                targetName = "iosTarget"
                exportFolderName = "Composables"
                autoExport = true
            }
        """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("copyFilesToXcode", "-PkeepScriptFile=true", "--stacktrace")
            .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        val modifiedScriptContent = File(projectDir, "exportToXcode.sh").readText()
        assertTrue(modifiedScriptContent.contains("kmp_module=\"${projectDir.name}\""))
        assertTrue(modifiedScriptContent.contains("iosApp_project_folder=\"iosFolder\""))
        assertTrue(modifiedScriptContent.contains("iosApp_name=\"iosApp\""))
        assertTrue(modifiedScriptContent.contains("iosApp_target_name=\"iosTarget\""))
        assertTrue(modifiedScriptContent.contains("group_name=\"Composables\""))
    }
}

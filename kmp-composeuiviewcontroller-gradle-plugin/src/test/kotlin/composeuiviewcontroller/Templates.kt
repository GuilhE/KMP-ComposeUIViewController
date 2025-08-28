package composeuiviewcontroller

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

object Templates {
    const val DEFAULT_ROOT_NAME: String = "testProject"

    /**
     * Creates a Kotlin source file under src/commonMain/kotlin for the given package and class.
     */
    fun createCommonMainSource(
        projectDir: File,
        packageName: String,
        className: String = "Test",
        fileName: String = "File.kt"
    ): File {
        val pkgPath = packageName.replace('.', '/')
        val dir = File(projectDir, "src/commonMain/kotlin/$pkgPath").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(
            """
            package $packageName
            class $className()
            """.trimIndent()
        )
        return file
    }

    /**
     * Writes the provided content to build.gradle.kts in projectDir, returning the file.
     */
    fun writeBuildGradle(projectDir: File, content: String): File {
        val buildFile = File(projectDir, "build.gradle.kts")
        buildFile.parentFile?.mkdirs()
        buildFile.writeText(content.trimIndent())
        return buildFile
    }

    /**
     * Writes a settings.gradle.kts with the given rootProject name and optional extra lines (e.g., includes).
     */
    fun writeSettingsGradle(
        projectDir: File,
        rootProjectName: String = DEFAULT_ROOT_NAME,
        extra: String = ""
    ): File {
        val settingsFile = File(projectDir, "settings.gradle.kts")
        val content = buildString {
            appendLine("rootProject.name = \"$rootProjectName\"")
            if (extra.isNotBlank()) append(extra.trim()).append('\n')
        }
        settingsFile.writeText(content)
        return settingsFile
    }

    /**
     * Ensures settings.gradle.kts exists with optional includes and Typesafe Project Accessors.
     */
    fun ensureSettings(
        projectDir: File,
        rootProjectName: String = DEFAULT_ROOT_NAME,
        includes: List<String> = emptyList(),
        typesafeAccessors: Boolean = false,
        extra: String = ""
    ): File {
        val includeLines = includes.joinToString("\n") { name -> "include(\"$name\")" }
        val feature = if (typesafeAccessors) "enableFeaturePreview(\"TYPESAFE_PROJECT_ACCESSORS\")\n" else ""
        val extras = buildString {
            append(feature)
            if (includeLines.isNotBlank()) append(includeLines).append('\n')
            if (extra.isNotBlank()) append(extra.trim()).append('\n')
        }
        return writeSettingsGradle(projectDir, rootProjectName, extras)
    }

    /**
     * Runs a Gradle build using TestKit. Set expectFailure to true to call buildAndFail().
     */
    fun runGradle(
        projectDir: File,
        args: List<String> = emptyList(),
        forwardOutput: Boolean = true,
        expectFailure: Boolean = false
    ): BuildResult {
        var runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
        if (forwardOutput) runner = runner.forwardOutput()
        return if (expectFailure) runner.withArguments(args).buildAndFail() else runner.withArguments(args).build()
    }
}
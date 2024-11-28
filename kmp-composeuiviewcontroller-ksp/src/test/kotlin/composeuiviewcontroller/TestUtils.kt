@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package composeuiviewcontroller

import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import java.io.File

object TestUtils {

    /**
     * Searches for files in a specified Gradle cache path matching the given package names.
     *
     * NOTE: [klibSourceFiles] was updated for CI usage to replace [findFiles], addressing issues with relative path resolution.
     *
     * @param basePath The root directory to start the search (e.g., ~/.gradle/caches/modules-2/files-2.1).
     * @param packages A list of package names to search for (e.g., "androidx.compose.runtime").
     * @param extension The file extension to filter by.
     * @param exclude A list of names to exclude from the file name search (e.g., "source", "metadata").
     * @param verbose Enables detailed logging of the search process.
     * @return A list of [File] objects pointing to the files found.
     */
    fun findFiles(
        basePath: String,
        packages: List<String>,
        extension: String,
        exclude: List<String> = emptyList(),
        verbose: Boolean = false
    ): List<File> {
        val rootDirectory = File(basePath)
        if (!rootDirectory.exists() || !rootDirectory.isDirectory) {
            throw IllegalArgumentException("Invalid base path: $basePath")
        }
        val files = mutableListOf<File>()
        for (packageName in packages) {
            val searchPath = File(rootDirectory, packageName)

            if (searchPath.exists() && searchPath.isDirectory) {
                searchPath.walkTopDown().forEach { file ->
                    if (file.extension == extension && !exclude.any { file.name.contains(it) }) {
                        files.add(file)
                        if (verbose) {
                            println("> ${file.name} found!")
                        }
                    }
                }
            }
        }
        return files
    }

    val jarPackages: List<File> = findFiles(
        basePath = System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1",
        packages = listOf("org.jetbrains.compose.runtime", "org.jetbrains.compose.ui"),
        extension = "jar",
        exclude = listOf("sources", "metadata")
    )
    val pluginPackages: List<File> = findFiles(
        basePath = System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1",
        packages = listOf("org.jetbrains.compose", "org.jetbrains.kotlin.plugin.compose"),
        extension = "plugin"
    )

    /**
     *  The kotlin-compile-testing library does not support Kotlin Multiplatform (KMP).
     *  As a result, to work with .klib files generated by Kotlin/Native, a workaround is required.
     *  This involves generating these files in a dummy manner, ensuring that the compiler recognizes the sources and can proceed with compilation.
     *
     *  Platform.kt and ComposeUi.kt provide .klib dependencies.
     *
     *  ComposeRuntime.kt provides dependencies that can also be located using [findFiles].
     *  However, due to challenges with relative paths in a CI environment, this utility simplifies
     *  the process and eliminates the need for [findFiles] in such contexts.
     *
     *  https://kotlinlang.slack.com/archives/C013BA8EQSE/p1732453625647829
     */
    fun klibSourceFiles(): List<SourceFile> {
        return listOf(
            kotlin(
                "Platform.kt",
                """
                    package platform.UIKit
                    open class UIViewController
                """.trimIndent()
            ),
            kotlin(
                "ComposeUi.kt",
                """
                    package androidx.compose.ui.window
                    
                    import androidx.compose.runtime.Composable
                    import platform.UIKit.UIViewController
                    
                    fun ComposeUIViewController(content: @Composable () -> Unit): UIViewController = UIViewController()
                """.trimIndent()
            ),
            kotlin(
                "ComposeRuntime.kt",
                """
                    package androidx.compose.runtime

                    @MustBeDocumented
                    @Retention(AnnotationRetention.BINARY)
                    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
                    annotation class Composable
                    
                    interface MutableState<T> { var value: T }
                    fun <T> mutableStateOf(initialValue: T): MutableState<T> {
                        return object : MutableState<T> {
                            override var value: T = initialValue
                        }
                    }
                """.trimIndent()
            )
        )
    }
}
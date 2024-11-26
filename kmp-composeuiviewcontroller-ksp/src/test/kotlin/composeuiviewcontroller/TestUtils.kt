@file:Suppress("MemberVisibilityCanBePrivate")

package composeuiviewcontroller

import java.io.File

object TestUtils {

    /**
     * Searches for files in a given Gradle cache path for specified package names.
     *
     * @param basePath The root directory to start the search (e.g., ~/.gradle/caches/modules-2/files-2.1).
     * @param packages The list of package names to search for (e.g., "androidx.compose.runtime").
     * @param extension The file extension
     * @return A list of Files pointing to the .jar files found for the specified packages.
     */
    fun findFiles(basePath: String, packages: List<String>, extension: String, exclude: List<String> = emptyList(), verbose: Boolean = false): List<File> {
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
                        if(verbose) {
                            println("> ${file.name} found!")
                        }
                    }
                }
            }
        }
        return files
    }
}
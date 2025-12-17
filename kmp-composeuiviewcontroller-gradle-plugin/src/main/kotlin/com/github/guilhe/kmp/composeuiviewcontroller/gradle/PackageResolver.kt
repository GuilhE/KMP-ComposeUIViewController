@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

/**
 * Resolves package names from Kotlin source sets with caching support.
 */
internal class PackageResolver(private val project: Project, private val logger: Logger) {
    private var cachedPackages: Set<String>? = null

    /**
     * Retrieves package names from commonMain source set.
     * Results are cached after first call.
     */
    fun resolvePackages(): Set<String> {
        cachedPackages?.let {
            logger.debug("\t> Using cached packages: {}", it)
            return it
        }

        val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
        val packages = HashSet<String>()

        commonMainSourceSet.kotlin.srcDirs.asSequence()
            .filter { it.exists() && it.isDirectory }
            .forEach { dir ->
                logger.debug("\t> Scanning directory: ${dir.absolutePath}")

                dir.walkTopDown()
                    .maxDepth(10)
                    .onEnter { file -> !file.name.startsWith(".") && file.name != "build" }
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        val relativePath = file.relativeTo(dir).parentFile?.path ?: ""
                        if (relativePath.isNotEmpty()) {
                            val packagePath = relativePath.replace(File.separatorChar, '.')
                            packages.add(packagePath)
                        }
                    }
            }

        logger.info("\t> Module packages found: $packages")

        if (packages.isEmpty()) {
            val searchedPaths = commonMainSourceSet.kotlin.srcDirs.joinToString { it.absolutePath }
            throw PluginConfigurationException(
                "Could not determine project's package. Searched in: $searchedPaths"
            )
        }

        cachedPackages = packages
        return packages
    }

    fun clearCache() {
        cachedPackages = null
        logger.debug("\t> Package cache cleared")
    }
}

@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle.utils

import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PluginConfigurationException
import java.io.File
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

/**
 * Resolves package names from Kotlin source sets with caching support.
 */
internal class PackageResolver(private val project: Project, private val logger: Logger) {
	private var cachedPackages: Set<String>? = null

	/**
	 * Retrieves package names from commonMain and from the source sets compiled into the iOS targets (iosMain, appleMain, ...),
	 * since `@ComposeUIViewController` functions are usually declared in iosMain and might live in packages that don't exist in commonMain.
	 * Results are cached after first call.
	 */
	fun resolvePackages(): Set<String> {
		cachedPackages?.let {
			logger.debug("\t> Using cached packages: {}", it)
			return it
		}

		val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
		val commonMainSourceSet = kmp.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
		// The default hierarchy template (iosMain, appleMain, ...) is applied by KGP after this plugin's afterEvaluate,
		// so those source sets might not exist yet. Their conventional directories are scanned as well.
		val hierarchySrcDirs = IOS_HIERARCHY_SOURCE_SETS.map { File(project.projectDir, "src/$it/kotlin") }
		val iosSourceSets = kmp.targets
			.filterIsInstance<KotlinNativeTarget>()
			.filter { it.konanTarget.family == Family.IOS }
			.mapNotNull { it.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME) }
			.flatMap { it.allKotlinSourceSets }
		val sourceSets = (listOf(commonMainSourceSet) + iosSourceSets).distinct()
		val packages = HashSet<String>()

		(sourceSets.asSequence().flatMap { it.kotlin.srcDirs.asSequence() } + hierarchySrcDirs)
			.distinct()
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
			val searchedPaths = (sourceSets.flatMap { it.kotlin.srcDirs } + hierarchySrcDirs).distinct().joinToString { it.absolutePath }
			throw PluginConfigurationException(
				"Could not determine project's package. Searched in: $searchedPaths"
			)
		}

		cachedPackages = packages
		return packages
	}

	private companion object {
		val IOS_HIERARCHY_SOURCE_SETS = listOf("iosMain", "appleMain", "nativeMain")
	}

	fun clearCache() {
		cachedPackages = null
		logger.debug("\t> Package cache cleared")
	}
}

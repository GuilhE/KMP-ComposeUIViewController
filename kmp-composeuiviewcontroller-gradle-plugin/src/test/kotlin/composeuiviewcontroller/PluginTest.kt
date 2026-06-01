@file:Suppress("SpellCheckingInspection")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.ERROR_MISSING_FRAMEWORK_CONFIG
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.ERROR_MISSING_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.ERROR_MISSING_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_COPY_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.FILE_NAME_SPM_SCRIPT_TEMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.INFO_MODULE_NAME_BY_FRAMEWORK
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.INFO_MODULE_NAME_BY_SWIFT_EXPORT
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.KSP_ARG_METADATA_HASH
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.LIB_ANNOTATIONS_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.LIB_GROUP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.LIB_KSP_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_APP_NAME
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_GROUP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_IOS_DEPLOYMENT_TARGET
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_KEEP_FILE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_KMP_MODULE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_SWIFT_TOOLS_VERSION
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PARAM_TARGET
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PLUGIN_KMP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PLUGIN_KSP
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.PluginParameters
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_COPY_FILES_TO_XCODE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EMBED_SWIFT_EXPORT_FOR_XCODE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_EXPORT_TO_SPM
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_FORMAT_SWIFT_FILES
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_SETUP_SPM_PACKAGE
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_SYNC_FRAMEWORK
import com.github.guilhe.kmp.composeuiviewcontroller.gradle.TASK_VALIDATE_REPRESENTABLES
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.gradle.api.internal.project.DefaultProject
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension
import org.jetbrains.kotlin.konan.target.Family

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
	fun `Method setupTargets adds KSP dependencies only to iOS targets and annotations to commonMain`() {
		val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
		kotlin.apply {
			jvm()
			iosArm64()
			iosSimulatorArm64()
		}

		val commonMain = kotlin.sourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME)
		val implementationConfig = project.configurations.getByName(commonMain.implementationConfigurationName)
		assertTrue(implementationConfig.dependencies.any { it.group == LIB_GROUP && it.name == LIB_ANNOTATIONS_NAME })

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
			assertEquals("ComposablesFramework", moduleMetadata.frameworkBaseName)
			assertTrue(moduleMetadata.packageNames.any { p -> p.startsWith("com.composables.module") })
		}
	}

	@Test
	fun `embed and sync tasks finalize via formatSwiftFiles chain`() {
		with(project) {
			extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
				iosSimulatorArm64().binaries.framework { baseName = "TestFramework" }
			}

			val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
			File(folder, "File.kt").writeText("package com.test\nclass Test()")

			(this as DefaultProject).evaluate()

			// embedAndSign is always registered by KMP for iOS framework targets
			val embedTask = tasks.findByName(TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE)
			assertNotNull(embedTask, "KMP should register $TASK_EMBED_AND_SING_APPLE_FRAMEWORK_FOR_XCODE for iOS framework targets")
			assertEquals(TASK_FORMAT_SWIFT_FILES, embedTask.finalizedBy.getDependencies(embedTask).first().name)

			// syncFramework registration depends on KMP version/configuration — verify if present
			tasks.findByName(TASK_SYNC_FRAMEWORK)?.let { syncTask ->
				assertEquals(TASK_FORMAT_SWIFT_FILES, syncTask.finalizedBy.getDependencies(syncTask).first().name)
			}

			val formatTask = tasks.getByName(TASK_FORMAT_SWIFT_FILES)
			val formatFinalizers = formatTask.finalizedBy.getDependencies(formatTask)
			assertEquals(1, formatFinalizers.size)
			assertEquals(TASK_COPY_FILES_TO_XCODE, formatFinalizers.first().name)
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
	fun `Task copyFilesToXcode clears temp files on success`() {
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

		val result = Templates.runGradle(projectDir, args = listOf(TASK_COPY_FILES_TO_XCODE))
		assertTrue(result.output.contains("BUILD SUCCESSFUL"))

		val tempFile = File("$projectDir/build/$TEMP_FILES_FOLDER/$FILE_NAME_COPY_SCRIPT_TEMP")
		assertFalse(tempFile.exists())
	}

	// region retrieveFrameworkBaseNamesFromIosTargets

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

	// endregion

	// region PackageResolver

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
	fun `Method PackageResolver successfully retrieves package information`() {
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

		val script = File("$projectDir/build/$TEMP_FILES_FOLDER/$FILE_NAME_COPY_SCRIPT_TEMP")
		assertTrue(script.exists())

		val modifiedScriptContent = script.readText()
		assertTrue(modifiedScriptContent.contains("$PARAM_KMP_MODULE=\"${projectDir.name}\""))
		assertTrue(modifiedScriptContent.contains("$PARAM_FOLDER=\"iosFolder\""))
		assertTrue(modifiedScriptContent.contains("$PARAM_APP_NAME=\"iosApp\""))
		assertTrue(modifiedScriptContent.contains("$PARAM_TARGET=\"iosTarget\""))
		assertTrue(modifiedScriptContent.contains("$PARAM_GROUP=\"Composables\""))
	}

	// endregion

	// region KSP cache invalidation

	@Test
	fun `configureKspTasksForCacheInvalidation declares metadata file as Gradle input to ksp tasks`() {
		with(project) {
			val fakeKspTaskProvider = tasks.register("kspFakeForCacheTest")

			extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
				iosSimulatorArm64().binaries.framework { baseName = "TestFramework" }
			}

			// Source file is needed so PackageResolver can find a package and write the metadata.
			val srcDir = File(rootProject.rootDir, "src/commonMain/kotlin/com/test").apply { mkdirs() }
			File(srcDir, "Test.kt").writeText("package com.test\nclass Test()")

			(this as DefaultProject).evaluate()

			val metadataFile = rootProject.layout.buildDirectory.file("$TEMP_FILES_FOLDER/$FILE_NAME_ARGS").get().asFile
			assertTrue(metadataFile.exists(), "$FILE_NAME_ARGS must exist after evaluate() — writeModuleMetadataToDisk should have created it")

			val fakeKspTask = fakeKspTaskProvider.get()
			val resolvedInputPaths = fakeKspTask.inputs.files.files.map { it.absolutePath }

			assertTrue(
				resolvedInputPaths.any { it == metadataFile.absolutePath },
				"Task '${fakeKspTask.name}' must declare '$FILE_NAME_ARGS' as a Gradle task input " +
					"so that Gradle's UP-TO-DATE check is invalidated whenever the metadata changes. " +
					"Resolved inputs found: ${resolvedInputPaths.map { File(it).name }}"
			)
		}
	}

	@Test
	fun `configureKspTasksForCacheInvalidation propagates metadata content hash to KSP processor arguments`() {
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")

		val result = Templates.runGradle(projectDir)

		// The log line is emitted inside afterEvaluate (eager, not lazy) once the metadata file
		// exists. Its presence confirms that KspExtension.arg(KSP_ARG_METADATA_HASH, hash) was
		// called successfully, which is the only observable effect of Layer 2.
		assertTrue(
			result.output.contains("Passed metadata hash") && result.output.contains(KSP_ARG_METADATA_HASH),
			"Build output should contain the metadata-hash confirmation log. " +
				"This proves KspExtension.arg('$KSP_ARG_METADATA_HASH', …) was called, " +
				"which busts KSP's internal incremental cache when metadata changes."
		)
	}

	// endregion

	// region experimentalSpmExport

	@Test
	fun `experimentalSpmExport throws when Swift Export is not configured`() {
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            ComposeUiViewController {
                experimentalSpmExport = true
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")

		val result = Templates.runGradle(projectDir, expectFailure = true)
		assertTrue(result.output.contains("experimentalSpmExport requires Swift Export"))
	}

	@Test
	fun `exportToSpm and setupRepresentablesSpmPackage tasks are registered and copyFilesToXcode is not in SPM mode`() {
		with(project) {
			extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
				val swiftExport = extensions.getByType(SwiftExportExtension::class.java)

				iosSimulatorArm64()
				swiftExport.moduleName.set("TestModule")
			}
			extensions.configure(PluginParameters::class.java) {
				it.experimentalSpmExport = true
			}

			val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
			File(folder, "File.kt").writeText("package com.test\nclass Test()")

			(this as DefaultProject).evaluate()

			assertNotNull(tasks.findByName(TASK_EXPORT_TO_SPM))
			assertNotNull(tasks.findByName(TASK_SETUP_SPM_PACKAGE))
			assertNull(tasks.findByName(TASK_COPY_FILES_TO_XCODE))
		}
	}

	@Test
	fun `exportToSpm script substitutions are applied correctly`() {
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
                iosSimulatorArm64()
                swiftExport {
                    moduleName = "MyModule"
                }
            }
            ComposeUiViewController {
                experimentalSpmExport = true
                iosAppFolderName = "iosFolder"
                iosAppName = "MyApp"
                targetName = "MyTarget"
                exportFolderName = "Composables"
                iosDeploymentTarget = "17"
                swiftToolsVersion = "5.9"
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")

		val result = Templates.runGradle(
			projectDir,
			args = listOf(TASK_EXPORT_TO_SPM, "-P$PARAM_KEEP_FILE=true", "--stacktrace")
		)
		assertTrue(result.output.contains("BUILD SUCCESSFUL"))

		val script = File("$projectDir/build/$TEMP_FILES_FOLDER/$FILE_NAME_SPM_SCRIPT_TEMP")
		assertTrue(script.exists())

		val content = script.readText()
		assertTrue(content.contains("$PARAM_KMP_MODULE=\"${projectDir.name}\""))
		assertTrue(content.contains("$PARAM_FOLDER=\"iosFolder\""))
		assertTrue(content.contains("$PARAM_APP_NAME=\"MyApp\""))
		assertTrue(content.contains("$PARAM_TARGET=\"MyTarget\""))
		assertTrue(content.contains("$PARAM_GROUP=\"Composables\""))
		assertTrue(content.contains("$PARAM_IOS_DEPLOYMENT_TARGET=\"17\""))
		assertTrue(content.contains("$PARAM_SWIFT_TOOLS_VERSION=\"5.9\""))
	}

	@Test
	fun `only embedSwiftExportForXcode triggers finalization chain in SPM mode`() {
		with(project) {
			extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
				val swiftExport = extensions.getByType(SwiftExportExtension::class.java)

				iosSimulatorArm64()
				swiftExport.moduleName.set("TestModule")
			}
			extensions.configure(PluginParameters::class.java) {
				it.experimentalSpmExport = true
			}

			val folder = File(projectDir.path, "src/commonMain/kotlin/com/test").apply { mkdirs() }
			File(folder, "File.kt").writeText("package com.test\nclass Test()")

			(this as DefaultProject).evaluate()

			val embedSwiftTask = tasks.findByName(TASK_EMBED_SWIFT_EXPORT_FOR_XCODE)
			assertNotNull(embedSwiftTask, "KMP should register $TASK_EMBED_SWIFT_EXPORT_FOR_XCODE with swiftExport config")
			val finalizers = embedSwiftTask.finalizedBy.getDependencies(embedSwiftTask)
			assertEquals(1, finalizers.size)
			assertEquals(TASK_FORMAT_SWIFT_FILES, finalizers.first().name)

			val formatTask = tasks.getByName(TASK_FORMAT_SWIFT_FILES)
			val formatFinalizers = formatTask.finalizedBy.getDependencies(formatTask)
			assertEquals(1, formatFinalizers.size)
			assertEquals(TASK_EXPORT_TO_SPM, formatFinalizers.first().name)
		}
	}

	@Test
	fun `validateRepresentables in SPM mode warns on missing Package swift and fails on empty Sources`() {
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
                iosSimulatorArm64()
                swiftExport {
                    moduleName = "TestModule"
                }
            }
            ComposeUiViewController {
                experimentalSpmExport = true
                iosAppFolderName = "iosApp"
                iosAppName = "TestApp"
                targetName = "TestApp"
                exportFolderName = "Representables"
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")

		// KSP output exists but Sources/ is empty — Package.swift missing → warn, empty Sources → fail
		val kspDir = File(projectDir, "build/generated/ksp/iosSimulatorArm64Main/kotlin").also { it.mkdirs() }
		File(kspDir, "FooRepresentable.swift").writeText("// generated")

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES), expectFailure = true)
		assertTrue(result.output.contains("[WARN]"))
		assertTrue(result.output.contains("Package.swift"))
		assertTrue(result.output.contains("[FAIL]"))
		assertTrue(result.output.contains("Sources dir is empty"))
	}

	@Test
	fun `autoExport false in SPM mode does not finalize tasks`() {
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
                iosSimulatorArm64()
                swiftExport {
                    moduleName = "TestModule"
                }
            }
            ComposeUiViewController {
                experimentalSpmExport = true
                autoExport = false
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")

		val result = Templates.runGradle(projectDir, args = listOf("help", "--stacktrace"))
		assertFalse(result.output.contains("will be finalizedBy"))
	}

	// endregion

	// region validateRepresentables

	@Test
	fun `validateRepresentables fails when KSP output has no Swift files`() {
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
		File(projectDir, "build/generated/ksp").mkdirs()

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES), expectFailure = true)
		assertTrue(result.output.contains("[FAIL]"))
		assertTrue(result.output.contains("No Swift files in KSP output"))
	}

	@Test
	fun `validateRepresentables fails when KSP has files but destination is empty`() {
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
		File(projectDir, "build/generated/ksp/iosSimulatorArm64Main/kotlin").apply { mkdirs() }
			.also { File(it, "FooRepresentable.swift").writeText("// generated") }

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES), expectFailure = true)
		assertTrue(result.output.contains("[FAIL]"))
		assertTrue(result.output.contains("destination is empty"))
	}

	@Test
	fun `validateRepresentables fails when file is in KSP output but missing from destination`() {
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
		val kspDir = File(projectDir, "build/generated/ksp/iosSimulatorArm64Main/kotlin").also { it.mkdirs() }
		File(kspDir, "FooRepresentable.swift").writeText("// generated")
		val destDir = File(projectDir, "iosApp/Representables").also { it.mkdirs() }
		File(destDir, "BarRepresentable.swift").writeText("// other")

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES), expectFailure = true)
		assertTrue(result.output.contains("[FAIL]"))
		assertTrue(result.output.contains("FooRepresentable.swift"))
	}

	@Test
	fun `validateRepresentables fails when Representable is not referenced in xcodeproj`() {
		val fileName = "FooRepresentable.swift"
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
		val kspDir = File(projectDir, "build/generated/ksp/iosSimulatorArm64Main/kotlin").also { it.mkdirs() }
		File(kspDir, fileName).writeText("// generated")
		val destDir = File(projectDir, "iosApp/Representables").also { it.mkdirs() }
		File(destDir, fileName).writeText("// generated")
		val xcodeDir = File(projectDir, "iosApp/iosApp.xcodeproj").also { it.mkdirs() }
		File(xcodeDir, "project.pbxproj").writeText("/* no swift references here */")

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES), expectFailure = true)
		assertTrue(result.output.contains("[FAIL]"))
		assertTrue(result.output.contains("Not referenced in xcodeproj"))
		assertTrue(result.output.contains(fileName))
	}

	@Test
	fun `validateRepresentables warns but passes when destination has stale files`() {
		val fileName = "FooRepresentable.swift"
		val staleFileName = "StaleRepresentable.swift"
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
		val kspDir = File(projectDir, "build/generated/ksp/iosSimulatorArm64Main/kotlin").also { it.mkdirs() }
		File(kspDir, fileName).writeText("// generated")
		val destDir = File(projectDir, "iosApp/Representables").also { it.mkdirs() }
		File(destDir, fileName).writeText("// generated")
		File(destDir, staleFileName).writeText("// stale")
		val xcodeDir = File(projectDir, "iosApp/iosApp.xcodeproj").also { it.mkdirs() }
		File(xcodeDir, "project.pbxproj").writeText("AABB /* $fileName */ = {isa = PBXFileReference;};")

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES))
		assertTrue(result.output.contains("BUILD SUCCESSFUL"))
		assertTrue(result.output.contains("[WARN]"))
		assertTrue(result.output.contains(staleFileName))
	}

	@Test
	fun `validateRepresentables passes when all checks succeed`() {
		val fileName = "FooRepresentable.swift"
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
                iosSimulatorArm64 {
                    binaries.framework { baseName = "TestFramework" }
                }
            }
            """
		)
		Templates.writeSettingsGradle(projectDir, rootProjectName = "testProject")
		val kspDir = File(projectDir, "build/generated/ksp/iosSimulatorArm64Main/kotlin").also { it.mkdirs() }
		File(kspDir, fileName).writeText("// generated")
		val destDir = File(projectDir, "iosApp/Representables").also { it.mkdirs() }
		File(destDir, fileName).writeText("// generated")
		val xcodeDir = File(projectDir, "iosApp/iosApp.xcodeproj").also { it.mkdirs() }
		File(xcodeDir, "project.pbxproj").writeText("AABB /* $fileName */ = {isa = PBXFileReference;};")

		val result = Templates.runGradle(projectDir, args = listOf(TASK_VALIDATE_REPRESENTABLES))
		assertTrue(result.output.contains("BUILD SUCCESSFUL"))
		assertTrue(result.output.contains("[OK] KSP output"))
		assertTrue(result.output.contains("[OK] Destination"))
		assertTrue(result.output.contains("[OK] KSP output and destination are in sync"))
		assertTrue(result.output.contains("[OK] All 1 Representable(s) referenced in xcodeproj"))
		assertTrue(result.output.contains("Validation passed"))
	}

	// endregion

	private companion object {
		private const val PLUGIN_ID = "io.github.guilhe.kmp.plugin-composeuiviewcontroller"
	}
}
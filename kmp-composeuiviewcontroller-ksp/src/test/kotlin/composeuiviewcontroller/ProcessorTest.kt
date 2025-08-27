@file:Suppress("TestFunctionName", "SpellCheckingInspection")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.EmptyFrameworkBaseNameException
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.InvalidParametersException
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import composeuiviewcontroller.Templates.CodeTemplates
import composeuiviewcontroller.Templates.CodeTemplates.screenWithParameterType
import composeuiviewcontroller.Templates.DATA_PACKAGE
import composeuiviewcontroller.Templates.ExpectedOutputs
import composeuiviewcontroller.Templates.ModuleConfigs
import composeuiviewcontroller.Templates.TestFileUtils
import composeuiviewcontroller.Templates.TestFileUtils.findGeneratedSwiftFile
import composeuiviewcontroller.TestUtils.klibSourceFiles
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class ProcessorTest {

    private lateinit var tempFolder: File
    private lateinit var tempArgs: File
    private var usesKsp2: Boolean = false

    private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
        return KotlinCompilation().apply {
            useKsp2()
            symbolProcessorProviders += ProcessorProvider()
            sources = sourceFiles.asList()
            workingDir = tempFolder
            inheritClassPath = true
            verbose = false
            usesKsp2 = precursorTools.contains("ksp2")
            if (!usesKsp2) {
                languageVersion = "1.9"
            }
        }
    }

    @BeforeTest
    fun setup() {
        val buildFolder = File("build/$TEMP_FILES_FOLDER").apply { mkdirs() }
        tempFolder = File(buildFolder, "test/src").apply { mkdirs() }
        tempArgs = File(buildFolder, FILE_NAME_ARGS).apply {
            parentFile.mkdirs()
            writeText("[]")
        }
    }

    @AfterTest
    fun clean() {
        tempArgs.delete()
        tempFolder.deleteRecursively()
    }

    @Test
    fun `Not using @ComposeUIViewController will not generate any file`() {
        val code = CodeTemplates.screenWithoutAnnotations()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(TestFileUtils.hasNoGeneratedFiles(compilation))
    }

    @Test
    fun `When frameworkBaseName is provided via ModulesJson it overrides @ComposeUIViewController frameworkBaseName value`() {
        tempArgs.writeText(ModuleConfigs.singleModule())
        val code = CodeTemplates.screenWithFrameworkOverride(annotationFramework = "MyFramework")
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertContains(generatedSwiftFiles.first().readText(), "import ComposablesFramework")
    }

    @Test
    fun `Empty frameworkBaseName in ModulesJson falls back to frameworkBaseName in @ComposeUIViewController`() {
        tempArgs.writeText(ModuleConfigs.singleModule(framework = ""))
        val code = CodeTemplates.basicScreenWithState(framework = "ComposablesFramework")
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(generatedSwiftFile, ExpectedOutputs.swiftRepresentableWithState())
    }

    @Test
    fun `Empty frameworkBaseName in ModulesJson and @ComposeUIViewController throws EmptyFrameworkBaseNameException`() {
        val code = CodeTemplates.screenWithEmptyFramework()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, EmptyFrameworkBaseNameException().message!!)
    }

    @Test
    fun `Not using @ComposeUIViewControllerState will generate files without state`() {
        val code = CodeTemplates.screenWithoutState()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedKotlinFiles = TestFileUtils.findGeneratedKotlinFile(compilation, "ScreenUIViewController.kt")
        assertTrue(generatedKotlinFiles.isNotEmpty())

        val generatedKotlinFile = generatedKotlinFiles.first().readText()
        assertEquals(
            generatedKotlinFile, ExpectedOutputs.kotlinUIViewControllerWithoutState(
                params = "data: SomeClass, value: Int, callBack: () -> Unit"
            )
        )

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(
            generatedSwiftFile, ExpectedOutputs.swiftRepresentableWithoutState(
                params = listOf(
                    "data" to "SomeClass",
                    "value" to "KotlinInt",
                    "callBack" to "() -> Void"
                )
            )
        )
    }

    @Test
    fun `Only 1 @ComposeUIViewControllerState is allowed`() {
        val code = CodeTemplates.screenWithMultipleStateAnnotations()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @Test
    fun `@Composable functions are not allowed as parameter`() {
        val code = CodeTemplates.screenWithComposableParameter()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, InvalidParametersException().message!!)
    }

    @Test
    fun `Composable functions properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate respective UIViewController and UIViewControllerRepresentable files`() {
        val code = CodeTemplates.multipleScreensWithDifferentStates()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            TestFileUtils.countGeneratedFiles(
                compilation,
                "ScreenAUIViewController.kt", "ScreenAUIViewControllerRepresentable.swift",
                "ScreenBUIViewController.kt", "ScreenBUIViewControllerRepresentable.swift"
            ),
            4
        )

        val generatedKotlinFiles = TestFileUtils.findGeneratedKotlinFile(compilation, "ScreenAUIViewController.kt")
        val generatedKotlinFile = generatedKotlinFiles.first().readText()
        assertEquals(generatedKotlinFile, ExpectedOutputs.kotlinUIViewControllerWithState())

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenAUIViewControllerRepresentable.swift")
        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(generatedSwiftFile, ExpectedOutputs.swiftRepresentableWithState(functionName = "ScreenA", stateType = "ViewAState"))
    }

    @Test
    fun `Composable functions from different files are parsed once only once`() {
        val data = CodeTemplates.viewStateFile(packageName = DATA_PACKAGE)
        val codeA = CodeTemplates.screenWithCrossPackageImport(functionName = "ScreenA")
        val codeB = CodeTemplates.screenWithCrossPackageImport(functionName = "ScreenB")

        val compilation = prepareCompilation(
            kotlin("ScreenA.kt", codeA),
            kotlin("ScreenB.kt", codeB),
            kotlin("Data.kt", data),
            *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            TestFileUtils.countGeneratedFiles(
                compilation,
                "ScreenAUIViewController.kt", "ScreenAUIViewControllerRepresentable.swift",
                "ScreenBUIViewController.kt", "ScreenBUIViewControllerRepresentable.swift"
            ),
            4
        )
    }

    @Test
    fun `Composable functions in the same file are parsed once only once`() {
        val code = CodeTemplates.multipleScreensInSameFile()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            TestFileUtils.countGeneratedFiles(
                compilation,
                "ScreenAUIViewController.kt", "ScreenAUIViewControllerRepresentable.swift",
                "ScreenBUIViewController.kt", "ScreenBUIViewControllerRepresentable.swift",
                "ScreenCUIViewController.kt", "ScreenCUIViewControllerRepresentable.swift"
            ),
            6
        )
    }

    @Test
    fun `Collection or Map parameters without type specification will throw TypeResolutionError`() {
        val testCases = listOf("List", "MutableList", "Set", "Map", "MutableMap")

        testCases.forEach { collectionType ->
            val code = screenWithParameterType(collectionType)
            val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())
            val result = compilation.compile()
            assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        }

        val codeWithReturnType = screenWithParameterType("List")
        val compilationReturn = prepareCompilation(kotlin("Screen.kt", codeWithReturnType), *klibSourceFiles().toTypedArray())
        val resultReturn = compilationReturn.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, resultReturn.exitCode)
    }

    @Test
    fun `Processor handles generic types in parameters`() {
        val code = CodeTemplates.screenWithGenericData()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val swiftContent = generatedSwiftFiles.first().readText()
        assertContains(swiftContent, "GenericData<KotlinInt>")
    }

    @Test
    fun `Processor handles complex nested generics correctly`() {
        val code = CodeTemplates.screenWithComplexGenerics()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val swiftContent = generatedSwiftFiles.first().readText()
        assertContains(swiftContent, "Dictionary<String, Array<Dictionary<String, KotlinInt>>>")
        assertContains(swiftContent, "Array<Dictionary<String, String>>")
    }

    @Test
    fun `Types imported from different KMP modules will not produce Swift files by default`() {
        tempArgs.writeText(ModuleConfigs.twoModules())

        val dataCode = CodeTemplates.dataFile()
        val screenCode = CodeTemplates.screenWithExternalDataDependency()

        val compilation = prepareCompilation(
            kotlin("Screen.kt", screenCode),
            kotlin("Data.kt", dataCode),
            *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = ExpectedOutputs.swiftRepresentableWithExternalDependency()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }

    @Test
    fun `Types imported from different KMP modules will produce Swift files with composed types when swiftExport flag is true`() {
        tempArgs.writeText(ModuleConfigs.twoModules(moduleASwiftExport = true))

        val dataCode = CodeTemplates.dataFile()
        val screenCode = CodeTemplates.screenWithExternalDataDependency()

        val compilation = prepareCompilation(
            kotlin("Screen.kt", screenCode),
            kotlin("Data.kt", dataCode),
            *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = ExpectedOutputs.swiftRepresentableWithExternalDependencies(
            framework = Templates.DEFAULT_FRAMEWORK,
            framework2 = Templates.FRAMEWORK_2,
            functionName = "Screen"
        )

        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }

    @Test
    fun `TypeAliasForExternalDependencies file will be created when external dependencies exist without flattenPackage configurations`() {
        tempArgs.writeText(ModuleConfigs.multiModule(swiftExportEnabled = true))

        val dataCode = CodeTemplates.dataFile()
        val stateCode = CodeTemplates.viewStateFile()
        val screenCode = CodeTemplates.screenWithExternalStateAndData()

        val compilation = prepareCompilation(
            kotlin("Screen.kt", screenCode),
            kotlin("Data.kt", dataCode),
            kotlin("State.kt", stateCode),
            *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "TypeAliasForExternalDependencies.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedTypeAliasSwiftOutput = ExpectedOutputs.swiftTypeAliasForExternalDependencies()
        assertEquals(generatedSwiftFiles.first().readText(), expectedTypeAliasSwiftOutput)
    }

    @Test
    fun `TypeAliasForExternalDependencies file will exclude external dependencies with flattenPackage configurations`() {
        tempArgs.writeText(
            ModuleConfigs.twoModules(
                moduleASwiftExport = true, moduleAFlattenPackage = true,
                moduleBSwiftExport = true, moduleBFlattenPackage = false,
            )
        )

        val dataCode = CodeTemplates.dataFile()
        val stateCode = CodeTemplates.viewStateFile()
        val screenCode = CodeTemplates.screenWithExternalStateAndData()

        val compilation = prepareCompilation(
            kotlin("Screen.kt", screenCode),
            kotlin("Data.kt", dataCode),
            kotlin("State.kt", stateCode),
            *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "TypeAliasForExternalDependencies.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())
        val expectedTypeAliasSwiftOutput = """
            // This file is auto-generated by KSP. Do not edit manually.
            // It contains typealias for external dependencies used in @ComposeUIViewController composables.
            // If you get errors about missing types, consider using the 'flattenPackage' property in KMP swiftExport settings.
            import ExportedKotlinPackages

            //This typealias can be avoided if you use the `flattenPackage = "com.mycomposable.data"` in KMP swiftExport settings
            typealias Data = ExportedKotlinPackages.com.mycomposable.data.Data
        """.trimIndent()
        assertEquals(generatedSwiftFiles.first().readText(), expectedTypeAliasSwiftOutput)
    }

    @Test
    fun `Function parameters with Kotlin types will map to ObjC-Swift export types`() {
        tempArgs.writeText(ModuleConfigs.singleModule())
        val code = CodeTemplates.screenWithKotlinTypes()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = ExpectedOutputs.swiftRepresentableWithObjCTypes()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }

    @Test
    fun `Function parameters with Kotlin types will map to Swift export types`() {
        tempArgs.writeText(ModuleConfigs.singleModule(swiftExportEnabled = true))
        val code = CodeTemplates.screenWithKotlinTypes()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = ExpectedOutputs.swiftRepresentableWithSwiftExportTypes()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }
}

@file:Suppress("TestFunctionName", "SpellCheckingInspection")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.EmptyFrameworkBaseNameException
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.InvalidParametersException
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerAnnotationName
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerStateAnnotationName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import composeuiviewcontroller.TestUtils.klibSourceFiles
import composeuiviewcontroller.Templates.CodeTemplates
import composeuiviewcontroller.Templates.ModuleConfigs
import composeuiviewcontroller.Templates.ExpectedOutputs
import composeuiviewcontroller.Templates.TestFileUtils
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

        val generatedSwiftFiles = TestFileUtils.findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertContains(generatedSwiftFiles.first().readText(), "import ComposablesFramework")
    }

    @Test
    fun `Empty frameworkBaseName in ModulesJson falls back to frameworkBaseName in @ComposeUIViewController`() {
        tempArgs.writeText(ModuleConfigs.singleModule(framework = "", swiftExportEnabled = false))
        val code = CodeTemplates.basicScreenWithState(framework = "ComposablesFramework")
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = TestFileUtils.findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
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
        assertEquals(generatedKotlinFile, ExpectedOutputs.kotlinUIViewControllerWithoutState(
            params = "data: SomeClass, value: Int, callBack: () -> Unit"
        ))

        val generatedSwiftFiles = TestFileUtils.findGeneratedSwiftFile(compilation, "ScreenUIViewControllerRepresentable.swift")
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(generatedSwiftFile, ExpectedOutputs.swiftRepresentableWithoutState(
            params = listOf(
                "data" to "SomeClass",
                "value" to "KotlinInt",
                "callBack" to "() -> Void"
            )
        ))
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
            TestFileUtils.countGeneratedFiles(compilation,
                "ScreenAUIViewController.kt", "ScreenAUIViewControllerRepresentable.swift",
                "ScreenBUIViewController.kt", "ScreenBUIViewControllerRepresentable.swift"
            ),
            4
        )

        val generatedKotlinFiles = TestFileUtils.findGeneratedKotlinFile(compilation, "ScreenAUIViewController.kt")
        val generatedKotlinFile = generatedKotlinFiles.first().readText()
        assertEquals(generatedKotlinFile, ExpectedOutputs.kotlinUIViewControllerWithState())

        val generatedSwiftFiles = TestFileUtils.findGeneratedSwiftFile(compilation, "ScreenAUIViewControllerRepresentable.swift")
        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(generatedSwiftFile, ExpectedOutputs.swiftRepresentableWithState(functionName = "ScreenA", stateType = "ViewAState"))
    }

    @Test
    fun `Composable functions from different files are parsed once only once`() {
        val data = CodeTemplates.dataPackageWithViewState()
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
            TestFileUtils.countGeneratedFiles(compilation,
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
            TestFileUtils.countGeneratedFiles(compilation,
                "ScreenAUIViewController.kt", "ScreenAUIViewControllerRepresentable.swift",
                "ScreenBUIViewController.kt", "ScreenBUIViewControllerRepresentable.swift",
                "ScreenCUIViewController.kt", "ScreenCUIViewControllerRepresentable.swift"
            ),
            6
        )
    }

    @Test
    fun `Collection or Map parameters without type specification will throw TypeResolutionError`() {
        var code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackB: (List) -> Unit
            ) { }
        """.trimIndent()
        var compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        var result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackC: (MutableList) -> Unit
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackE: (Set) -> Unit
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackD: (Map) -> Unit
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackE: (MutableMap) -> Unit                   
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackB: (List<String>) -> List
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @Test
    fun `Processor handles generic types in parameters`() {
        val code = """
            package com.mycomposable.test
            import ${'$'}composeUIViewControllerAnnotationName
            import androidx.compose.runtime.Composable
            
            data class GenericData<T>(val value: T)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(data: GenericData<Int>) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.toList().isNotEmpty())

        val swiftContent = generatedSwiftFiles.first().readText()
        assertContains(swiftContent, "GenericData<KotlinInt>")
    }

    @Test
    fun `Processor handles complex nested generics correctly`() {
        val code = """
            package com.mycomposable.test
            import ${'$'}composeUIViewControllerAnnotationName
            import androidx.compose.runtime.Composable
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                callback: (Map<String, List<Map<String, Int>>>) -> List<Map<String, String>>
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.toList().isNotEmpty())

        val swiftContent = generatedSwiftFiles.first().readText()
        assertContains(swiftContent, "Dictionary<String, Array<Dictionary<String, KotlinInt>>>")
        assertContains(swiftContent, "Array<Dictionary<String, String>>")
    }

    @Test
    fun `Types imported from different KMP modules will not produce Swift files by default`() {
        tempArgs.writeText(
            """
                [
                    {"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExportEnabled":false,"flattenPackageConfigured":false},
                    {"name":"module-data","packageNames":["com.mycomposable.data"],"frameworkBaseName":"ComposablesFramework2","swiftExportEnabled":false,"flattenPackageConfigured":false}
                ]
                """.trimIndent()
        )
        val data = """
            package com.mycomposable.data
            data class Data(val field: Int)
        """.trimIndent()
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.data.Data
            
            @ComposeUIViewController
            @Composable
            fun Screen(data: Data) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), kotlin("Data.kt", data), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import ComposablesFramework

            public struct ScreenRepresentable: UIViewControllerRepresentable {
                let data: Data

                public func makeUIViewController(context: Context) -> UIViewController {
                    ScreenUIViewController().make(data: data)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }

    @Test
    fun `Types imported from different KMP modules will produce Swift files with composed types when swiftExport flag is true`() {
        tempArgs.writeText(
            """
                [
                    {"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExportEnabled":true,"flattenPackageConfigured":false},
                    {"name":"module-data","packageNames":["com.mycomposable.data"],"frameworkBaseName":"ComposablesFramework2","swiftExportEnabled":true,"flattenPackageConfigured":false}
                ]
                """.trimIndent()
        )
        val data = """
            package com.mycomposable.data
            data class Data(val field: Int)
        """.trimIndent()
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.data.Data
            
            @ComposeUIViewController
            @Composable
            fun Screen(data: Data) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), kotlin("Data.kt", data), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import ComposablesFramework2
            import ComposablesFramework
            
            public struct ScreenRepresentable: UIViewControllerRepresentable {
                let data: Data

                public func makeUIViewController(context: Context) -> UIViewController {
                    ScreenUIViewController.shared.make(data: data)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }

    @Test
    fun `TypeAliasForExternalDependencies file will be created when external dependencies exist`() {
        tempArgs.writeText(
            """
                [
                    {"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExportEnabled":true,"flattenPackageConfigured":false},
                    {"name":"module-data","packageNames":["com.mycomposable.data"],"frameworkBaseName":"ComposablesFramework2","swiftExportEnabled":true,"flattenPackageConfigured":false},
                    {"name":"module-state","packageNames":["com.mycomposable.state"],"frameworkBaseName":"ComposablesFramework3","swiftExportEnabled":true,"flattenPackageConfigured":false}
                ]
                """.trimIndent()
        )
        val data = """
            package com.mycomposable.data
            data class Data(val field: Int)
        """.trimIndent()
        val state = """
            package com.mycomposable.state
            data class ViewState(val field: Int = 0)
        """.trimIndent()
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.data.Data
            import com.mycomposable.state.ViewState

            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState, data: Data) { }
        """.trimIndent()
        val compilation = prepareCompilation(
            kotlin("Screen.kt", code),
            kotlin("Data.kt", data),
            kotlin("State.kt", state),
            *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "TypeAliasForExternalDependencies.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedTypeAliasSwiftOutput = """
            // This file is auto-generated by KSP. Do not edit manually.
            // It contains typealias for external dependencies used in @ComposeUIViewController composables.
            // If you get errors about missing types, consider using the 'flattenPackage' property in KMP swiftExport settings.
            import ExportedKotlinPackages

            //This typealias can be avoided if you use the `flattenPackage = "com.mycomposable.test"` in KMP swiftExport settings
            typealias ScreenUIViewController = ExportedKotlinPackages.com.mycomposable.test.ScreenUIViewController
            //This typealias can be avoided if you use the `flattenPackage = "com.mycomposable.data"` in KMP swiftExport settings
            typealias Data = ExportedKotlinPackages.com.mycomposable.data.Data
            //This typealias can be avoided if you use the `flattenPackage = "com.mycomposable.state"` in KMP swiftExport settings
            typealias ViewState = ExportedKotlinPackages.com.mycomposable.state.ViewState
        """.trimIndent()
        assertEquals(generatedSwiftFiles.first().readText(), expectedTypeAliasSwiftOutput)
    }

    @Test
    fun `Function parameters with Kotlin types will map to ObjC-Swift export types`() {
        tempArgs.writeText("""[{"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExportEnabled":false,"flattenPackageConfigured":false}]""")
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.test.ViewState                       
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: (List<Map<String, List<Int>>>) -> List<String>,
                    callBackS: (Set<Int>) -> Unit,
                    callBackC: (MutableList<String>) -> Unit,
                    callBackD: (Map<String, String>) -> Unit,
                    callBackE: (MutableMap<String, String>) -> Unit,
                    callBackF: (Byte) -> Unit,
                    callBackG: (UByte) -> Unit,
                    callBackH: (Short) -> Unit,
                    callBackI: (UShort) -> Unit,
                    callBackJ: (Int) -> Unit,
                    callBackK: (UInt) -> Unit,
                    callBackL: (Long) -> Unit,
                    callBackM: (ULong) -> Unit,
                    callBackN: (Float) -> Unit,
                    callBackO: (Double) -> Unit,
                    callBackP: (Boolean) -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir.walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = """
                // This file is auto-generated by KSP. Do not edit manually.
                import SwiftUI
                import ComposablesFramework
                
                public struct ScreenRepresentable: UIViewControllerRepresentable {
                    @Binding var state: ViewState
                    let callBackA: () -> Void
                    let callBackB: (Array<Dictionary<String, Array<KotlinInt>>>) -> Array<String>
                    let callBackS: (Set<KotlinInt>) -> Void
                    let callBackC: (NSMutableArray<String>) -> Void
                    let callBackD: (Dictionary<String, String>) -> Void
                    let callBackE: (NSMutableDictionary<String, String>) -> Void
                    let callBackF: (KotlinByte) -> Void
                    let callBackG: (KotlinUByte) -> Void
                    let callBackH: (KotlinShort) -> Void
                    let callBackI: (KotlinUShort) -> Void
                    let callBackJ: (KotlinInt) -> Void
                    let callBackK: (KotlinUInt) -> Void
                    let callBackL: (KotlinLong) -> Void
                    let callBackM: (KotlinULong) -> Void
                    let callBackN: (KotlinFloat) -> Void
                    let callBackO: (KotlinDouble) -> Void
                    let callBackP: (KotlinBoolean) -> Void
                
                    public func makeUIViewController(context: Context) -> UIViewController {
                        ScreenUIViewController().make(callBackA: callBackA, callBackB: callBackB, callBackS: callBackS, callBackC: callBackC, callBackD: callBackD, callBackE: callBackE, callBackF: callBackF, callBackG: callBackG, callBackH: callBackH, callBackI: callBackI, callBackJ: callBackJ, callBackK: callBackK, callBackL: callBackL, callBackM: callBackM, callBackN: callBackN, callBackO: callBackO, callBackP: callBackP)
                    }
                
                    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                        ScreenUIViewController().update(state: state)
                    }
                }
        """.trimIndent()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }

    @Test
    fun `Function parameters with Kotlin types will map to Swift export types`() {
        tempArgs.writeText(
            """[{"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework",
            |"swiftExportEnabled":true,"flattenPackageConfigured":false}]""".trimMargin()
        )
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.test.ViewState                       
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: (List<Map<String, List<Int>>>) -> List<String>,
                    callBackS: (Set<Int>) -> Unit,
                    callBackC: (MutableList<String>) -> Unit,
                    callBackD: (Map<String, String>) -> Unit,
                    callBackE: (MutableMap<String, String>) -> Unit,
                    callBackF: (Byte) -> Unit,
                    callBackG: (UByte) -> Unit,
                    callBackH: (Short) -> Unit,
                    callBackI: (UShort) -> Unit,
                    callBackJ: (Int) -> Unit,
                    callBackK: (UInt) -> Unit,
                    callBackL: (Long) -> Unit,
                    callBackM: (ULong) -> Unit,
                    callBackN: (Float) -> Unit,
                    callBackO: (Double) -> Unit,
                    callBackP: (Boolean) -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir.walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import ComposablesFramework

            public struct ScreenRepresentable: UIViewControllerRepresentable {
                @Binding var state: ViewState
                let callBackA: () -> Void
                let callBackB: (Array<Dictionary<String, Array<Int32>>>) -> Array<String>
                let callBackS: (Set<Int32>) -> Void
                let callBackC: (Array<String>) -> Void
                let callBackD: (Dictionary<String, String>) -> Void
                let callBackE: (Dictionary<String, String>) -> Void
                let callBackF: (Int8) -> Void
                let callBackG: (UInt8) -> Void
                let callBackH: (Int16) -> Void
                let callBackI: (UInt16) -> Void
                let callBackJ: (Int32) -> Void
                let callBackK: (UInt32) -> Void
                let callBackL: (Int64) -> Void
                let callBackM: (UInt64) -> Void
                let callBackN: (Float) -> Void
                let callBackO: (Double) -> Void
                let callBackP: (Bool) -> Void

                public func makeUIViewController(context: Context) -> UIViewController {
                    ScreenUIViewController.shared.make(callBackA: callBackA, callBackB: callBackB, callBackS: callBackS, callBackC: callBackC, callBackD: callBackD, callBackE: callBackE, callBackF: callBackF, callBackG: callBackG, callBackH: callBackH, callBackI: callBackI, callBackJ: callBackJ, callBackK: callBackK, callBackL: callBackL, callBackM: callBackM, callBackN: callBackN, callBackO: callBackO, callBackP: callBackP)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ScreenUIViewController.shared.update(state: state)
                }
            }
        """.trimIndent()
        assertEquals(generatedSwiftFiles.first().readText(), expectedSwiftOutput)
    }
}

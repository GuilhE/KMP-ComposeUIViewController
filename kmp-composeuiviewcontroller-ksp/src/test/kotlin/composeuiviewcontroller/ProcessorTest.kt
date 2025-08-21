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
        val code = """
            package com.mycomposable.test
            
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)
            
            @Composable
            fun ScreenA(state: ViewState) { }
            
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(
            compilation.kspSourcesDir
                .walkTopDown()
                .filter { it.extension == "kt" || it.extension == "swift" }
                .toList()
                .isEmpty()
        )
    }

    @Test
    fun `When frameworkBaseName is provided via ModulesJson it overrides @ComposeUIViewController frameworkBaseName value`() {
        tempArgs.writeText("""[{"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExport":false}]""")
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("MyFramework")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
        assertContains(generatedSwiftFiles.first().readText(), "import ComposablesFramework")
    }

    @Test
    fun `Empty frameworkBaseName in ModulesJson falls back to frameworkBaseName in @ComposeUIViewController`() {
        tempArgs.writeText("""[{"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"","swiftExport":false}]""")
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
        assertContains(generatedSwiftFiles.first().readText(), "import ComposablesFramework")
    }

    @Test
    fun `Empty frameworkBaseName in ModulesJson and @ComposeUIViewController throws EmptyFrameworkBaseNameException`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, EmptyFrameworkBaseNameException().message!!)
    }

    @Test
    fun `Not using @ComposeUIViewControllerState will generate files without state`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import androidx.compose.runtime.Composable
            
            data class SomeClass(val field: Int)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(data: SomeClass, value: Int, callBack: () -> Unit) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val expectedKotlinOutput = """
            @file:Suppress("unused")
            package com.mycomposable.test

            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController

            object ScreenUIViewController {
                fun make(data: SomeClass, value: Int, callBack: () -> Unit): UIViewController {
                    return ComposeUIViewController {
                        Screen(data, value, callBack)
                    }
                }
            }
        """.trimIndent()
        val generatedKotlinFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewController.kt" }
            .toList()
        assertTrue(generatedKotlinFiles.isNotEmpty())

        val generatedKotlinFile = generatedKotlinFiles.first().readText()
        assertEquals(generatedKotlinFile, expectedKotlinOutput)

        val expectedSwiftOutput = """
            import SwiftUI
            import ComposablesFramework

            public struct ScreenRepresentable: UIViewControllerRepresentable {
                let data: SomeClass
                let value: KotlinInt
                let callBack: () -> Void

                public func makeUIViewController(context: Context) -> UIViewController {
                    ScreenUIViewController().make(data: data, value: value, callBack: callBack)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()
        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(generatedSwiftFile, expectedSwiftOutput)
    }

    @Test
    fun `Only 1 @ComposeUIViewControllerState is allowed`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState, @ComposeUIViewControllerState state2: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @Test
    fun `@Composable functions are not allowed as parameter`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,
                    callback: () -> Unit,
                    @Composable content: () -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, InvalidParametersException().message!!)
    }

    @Test
    fun `Composable functions properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate respective UIViewController and UIViewControllerRepresentable files`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            data class ViewAState(val field: Int)
            data class ViewBState(val field: Int)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewAState) { }
            
            private fun dummy() {}
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewBState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            compilation.kspSourcesDir
                .walkTopDown()
                .filter {
                    it.name == "ScreenAUIViewController.kt" || it.name == "ScreenAUIViewControllerRepresentable.swift" ||
                            it.name == "ScreenBUIViewController.kt" || it.name == "ScreenBUIViewControllerRepresentable.swift"
                }
                .toList()
                .size,
            4
        )

        val expectedKotlinOutput = """
            @file:Suppress("unused")
            package com.mycomposable.test

            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController

            object ScreenAUIViewController {
                private val state = mutableStateOf<ViewAState?>(null)

                fun make(): UIViewController {
                    return ComposeUIViewController {
                        state.value?.let { ScreenA(it) }
                    }
                }

                fun update(state: ViewAState) {
                    this.state.value = state
                }
            }
        """.trimIndent()
        val generatedKotlinFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenAUIViewController.kt" }
        val generatedKotlinFile = generatedKotlinFiles.first().readText()
        assertEquals(generatedKotlinFile, expectedKotlinOutput)

        val expectedSwiftOutput = """
            import SwiftUI
            import ComposablesFramework

            public struct ScreenARepresentable: UIViewControllerRepresentable {
                @Binding var state: ViewAState
                
                public func makeUIViewController(context: Context) -> UIViewController {
                    ScreenAUIViewController().make()
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ScreenAUIViewController().update(state: state)
                }
            }
        """.trimIndent()
        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenAUIViewControllerRepresentable.swift" }
        val generatedSwiftFile = generatedSwiftFiles.first().readText()
        assertEquals(generatedSwiftFile, expectedSwiftOutput)
    }

    @Test
    fun `Composable functions from different files are parsed once only once`() {
        val data = """
            package com.mycomposable.data
            data class ViewState(val field: Int = 0)
        """.trimIndent()
        val codeA = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.data.*
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val codeB = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            import com.mycomposable.data.*
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(
            kotlin("ScreenA.kt", codeA), kotlin("ScreenB.kt", codeB), kotlin("Data.kt", data), *klibSourceFiles().toTypedArray()
        )

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            compilation.kspSourcesDir.walkTopDown()
                .filter {
                    it.name == "ScreenAUIViewController.kt" || it.name == "ScreenAUIViewControllerRepresentable.swift" ||
                            it.name == "ScreenBUIViewController.kt" || it.name == "ScreenBUIViewControllerRepresentable.swift"
                }
                .toList()
                .size,
            4
        )
    }

    @Test
    fun `Composable functions in the same file are parsed once only once`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            import androidx.compose.runtime.*
            
            data class ViewState(val field: Int = 0)
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState, callBackA: () -> Unit) { }
            
            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun ScreenC(@ComposeUIViewControllerState state: ViewState, callBackA: () -> Unit, callBackB: () -> Unit) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code), *klibSourceFiles().toTypedArray())

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        assertEquals(
            compilation.kspSourcesDir.walkTopDown()
                .filter {
                    it.name == "ScreenAUIViewController.kt" || it.name == "ScreenAUIViewControllerRepresentable.swift" ||
                            it.name == "ScreenBUIViewController.kt" || it.name == "ScreenBUIViewControllerRepresentable.swift" ||
                            it.name == "ScreenCUIViewController.kt" || it.name == "ScreenCUIViewControllerRepresentable.swift"
                }
                .toList()
                .size,
            6
        )
    }

    @Test
    fun `Function parameters with Kotlin types will map to Swift types`() {
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
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackC: (MutableList) -> Unit
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code))
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackE: (Set) -> Unit
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code))
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackD: (Map) -> Unit
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code))
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackE: (MutableMap) -> Unit                   
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code))
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)

        code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            data class ViewState(val field: Int = 0)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBackB: (List<String>) -> List
            ) { }
        """.trimIndent()
        compilation = prepareCompilation(kotlin("Screen.kt", code))
        result = compilation.compile()
        assertEquals(if (usesKsp2) KotlinCompilation.ExitCode.INTERNAL_ERROR else KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @Test
    fun `Types imported from different KMP modules will not produce Swift files by default`() {
        tempArgs.writeText(
            """
                [
                    {"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExport":false},
                    {"name":"module-data","packageNames":["com.mycomposable.data"],"frameworkBaseName":"ComposablesFramework2","swiftExport":false}
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
            
            @ComposeUIViewController("ComposablesFramework")
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
                    {"name":"module-test","packageNames":["com.mycomposable.test"],"frameworkBaseName":"ComposablesFramework","swiftExport":true},
                    {"name":"module-data","packageNames":["com.mycomposable.data"],"frameworkBaseName":"ComposablesFramework2","swiftExport":true}
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
            
            @ComposeUIViewController("ComposablesFramework")
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
            import SwiftUI
            import ComposablesFramework2
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
}
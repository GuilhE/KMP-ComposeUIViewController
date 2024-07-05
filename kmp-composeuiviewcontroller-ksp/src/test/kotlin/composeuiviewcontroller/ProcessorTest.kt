@file:Suppress("TestFunctionName")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.EmptyFrameworkBaseNameException
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.InvalidParametersException
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerAnnotationName
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerStateAnnotationName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContains

@OptIn(ExperimentalCompilerApi::class)
class ProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder(File("build/tmp/test-module/src").also { it.mkdirs() })

    private lateinit var tempArgs: File

    private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
        tempArgs = File(
            File(tempFolder.root.parentFile.parentFile.parentFile.parentFile.path), //we need to reach module's ./build
            FILE_NAME_ARGS
        ).apply {
            writeText("[]")
        }
        return KotlinCompilation().apply {
            workingDir = tempFolder.root
            inheritClassPath = true
            symbolProcessorProviders = listOf(ProcessorProvider())
            sources = sourceFiles.asList()
            verbose = false
            kspIncremental = false
        }
    }

    @After
    fun clean() {
        tempArgs.delete()
    }

    @Test
    fun `Not using @ComposeUIViewController will not generate any file`() {
        val code = """
            package com.mycomposable.test
            
            import $composeUIViewControllerStateAnnotationName 
                                           
            @Composable
            fun ScreenA(state: ViewState) { }
            
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
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
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName

            data class ViewState(val field: Int)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        tempArgs.writeText("""[{"name":"module-test","packageName":"com.mycomposable.test","frameworkBaseName":"MyFramework"}]""")
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
        assertContains(generatedSwiftFiles.first().readText(), "import MyFramework")
    }

    @Test
    fun `Empty frameworkBaseName in ModulesJson falls back to frameworkBaseName in @ComposeUIViewController`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName

            data class ViewState(val field: Int)

            @ComposeUIViewController("ComposablesFramework")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        tempArgs.writeText("""[{"name":"module-test","packageName":"com.mycomposable.test","frameworkBaseName":""}]""")
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
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

            data class ViewState(val field: Int)

            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertContains(result.messages, EmptyFrameworkBaseNameException().message!!)
    }

    @Test
    fun `Not using @ComposeUIViewControllerState will generate files without state`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            
            data class SomeClass(val field: Int)
            
            @ComposeUIViewController("MyFramework")
            @Composable
            fun Screen(data: SomeClass, value: Int, callBack: () -> Unit) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)

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
            import MyFramework

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
            
            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState, @ComposeUIViewControllerState state2: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }

    @Test
    fun `Only 1 @ComposeUIViewControllerState and N function parameters (excluding @Composable) are allowed`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: (Int) -> Unit,
                    @Composable content: () -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertContains(result.messages, InvalidParametersException().message!!)
    }

    @Test
    fun `Composable functions properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate respective UIViewController and UIViewControllerRepresentable files`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            private data class ViewAState(val field: Int)
            private data class ViewBState(val field: Int)

            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewAState) { }
            
            private fun dummy() 
            
            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewBState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
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
            import MyFramework

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
            data class ViewState(val field: Int)
        """.trimIndent()
        val codeA = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import com.mycomposable.data.*

            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val codeB = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import com.mycomposable.data.*

            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("ScreenA.kt", codeA), kotlin("ScreenB.kt", codeB), kotlin("Data.kt", data))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
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
            
            private data class ViewState(val field: Int)

            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }

            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState, callBackA: () -> Unit) { }

            @ComposeUIViewController("MyFramework")
            @Composable
            fun ScreenC(@ComposeUIViewControllerState state: ViewState, callBackA: () -> Unit, callBackB: () -> Unit) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code, isMultiplatformCommonSource = true))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
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
    fun `Function parameter with Kotlin primitive type will map to Swift expected type`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import com.mycomposable.data.ViewState                       
            
            private data class ViewState(val field: Int)

            @ComposeUIViewController("MyFramework")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: (List) -> Unit,
                    callBackC: (MutableList) -> Unit,
                    callBackD: (Map) -> Unit,
                    callBackE: (MutableMap) -> Unit,
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
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        val generatedSwiftFiles = compilation.kspSourcesDir.walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())
        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)

        val expectedSwiftTypes = listOf(
            "Void",
            "Array",
            "NSMutableArray",
            "Dictionary",
            "NSMutableDictionary",
            "KotlinByte",
            "KotlinUByte",
            "KotlinShort",
            "KotlinUShort",
            "KotlinInt",
            "KotlinUInt",
            "KotlinLong",
            "KotlinULong",
            "KotlinFloat",
            "KotlinDouble",
            "KotlinBoolean"
        )
        val generatedCodeFile = generatedSwiftFiles.first().readText()
        for (expectedType in expectedSwiftTypes) {
            assertTrue(generatedCodeFile.contains(expectedType))
        }
    }

    @Test
    fun `Types imported from different KMP modules will produce Swift files with composed types`() {
        val data = """
            package com.mycomposable.data
            data class Data(val field: Int)
        """.trimIndent()
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            import com.mycomposable.data.Data

            @ComposeUIViewController("MyFramework")
            @Composable
            fun Screen(data: Data) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("Screen.kt", code), kotlin("Data.kt", data))
        tempArgs.writeText(
            """
                [
                    {"name":"module-test","packageName":"com.mycomposable.test","frameworkBaseName":"MyFramework"},
                    {"name":"module-data","packageName":"com.mycomposable.data","frameworkBaseName":"MyFramework2"}
                ]
                """.trimIndent()
        )
        val result = compilation.compile()
        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)

        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

        val expectedSwiftOutput = """
            import SwiftUI
            import MyFramework

            public struct ScreenRepresentable: UIViewControllerRepresentable {
                let data: Test_moduleData

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
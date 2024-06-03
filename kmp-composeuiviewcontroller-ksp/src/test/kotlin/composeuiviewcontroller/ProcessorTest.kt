@file:Suppress("TestFunctionName")

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerAnnotationName
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerStateAnnotationName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertContains

class ProcessorTest {

    @Rule
    @JvmField
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    private fun prepareCompilation(vararg sourceFiles: SourceFile): KotlinCompilation {
        return KotlinCompilation().apply {
            workingDir = temporaryFolder.root
            inheritClassPath = true
            symbolProcessorProviders = listOf(ProcessorProvider())
            sources = sourceFiles.asList()
            verbose = false
            kspIncremental = false
        }
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
    fun `Not using @ComposeUIViewControllerState will generate files without state`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName

            @ComposeUIViewController("SharedComposables")
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
            import SharedComposables
            
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
    fun `Empty frameworkName in @ComposeUIViewController throws IllegalStateException`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName

            @ComposeUIViewController("")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }

    @Test
    fun `Default frameworkName in @ComposeUIViewController will have the value 'SharedComposables'`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName

            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        val generatedSwiftFiles = compilation.kspSourcesDir
            .walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
        assertContains(generatedSwiftFiles.first().readText(), "import SharedComposables")
    }

    @Test
    fun `No more than one @ComposeUIViewControllerState is allowed`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
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
            
            @ComposeUIViewController("SharedComposables")
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
    }

    @Test
    fun `Composable functions properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate respective UIViewController and UIViewControllerRepresentable files`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewAState) { }
            
            private fun dummy() 
            
            @ComposeUIViewController("SharedComposables")
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
            import SharedComposables

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
        val codeA = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val codeB = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("ScreenA.kt", codeA), kotlin("ScreenB.kt", codeB))
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
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }

            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState, callBackA: () -> Unit) { }

            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenC(@ComposeUIViewControllerState state: ViewState, callBackA: () -> Unit, callBackB: () -> Unit) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
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
            
            @ComposeUIViewController("SharedComposables")
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
        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)

        val generatedSwiftFiles = compilation.kspSourcesDir.walkTopDown()
            .filter { it.name == "ScreenUIViewControllerRepresentable.swift" }
            .toList()
        assertTrue(generatedSwiftFiles.isNotEmpty())

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
}
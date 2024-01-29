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
import kotlin.test.assertNotEquals

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
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.extension == "kt" || it.extension == "swift" }
                .toList()
                .isEmpty()
        )
    }

    @Test
    fun `Not using @ComposeUIViewControllerState throws IllegalStateException`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName

            data class ViewState(val status: String = "default")
         
            @ComposeUIViewController("")
            @Composable
            fun Screen(state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }

    @Test
    fun `Empty frameworkName in @ComposeUIViewController throws IllegalStateException`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName

            data class ViewState(val status: String = "default")
         
            @ComposeUIViewController("")
            @Composable
            fun Screen(state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
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
        val file1 = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: () -> Unit,
                    @Composable content: () -> Unit
            ) { }
        """.trimIndent()
        val file2 = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun Screen(
                    modifier: Modifier,
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: () -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("File1.kt", file1), kotlin("File2.kt", file2))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }

    @Test
    fun `Composable Screens properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate ScreenUIViewController and ScreenUIViewControllerRepresentable files`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
            
            private fun dummy() 
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }

        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertEquals(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.name == "ScreenAUIViewController.kt" || it.name == "ScreenAUIViewControllerRepresentable.swift" ||
                        it.name == "ScreenBUIViewController.kt" || it.name == "ScreenBUIViewControllerRepresentable.swift"}
                .toList()
                .size,
            4
        )
    }

    @Test
    fun `Composables from different files are parsed once only once`() {
        val fileA = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val fileB = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("FileA.kt", fileA), kotlin("FileB.kt", fileB))
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
    fun `Composables in the same file are parsed once only once`() {
        val code = """
            package com.mycomposable.test
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }

            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenB(@ComposeUIViewControllerState uiState: ViewState, callBackA: () -> Unit) { }

            @ComposeUIViewController("SharedComposables")
            @Composable
            fun ScreenC(@ComposeUIViewControllerState screenState: ViewState, callBackA: () -> Unit, callBackB: () -> Unit) { }
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
        val file1 = """
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
        val compilation = prepareCompilation(kotlin("File1.kt", file1))
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
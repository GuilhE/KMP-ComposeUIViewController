package composeuiviewcontroller

import com.github.guilhe.ksp.composeuiviewcontroller.ProcessorProvider
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
                .filter { it.nameWithoutExtension == "ScreenAUIViewController" || it.nameWithoutExtension == "ScreenBUIViewController" }
                .toList()
                .isEmpty()
        )
    }

    @Test
    fun `Not using @ComposeUIViewControllerState throws IllegalStateException`() {
        val code = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController

            data class ViewState(val status: String = "default")
         
            @ComposeUIViewController
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
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
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
        val file1 = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
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
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
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
    fun `Composable Screen properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate ScreenUIViewController file`() {
        val code = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertTrue(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.nameWithoutExtension == "ScreenUIViewController" }
                .toList()
                .isNotEmpty()
        )
    }

    @Test
    fun `Composables from different files are parsed once only once`() {
        val fileA = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val fileB = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
            @Composable
            fun ScreenB(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        val compilation = prepareCompilation(kotlin("FileA.kt", fileA), kotlin("FileB.kt", fileB))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertEquals(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.nameWithoutExtension == "ScreenAUIViewController" || it.nameWithoutExtension == "ScreenBUIViewController" }
                .toList()
                .size,
            2
        )
    }

    @Test
    fun `Composables in the same file are parsed once only once`() {
        val code = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
            @Composable
            fun ScreenA(@ComposeUIViewControllerState state: ViewState) { }

            @ComposeUIViewController
            @Composable
            fun ScreenB(@ComposeUIViewControllerState uiState: ViewState, callBackA: () -> Unit) { }

            @ComposeUIViewController
            @Composable
            fun ScreenC(@ComposeUIViewControllerState screenState: ViewState, callBackB: () -> Unit) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertEquals(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.nameWithoutExtension == "ScreenAUIViewController" || it.nameWithoutExtension == "ScreenBUIViewController" || it.nameWithoutExtension == "ScreenCUIViewController" }
                .toList()
                .size,
            3
        )
    }
}
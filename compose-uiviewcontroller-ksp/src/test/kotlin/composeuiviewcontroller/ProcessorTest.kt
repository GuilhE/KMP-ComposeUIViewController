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
            fun MyScreen(state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertTrue(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.nameWithoutExtension == "MyScreenUIViewController" }
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
            fun MyScreen(state: ViewState) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }

    @Test
    fun `Composable MyScreen properly using @ComposeUIViewController and @ComposeUIViewControllerState will generate MyScreenUIViewController file`() {
        val code = """
            package com.mycomposable.test
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            import com.github.guilhe.ksp.composeuiviewcontroller.ComposeUIViewController
            
            @ComposeUIViewController
            @Composable
            fun MyScreen(
                    modifier: Modifier,
                    @ComposeUIViewControllerState state: ViewState,
                    callBackA: () -> Unit,
                    callBackB: () -> Unit,
                    content: @Composable () -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertTrue(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.nameWithoutExtension == "MyScreenUIViewController" }
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
            fun MyScreenA(@ComposeUIViewControllerState state: ViewState) { }

            @ComposeUIViewController
            @Composable
            fun MyScreenB(
                    modifier: Modifier,
                    @ComposeUIViewControllerState uiState: ViewState                    
            ) { }

            @ComposeUIViewController
            @Composable
            fun MyScreenC(
                    modifier: Modifier,
                    @ComposeUIViewControllerState screenState: ViewState,
                    callBackA: () -> Unit,
                    callBackB: () -> Unit,
                    content: @Composable () -> Unit
            ) { }
        """.trimIndent()
        val compilation = prepareCompilation(kotlin("Screen.kt", code))
        val result = compilation.compile()

        assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
        assertEquals(
            compilation.kspSourcesDir.walkTopDown()
                .filter { it.nameWithoutExtension == "MyScreenAUIViewController" || it.nameWithoutExtension == "MyScreenBUIViewController" || it.nameWithoutExtension == "MyScreenCUIViewController" }
                .toList()
                .size,
            3
        )
    }
}
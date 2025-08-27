@file:Suppress("TestFunctionName", "SpellCheckingInspection")
@file:OptIn(ExperimentalCompilerApi::class)

package composeuiviewcontroller

import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerAnnotationName
import com.github.guilhe.kmp.composeuiviewcontroller.ksp.composeUIViewControllerStateAnnotationName
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

object Templates {

    const val TEST_PACKAGE = "com.mycomposable.test"
    const val DATA_PACKAGE = "com.mycomposable.data"
    const val STATE_PACKAGE = "com.mycomposable.state"

    const val DEFAULT_FRAMEWORK = "ComposablesFramework"
    const val FRAMEWORK_2 = "ComposablesFramework2"
    const val FRAMEWORK_3 = "ComposablesFramework3"

    val COMMON_IMPORTS = """
        import $composeUIViewControllerAnnotationName
        import $composeUIViewControllerStateAnnotationName
        import androidx.compose.runtime.Composable
    """.trimIndent()

    object DataClasses {
        val viewState = """
            data class ViewState(val field: Int = 0)
        """.trimIndent()

        val viewAState = """
            data class ViewAState(val field: Int)
        """.trimIndent()

        val viewBState = """
            data class ViewBState(val field: Int)
        """.trimIndent()

        val someClass = """
            data class SomeClass(val field: Int)
        """.trimIndent()

        val genericData = """
            data class GenericData<T>(val value: T)
        """.trimIndent()

        val data = """
            data class Data(val field: Int)
        """.trimIndent()
    }

    object Composables {
        fun simpleComposableWithState(
            functionName: String = "Screen",
            stateType: String = "ViewState",
            framework: String = DEFAULT_FRAMEWORK,
            additionalParams: String = ""
        ) = """
            @ComposeUIViewController("$framework")
            @Composable
            fun $functionName(@ComposeUIViewControllerState state: $stateType${if (additionalParams.isNotEmpty()) ", $additionalParams" else ""}) { }
        """.trimIndent()

        fun simpleComposableWithoutState(
            functionName: String = "Screen",
            framework: String = DEFAULT_FRAMEWORK,
            params: String
        ) = """
            @ComposeUIViewController("$framework")
            @Composable
            fun $functionName($params) { }
        """.trimIndent()

        fun composableWithoutAnnotation(
            functionName: String = "Screen",
            stateType: String = "ViewState"
        ) = """
            @Composable
            fun $functionName(state: $stateType) { }
        """.trimIndent()

        fun composableWithStateAnnotationOnly(
            functionName: String = "Screen",
            stateType: String = "ViewState"
        ) = """
            @Composable
            fun $functionName(@ComposeUIViewControllerState state: $stateType) { }
        """.trimIndent()
    }

    object CodeTemplates {
        fun basicScreenWithState(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.viewState}
            
            ${Composables.simpleComposableWithState(framework = framework)}
        """.trimIndent()

        fun screenWithoutState(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.someClass}
            
            ${Composables.simpleComposableWithoutState(
                framework = framework,
                params = "data: SomeClass, value: Int, callBack: () -> Unit"
            )}
        """.trimIndent()

        fun screenWithoutAnnotations(packageName: String = TEST_PACKAGE) = """
            package $packageName
            
            import $composeUIViewControllerStateAnnotationName
            import androidx.compose.runtime.Composable
            
            ${DataClasses.viewState}
            
            ${Composables.composableWithoutAnnotation()}
            
            ${Composables.composableWithStateAnnotationOnly("ScreenB")}
        """.trimIndent()

        fun multipleScreensInSameFile(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            import androidx.compose.runtime.*
            
            ${DataClasses.viewState}
            
            ${Composables.simpleComposableWithState("ScreenA", framework = framework)}
            
            ${Composables.simpleComposableWithState("ScreenB", framework = framework, additionalParams = "callBackA: () -> Unit")}
            
            ${Composables.simpleComposableWithState("ScreenC", framework = framework, additionalParams = "callBackA: () -> Unit, callBackB: () -> Unit")}
        """.trimIndent()

        fun screenWithGenericType(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.genericData}
            
            ${Composables.simpleComposableWithoutState(
                framework = framework,
                params = "data: GenericData<Int>"
            )}
        """.trimIndent()

        fun screenWithComplexGenerics(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${Composables.simpleComposableWithoutState(
                framework = framework,
                params = "callback: (Map<String, List<Map<String, Int>>>) -> List<Map<String, String>>"
            )}
        """.trimIndent()

        fun screenWithKotlinTypes(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            import $packageName.ViewState                       
            
            ${DataClasses.viewState}
            
            @ComposeUIViewController("$framework")
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

        // New template based on ProcessorTest usage
        fun multipleScreensWithDifferentStates(
            framework: String = DEFAULT_FRAMEWORK,
            packageName: String = TEST_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.viewAState}
            ${DataClasses.viewBState}
            
            ${Composables.simpleComposableWithState("ScreenA", "ViewAState", framework)}
            
            private fun dummy() {}
            
            ${Composables.simpleComposableWithState("ScreenB", "ViewBState", framework)}
        """.trimIndent()

        // New template for cross-package imports
        fun screenWithCrossPackageImport(
            functionName: String = "ScreenA",
            packageName: String = TEST_PACKAGE,
            dataPackage: String = DATA_PACKAGE,
            framework: String = DEFAULT_FRAMEWORK
        ) = """
            package $packageName
            $COMMON_IMPORTS
            import $dataPackage.*
            
            ${Composables.simpleComposableWithState(functionName, "ViewState", framework)}
        """.trimIndent()

        // Template for data package
        fun dataPackageWithViewState(packageName: String = DATA_PACKAGE) = """
            package $packageName
            ${DataClasses.viewState}
        """.trimIndent()

        // Template for error cases - multiple state annotations
        fun screenWithMultipleStateAnnotations(packageName: String = TEST_PACKAGE) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.viewState}
            
            @ComposeUIViewController("$DEFAULT_FRAMEWORK")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState, @ComposeUIViewControllerState state2: ViewState) { }
        """.trimIndent()

        // Template for error cases - composable parameters
        fun screenWithComposableParameter(packageName: String = TEST_PACKAGE) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.viewState}
            
            @ComposeUIViewController("$DEFAULT_FRAMEWORK")
            @Composable
            fun Screen(
                @ComposeUIViewControllerState state: ViewState,
                callback: () -> Unit,
                @Composable content: () -> Unit
            ) { }
        """.trimIndent()

        // Template for empty framework annotation
        fun screenWithEmptyFramework(packageName: String = TEST_PACKAGE) = """
            package $packageName
            import $composeUIViewControllerAnnotationName
            import $composeUIViewControllerStateAnnotationName
            
            ${DataClasses.viewState}
            
            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        // Template for framework override from module config
        fun screenWithFrameworkOverride(
            packageName: String = TEST_PACKAGE,
            annotationFramework: String = "MyFramework"
        ) = """
            package $packageName
            $COMMON_IMPORTS
            
            ${DataClasses.viewState}
            
            @ComposeUIViewController("$annotationFramework")
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState) { }
        """.trimIndent()

        // Template for external dependencies with multiple modules
        fun screenWithExternalDependencies(
            packageName: String = TEST_PACKAGE,
            dataPackage: String = DATA_PACKAGE,
            statePackage: String = STATE_PACKAGE
        ) = """
            package $packageName
            $COMMON_IMPORTS
            import $dataPackage.Data
            import $statePackage.ViewState

            @ComposeUIViewController
            @Composable
            fun Screen(@ComposeUIViewControllerState state: ViewState, data: Data) { }
        """.trimIndent()

        // Template for data package with Data class
        fun dataPackageWithData(packageName: String = DATA_PACKAGE) = """
            package $packageName
            ${DataClasses.data}
        """.trimIndent()

        // Template for state package with ViewState
        fun statePackageWithViewState(packageName: String = STATE_PACKAGE) = """
            package $packageName
            ${DataClasses.viewState}
        """.trimIndent()
    }

    object ModuleConfigs {
        fun singleModule(
            moduleName: String = "module-test",
            packageName: String = TEST_PACKAGE,
            framework: String = DEFAULT_FRAMEWORK,
            swiftExportEnabled: Boolean = false,
            flattenPackageConfigured: Boolean = false
        ) = """[{"name":"$moduleName","packageNames":["$packageName"],"frameworkBaseName":"$framework","swiftExportEnabled":$swiftExportEnabled,
            |"flattenPackageConfigured":$flattenPackageConfigured}]""".trimMargin()

        fun multipleModules(
            modules: List<ModuleConfig>
        ) = modules.joinToString(prefix = "[", postfix = "]") { module ->
            """{"name":"${module.name}","packageNames":["${module.packageName}"],"frameworkBaseName":"${module.framework}","swiftExportEnabled":${module.swiftExportEnabled},"flattenPackageConfigured":${module.flattenPackageConfigured}}"""
        }

        data class ModuleConfig(
            val name: String,
            val packageName: String,
            val framework: String,
            val swiftExportEnabled: Boolean = false,
            val flattenPackageConfigured: Boolean = false
        )

        val testAndDataModules = listOf(
            ModuleConfig("module-test", TEST_PACKAGE, DEFAULT_FRAMEWORK),
            ModuleConfig("module-data", DATA_PACKAGE, FRAMEWORK_2)
        )

        val testDataAndStateModules = listOf(
            ModuleConfig("module-test", TEST_PACKAGE, DEFAULT_FRAMEWORK, swiftExportEnabled = true),
            ModuleConfig("module-data", DATA_PACKAGE, FRAMEWORK_2, swiftExportEnabled = true),
            ModuleConfig("module-state", STATE_PACKAGE, FRAMEWORK_3, swiftExportEnabled = true)
        )
    }

    object ExpectedOutputs {
        fun kotlinUIViewControllerWithoutState(
            packageName: String = TEST_PACKAGE,
            functionName: String = "Screen",
            params: String = "data: SomeClass, value: Int, callBack: () -> Unit"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            @file:Suppress("unused")
            package $packageName

            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController

            object ${functionName}UIViewController {
                fun make($params): UIViewController {
                    return ComposeUIViewController {
                        $functionName(${params.split(", ").joinToString(", ") { it.split(":")[0].trim() }})
                    }
                }
            }
        """.trimIndent()

        fun kotlinUIViewControllerWithState(
            packageName: String = TEST_PACKAGE,
            functionName: String = "ScreenA",
            stateType: String = "ViewAState"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            @file:Suppress("unused")
            package $packageName

            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController

            object ${functionName}UIViewController {
                private val state = mutableStateOf<$stateType?>(null)

                fun make(): UIViewController {
                    return ComposeUIViewController {
                        state.value?.let { $functionName(it) }
                    }
                }

                fun update(state: $stateType) {
                    this.state.value = state
                }
            }
        """.trimIndent()

        fun swiftRepresentableWithoutState(
            framework: String = DEFAULT_FRAMEWORK,
            functionName: String = "Screen",
            params: List<Pair<String, String>> = listOf(
                "data" to "SomeClass",
                "value" to "KotlinInt",
                "callBack" to "() -> Void"
            ),
            isSharedInstance: Boolean = false
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework

            public struct ${functionName}Representable: UIViewControllerRepresentable {
                ${params.joinToString("\n") { "let ${it.first}: ${it.second}" }}

                public func makeUIViewController(context: Context) -> UIViewController {
                    ${functionName}UIViewController${if (isSharedInstance) ".shared" else "()"}.make(${params.joinToString(", ") { "${it.first}: ${it.first}" }})
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()

        fun swiftRepresentableWithState(
            framework: String = DEFAULT_FRAMEWORK,
            functionName: String = "ScreenA",
            stateType: String = "ViewAState",
            isSharedInstance: Boolean = false
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework

            public struct ${functionName}Representable: UIViewControllerRepresentable {
                @Binding var state: $stateType
                
                public func makeUIViewController(context: Context) -> UIViewController {
                    ${functionName}UIViewController${if (isSharedInstance) ".shared" else "()"}.make()
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ${functionName}UIViewController${if (isSharedInstance) ".shared" else "()"}.update(state: state)
                }
            }
        """.trimIndent()

        fun typeAliasForExternalDependencies(
            aliases: List<Pair<String, String>>
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            // It contains typealias for external dependencies used in @ComposeUIViewController composables.
            // If you get errors about missing types, consider using the 'flattenPackage' property in KMP swiftExport settings.
            import ExportedKotlinPackages

            ${aliases.joinToString("\n") { (typeName, fullPath) ->
                "//This typealias can be avoided if you use the `flattenPackage = \"${fullPath.substringBeforeLast(".")}\"` in KMP swiftExport settings\ntypealias $typeName = ExportedKotlinPackages.$fullPath"
            }}
        """.trimIndent()

        // New expected outputs based on ProcessorTest patterns
        fun swiftRepresentableWithGenericType(
            framework: String = DEFAULT_FRAMEWORK,
            functionName: String = "Screen"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework

            public struct ${functionName}Representable: UIViewControllerRepresentable {
                let data: GenericData<KotlinInt>

                public func makeUIViewController(context: Context) -> UIViewController {
                    ${functionName}UIViewController().make(data: data)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()

        fun swiftRepresentableWithComplexGenerics(
            framework: String = DEFAULT_FRAMEWORK,
            functionName: String = "Screen"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework

            public struct ${functionName}Representable: UIViewControllerRepresentable {
                let callback: (Dictionary<String, Array<Dictionary<String, KotlinInt>>>) -> Array<Dictionary<String, String>>

                public func makeUIViewController(context: Context) -> UIViewController {
                    ${functionName}UIViewController().make(callback: callback)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()

        fun swiftRepresentableWithExternalDependenciesShared(
            framework: String = DEFAULT_FRAMEWORK,
            framework2: String = FRAMEWORK_2,
            functionName: String = "Screen"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework2
            import $framework
            
            public struct ${functionName}Representable: UIViewControllerRepresentable {
                let data: Data

                public func makeUIViewController(context: Context) -> UIViewController {
                    ${functionName}UIViewController.shared.make(data: data)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()

        fun swiftRepresentableWithObjCTypes(
            framework: String = DEFAULT_FRAMEWORK,
            functionName: String = "Screen"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework
            
            public struct ${functionName}Representable: UIViewControllerRepresentable {
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
                    ${functionName}UIViewController().make(callBackA: callBackA, callBackB: callBackB, callBackS: callBackS, callBackC: callBackC, callBackD: callBackD, callBackE: callBackE, callBackF: callBackF, callBackG: callBackG, callBackH: callBackH, callBackI: callBackI, callBackJ: callBackJ, callBackK: callBackK, callBackL: callBackL, callBackM: callBackM, callBackN: callBackN, callBackO: callBackO, callBackP: callBackP)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ${functionName}UIViewController().update(state: state)
                }
            }
        """.trimIndent()

        fun swiftRepresentableWithSwiftExportTypes(
            framework: String = DEFAULT_FRAMEWORK,
            functionName: String = "Screen"
        ) = """
            // This file is auto-generated by KSP. Do not edit manually.
            import SwiftUI
            import $framework

            public struct ${functionName}Representable: UIViewControllerRepresentable {
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
                    ${functionName}UIViewController.shared.make(callBackA: callBackA, callBackB: callBackB, callBackS: callBackS, callBackC: callBackC, callBackD: callBackD, callBackE: callBackE, callBackF: callBackF, callBackG: callBackG, callBackH: callBackH, callBackI: callBackI, callBackJ: callBackJ, callBackK: callBackK, callBackL: callBackL, callBackM: callBackM, callBackN: callBackN, callBackO: callBackO, callBackP: callBackP)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ${functionName}UIViewController.shared.update(state: state)
                }
            }
        """.trimIndent()

        fun typeAliasForExternalDependenciesComplete() = """
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
    }

    object TestFileUtils {
        fun findGeneratedKotlinFile(compilation: KotlinCompilation, fileName: String): List<File> {
            return compilation.kspSourcesDir
                .walkTopDown()
                .filter { it.name == fileName }
                .toList()
        }

        fun findGeneratedSwiftFile(compilation: KotlinCompilation, fileName: String): List<File> {
            return compilation.kspSourcesDir
                .walkTopDown()
                .filter { it.name == fileName }
                .toList()
        }

        fun countGeneratedFiles(compilation: KotlinCompilation, vararg fileNames: String): Int {
            return compilation.kspSourcesDir
                .walkTopDown()
                .filter { it.name in fileNames }
                .toList()
                .size
        }

        fun hasNoGeneratedFiles(compilation: KotlinCompilation): Boolean {
            return compilation.kspSourcesDir
                .walkTopDown()
                .filter { it.extension == "kt" || it.extension == "swift" }
                .toList()
                .isEmpty()
        }

        fun createSourceFiles(vararg codeBlocks: Pair<String, String>): Array<SourceFile> {
            return codeBlocks.map { (fileName, code) -> kotlin(fileName, code) }.toTypedArray()
        }
    }

    object ErrorTemplates {
        fun collectionWithoutType(collectionType: String) = """
            package $TEST_PACKAGE
            $COMMON_IMPORTS
            
            ${DataClasses.viewState}

            @ComposeUIViewController("$DEFAULT_FRAMEWORK")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBack: ($collectionType) -> Unit
            ) { }
        """.trimIndent()

        fun functionWithReturnTypeWithoutSpecification(returnType: String) = """
            package $TEST_PACKAGE
            $COMMON_IMPORTS
            
            ${DataClasses.viewState}

            @ComposeUIViewController("$DEFAULT_FRAMEWORK")
            @Composable
            fun Screen(
                    @ComposeUIViewControllerState state: ViewState,                   
                    callBack: (List<String>) -> $returnType
            ) { }
        """.trimIndent()
    }
}

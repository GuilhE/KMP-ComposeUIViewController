package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter

internal class Processor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getSymbolsWithAnnotation(composeUIViewControllerAnnotationName)
        if (!candidates.iterator().hasNext()) {
            logger.info("No @${composeUIViewControllerAnnotationName.name()} found!")
            return emptyList()
        }

        val trimmedCandidates = candidates.distinctBy { it.containingFile?.fileName }
        for (node in trimmedCandidates) {
            val frameworkName: String? = node.annotations
                .find { it.shortName.asString() == composeUIViewControllerAnnotationName.name() }
                ?.arguments
                ?.firstOrNull { it.name?.asString() == composeUIViewControllerAnnotationParameterName }
                ?.value as? String

            if (frameworkName.isNullOrEmpty()) {
                throw IllegalArgumentException("@${composeUIViewControllerAnnotationName.name()} has no value for $composeUIViewControllerAnnotationParameterName")
            }

            node.containingFile?.let { file ->
                val packageName = file.packageName.asString()
                for (composable in file.declarations.filterIsInstance<KSFunctionDeclaration>()) {
                    val parameters: List<KSValueParameter> = composable.parameters
                    val stateParameters = parameters.filter {
                        it.annotations
                            .filter { annotation -> annotation.shortName.getShortName() == composeUIViewControllerStateAnnotationName.name() }
                            .toList()
                            .isNotEmpty()
                    }

                    when {
                        stateParameters.size > 1 -> throw IllegalArgumentException(
                            "The composable ${composable.name()} has more than one parameter annotated " +
                                    "with @${composeUIViewControllerStateAnnotationName.name()}."
                        )

                        stateParameters.isEmpty() -> throw IllegalArgumentException(
                            "The composable ${composable.name()} is annotated with @${composeUIViewControllerAnnotationName.split(".").last()}" +
                                    "but it's missing the ui state parameter annotated with @${composeUIViewControllerStateAnnotationName.name()}"
                        )
                    }

                    val stateParameter = stateParameters.first()
                    val stateParameterName = stateParameter.name()
                    val makeParameters = parameters.filterNot { it.type == stateParameter.type }.filterFunctions()

                    if (parameters.size != makeParameters.size + 1) {
                        throw IllegalArgumentException(
                            "Only 1 @${composeUIViewControllerStateAnnotationName.name()} and " +
                                    "N high-order function parameters (excluding @Composable content: () -> Unit) are allowed."
                        )
                    }

                    createKotlinFile(packageName, composable, stateParameterName, stateParameter, makeParameters, parameters).also {
                        logger.info("${composable.name()}UIViewController created!")
                    }
                    createSwiftFile(frameworkName, composable, stateParameterName, stateParameter, makeParameters).also {
                        logger.info("${composable.name()}Representable created!")
                    }
                }
            }
        }
        return emptyList()
    }

    private fun createKotlinFile(
        packageName: String,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>
    ): String {
        val code = """
            @file:Suppress("unused")
            package $packageName
             
            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            
            public object ${composable.name()}UIViewController {
                private val $stateParameterName = mutableStateOf(${stateParameter.type}())
                
                public fun make(${makeParameters.joinToString()}): UIViewController {
                    return ComposeUIViewController {
                        ${composable.name()}(${parameters.toComposableParameters(stateParameterName)})
                    }
                }
                
                public fun update($stateParameterName: ${stateParameter.type}) {
                    this.$stateParameterName.value = $stateParameterName
                }
            }
        """.trimIndent()
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewController",
            ).write(code.toByteArray())
        return code
    }

    private fun createSwiftFile(
        frameworkName: String,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
    ): String {
        val letParameters = makeParameters.joinToString("\n") {
            "let ${it.name()}: ${kotlinTypeToSwift(it.type)}"
        }
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }

        val code = """
            import SwiftUI
            import $frameworkName
            
            public struct ${composable.name()}Representable: UIViewControllerRepresentable {
                @Binding var $stateParameterName: ${stateParameter.type}
                $letParameters
                
                public func makeUIViewController(context: Context) -> UIViewController {
                    return ${composable.name()}UIViewController().make($makeParametersParsed)
                }
                
                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ${composable.name()}UIViewController().update($stateParameterName: $stateParameterName)
                }
            }
        """.trimIndent()
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewControllerRepresentable",
                extensionName = "swift"
            ).write(code.toByteArray())
        return code
    }

    /**
     * @param type Kotlin type to be converted to Swift type
     * @return String with Swift type
     * @see https://kotlinlang.org/docs/apple-framework.html#generated-framework-headers
     */
    private fun kotlinTypeToSwift(type: KSTypeReference): String {
        val regex = "\\b(Unit|List|MutableList|Map|MutableMap|Byte|UByte|Short|UShort|Int|UInt|Long|ULong|Float|Double|Boolean)\\b".toRegex()
        return regex.replace("$type") { matchResult ->
            when (matchResult.value) {
                "Unit" -> "Void"
                "List" -> "Array"
                "MutableList" -> "NSMutableArray"
                "Map" -> "Dictionary"
                "MutableMap" -> "NSMutableDictionary"
                "Byte" -> "KotlinByte"
                "UByte" -> "KotlinUByte"
                "Short" -> "KotlinShort"
                "UShort" -> "KotlinUShort"
                "Int" -> "KotlinInt"
                "UInt" -> "KotlinUInt"
                "Long" -> "KotlinLong"
                "ULong" -> "KotlinULong"
                "Float" -> "KotlinFloat"
                "Double" -> "KotlinDouble"
                "Boolean" -> "KotlinBoolean"
                else -> "KotlinNumber"
            }
        }
    }

    private fun String.name() = split(".").last()

    private fun List<KSValueParameter>.toComposableParameters(stateParameterName: String): String =
        joinToString(", ") { if (it.name() == stateParameterName) "${it.name()}.value" else it.name() }

    private fun List<KSValueParameter>.filterFunctions(): List<KSValueParameter> =
        filter { it.type.resolve().isFunctionType && it.annotations.none { annotation -> annotation.shortName.getShortName() == "Composable" } }

    private fun List<KSValueParameter>.joinToString(): String = joinToString(", ") { "${it.name!!.getShortName()}: ${it.type}" }

    private fun KSFunctionDeclaration.name(): String = qualifiedName!!.getShortName()

    private fun KSValueParameter.name(): String = name!!.getShortName()
}
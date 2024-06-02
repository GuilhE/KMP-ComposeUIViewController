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
        if (candidates.none()) {
            logger.info("No more @${composeUIViewControllerAnnotationName.name()} found!")
            return emptyList()
        }

        val trimmedCandidates = candidates.distinctBy { it.containingFile?.fileName }
        for (node in trimmedCandidates) {
            val frameworkName: String = getFrameworkNameFromAnnotations(node)
            node.containingFile?.let { file ->
                val packageName = file.packageName.asString()
                for (composable in file.declarations.filterIsInstance<KSFunctionDeclaration>().filter {
                    it.annotations.any { annotation -> annotation.shortName.asString() == composeUIViewControllerAnnotationName.name() }
                }) {
                    val parameters: List<KSValueParameter> = composable.parameters
                    val stateParameters = getStateParameters(parameters, composable)
                    val stateParameter = stateParameters.firstOrNull()
                    val makeParameters =
                        if (stateParameter == null) {
                            parameters
                                .filterComposableFunctions()
                                .also {
                                    if (parameters.size != it.size) {
                                        throw IllegalArgumentException(
                                            "Only 1 @${composeUIViewControllerStateAnnotationName.name()} and " +
                                                    "N high-order function parameters (excluding @Composable content: () -> Unit) are allowed."
                                        )
                                    }
                                }
                        } else {
                            parameters
                                .filterNot { it.type == stateParameter.type }
                                .filterComposableFunctions()
                                .also {
                                    if (parameters.size != it.size + 1) {
                                        throw IllegalArgumentException(
                                            "Only 1 @${composeUIViewControllerStateAnnotationName.name()} and " +
                                                    "N high-order function parameters (excluding @Composable content: () -> Unit) are allowed."
                                        )
                                    }
                                }
                        }

                    if (stateParameter == null) {
                        createKotlinFileWithoutState(packageName, composable, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithoutState(frameworkName, composable, makeParameters).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    } else {
                        val stateParameterName = stateParameter.name()
                        createKotlinFileWithState(packageName, composable, stateParameterName, stateParameter, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithState(frameworkName, composable, stateParameterName, stateParameter, makeParameters).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun getFrameworkNameFromAnnotations(node: KSAnnotated): String {
        val annotation = node.annotations.firstOrNull { it.shortName.asString() == composeUIViewControllerAnnotationName.name() }
        if (annotation != null) {
            val argument = annotation.arguments.firstOrNull { it.name?.asString() == composeUIViewControllerAnnotationParameterName }
            if (argument != null) {
                val value = argument.value
                if (value is String && value.isNotEmpty()) {
                    return value
                }
            }
        }
        throw IllegalArgumentException("@${composeUIViewControllerAnnotationName.name()} requires a non-null and non-empty value for $composeUIViewControllerAnnotationParameterName")
    }

    private fun getStateParameters(parameters: List<KSValueParameter>, composable: KSFunctionDeclaration): List<KSValueParameter> {
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
        }
        return stateParameters
    }

    private fun createKotlinFileWithoutState(
        packageName: String,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>
    ): String {
        val code = """
            @file:Suppress("unused")
            package $packageName
             
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            
            object ${composable.name()}UIViewController {
                fun make(${makeParameters.joinToString()}): UIViewController {
                    return ComposeUIViewController {
                        ${composable.name()}(${parameters.toComposableParameters()})
                    }
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

    private fun createKotlinFileWithState(
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
            
            object ${composable.name()}UIViewController {
                private val $stateParameterName = mutableStateOf(${stateParameter.type}())
                
                fun make(${makeParameters.joinToString()}): UIViewController {
                    return ComposeUIViewController {
                        ${composable.name()}(${parameters.toComposableParameters(stateParameterName)})
                    }
                }
                
                fun update($stateParameterName: ${stateParameter.type}) {
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

    private fun createSwiftFileWithoutState(
        frameworkName: String,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>
    ): String {
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") { "let ${it.name()}: ${kotlinTypeToSwift(it.type)}" }
        val code = """
            import SwiftUI
            import $frameworkName
            
            public struct ${composable.name()}Representable: UIViewControllerRepresentable {
                $letParameters
                
                public func makeUIViewController(context: Context) -> UIViewController {
                    ${composable.name()}UIViewController().make($makeParametersParsed)
                }
                
                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    //unused
                }
            }
        """.trimIndent()
        val updatedCode = indentParameters(code, letParameters)
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewControllerRepresentable",
                extensionName = "swift"
            ).write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun createSwiftFileWithState(
        frameworkName: String,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
    ): String {
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") { "let ${it.name()}: ${kotlinTypeToSwift(it.type)}" }
        val code = """
            import SwiftUI
            import $frameworkName
            
            public struct ${composable.name()}Representable: UIViewControllerRepresentable {
                @Binding var $stateParameterName: ${stateParameter.type}
                $letParameters
                
                public func makeUIViewController(context: Context) -> UIViewController {
                    ${composable.name()}UIViewController().make($makeParametersParsed)
                }
                
                public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
                    ${composable.name()}UIViewController().update($stateParameterName: $stateParameterName)
                }
            }
        """.trimIndent()
        val updatedCode = indentParameters(code, letParameters)
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewControllerRepresentable",
                extensionName = "swift"
            ).write(updatedCode.toByteArray())
        return updatedCode
    }

    /**
     * @param type Kotlin type to be converted to Swift type
     * @return String with Swift type
     * @see https://kotlinlang.org/docs/apple-framework.html#generated-framework-headers
     */
    @Suppress("KDocUnresolvedReference")
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

    private fun indentParameters(code: String, parameters: String): String {
        parameters.ifEmpty {
            return removeAdjacentEmptyLines(code.lines()).joinToString("\n").trimIndent()
        }
        val linesBeforeLetParameters = code.substringBefore(parameters).lines()
        val indentation = linesBeforeLetParameters.lastOrNull()?.takeWhile { it.isWhitespace() } ?: ""
        val indentedLetParameters = parameters.lines().joinToString("\n") { "$indentation$it" }.lines()
        val codeLines = code.lines().toMutableList()
        codeLines.subList(linesBeforeLetParameters.size - 1, linesBeforeLetParameters.size - 1 + indentedLetParameters.size).clear()
        codeLines.addAll(linesBeforeLetParameters.size - 1, indentedLetParameters)
        return codeLines.joinToString("\n").trimIndent()
    }

    private fun removeAdjacentEmptyLines(list: List<String>): List<String> {
        return list.fold<String, MutableList<String>>(mutableListOf()) { acc, line ->
            if (!(line.isBlank() && acc.lastOrNull()?.isBlank() == true)) {
                acc.add(line)
            }
            acc
        }.toList()
    }

    private fun String.name() = split(".").last()

    private fun List<KSValueParameter>.toComposableParameters(stateParameterName: String): String =
        joinToString(", ") { if (it.name() == stateParameterName) "${it.name()}.value" else it.name() }

    private fun List<KSValueParameter>.toComposableParameters(): String = joinToString(", ") { it.name() }

    private fun List<KSValueParameter>.filterComposableFunctions(): List<KSValueParameter> =
        filter { it.annotations.none { annotation -> annotation.shortName.getShortName() == "Composable" } }

    private fun List<KSValueParameter>.joinToString(): String = joinToString(", ") { "${it.name!!.getShortName()}: ${it.type}" }

    private fun KSFunctionDeclaration.name(): String = qualifiedName!!.getShortName()

    private fun KSValueParameter.name(): String = name!!.getShortName()
}
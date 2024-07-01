package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter

internal class Processor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getSymbolsWithAnnotation(composeUIViewControllerAnnotationName)
        if (candidates.none()) {
            logger.info("No more @${composeUIViewControllerAnnotationName.name()} found!")
            return emptyList()
        }

        val trimmedCandidates = candidates.distinctBy { it.containingFile?.fileName }
        for (node in trimmedCandidates) {
            val frameworkBaseName: String = getFrameworkBaseNameFromCompilerArgs() ?: getFrameworkNameFromAnnotations(node)
            node.containingFile?.let { file ->
                val packageName = file.packageName.asString()
                for (composable in file.declarations.filterIsInstance<KSFunctionDeclaration>().filter {
                    it.annotations.any { annotation -> annotation.shortName.asString() == composeUIViewControllerAnnotationName.name() }
                }) {
                    val parameters: List<KSValueParameter> = composable.parameters
                    val stateParameter = getStateParameter(parameters, composable).firstOrNull()
                    val makeParameters =
                        if (stateParameter == null) {
                            parameters
                                .filterComposableFunctions()
                                .also {
                                    if (parameters.size != it.size) {
                                        throw InvalidParametersException()
                                    }
                                }
                        } else {
                            parameters
                                .filterNot { it.type == stateParameter.type }
                                .filterComposableFunctions()
                                .also {
                                    if (parameters.size != it.size + 1) {
                                        throw InvalidParametersException()
                                    }
                                }
                        }

                    if (stateParameter == null) {
                        createKotlinFileWithoutState(packageName, composable, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithoutState(frameworkBaseName, composable, makeParameters).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    } else {
                        val stateParameterName = stateParameter.name()
                        createKotlinFileWithState(packageName, composable, stateParameterName, stateParameter, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithState(frameworkBaseName, composable, stateParameterName, stateParameter, makeParameters).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun getFrameworkBaseNameFromCompilerArgs(): String? {
        val name = options[composeUIViewControllerAnnotationParameterName]
        return name?.ifEmpty { throw EmptyFrameworkBaseNameException() } ?: name
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
        throw EmptyFrameworkBaseNameException()
    }

    private fun getStateParameter(parameters: List<KSValueParameter>, composable: KSFunctionDeclaration): List<KSValueParameter> {
        val stateParameters = parameters.filter {
            it.annotations
                .filter { annotation -> annotation.shortName.getShortName() == composeUIViewControllerStateAnnotationName.name() }
                .toList()
                .isNotEmpty()
        }
        when {
            stateParameters.size > 1 -> throw MultipleComposeUIViewControllerStateException(composable)
        }
        return stateParameters
    }

    private fun createKotlinFileWithoutState(
        packageName: String,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>
    ): String {
        val imports = generateImports(packageName, makeParameters, parameters)
        val code = """
            @file:Suppress("unused")
            package $packageName

            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            $imports

            object ${composable.name()}UIViewController {
                fun make(${makeParameters.joinToString()}): UIViewController {
                    return ComposeUIViewController {
                        ${composable.name()}(${parameters.toComposableParameters()})
                    }
                }
            }
        """.trimIndent()
        val updatedCode = indentParameters(code, imports)
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewController",
            ).write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun createKotlinFileWithState(
        packageName: String,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>
    ): String {
        val imports = generateImports(packageName, makeParameters, parameters)
        val code = """
            @file:Suppress("unused")
            package $packageName

            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            $imports

            object ${composable.name()}UIViewController {
                private val $stateParameterName = mutableStateOf<${stateParameter.type}?>(null)

                fun make(${makeParameters.joinToString()}): UIViewController {
                    return ComposeUIViewController {
                        state.value?.let { ${composable.name()}(${parameters.toComposableParameters(stateParameterName)}) }
                    }
                }

                fun update($stateParameterName: ${stateParameter.type}) {
                    this.$stateParameterName.value = $stateParameterName
                }
            }
        """.trimIndent()
        val updatedCode = indentParameters(code, imports)
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewController",
            ).write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun createSwiftFileWithoutState(
        frameworkBaseName: String,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>
    ): String {
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") { "let ${it.name()}: ${kotlinTypeToSwift(it.type)}" }
        val code = """
            import SwiftUI
            import $frameworkBaseName

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
        frameworkBaseName: String,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
    ): String {
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") { "let ${it.name()}: ${kotlinTypeToSwift(it.type)}" }
        val code = """
            import SwiftUI
            import $frameworkBaseName

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
}
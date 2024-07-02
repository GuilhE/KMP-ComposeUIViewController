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
import java.io.File
import java.util.Properties

internal class Processor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getSymbolsWithAnnotation(composeUIViewControllerAnnotationName)
        if (candidates.none()) {
            logger.info("No more @${composeUIViewControllerAnnotationName.name()} found!")
            return emptyList()
        }

        val trimmedCandidates = candidates.distinctBy { it.containingFile?.fileName }
        for (node in trimmedCandidates) {
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
                        createSwiftFileWithoutState(
                            getFrameworkBaseNames(composable, node, makeParameters, parameters),
                            composable,
                            makeParameters
                        ).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    } else {
                        val stateParameterName = stateParameter.name()
                        createKotlinFileWithState(packageName, composable, stateParameterName, stateParameter, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithState(
                            getFrameworkBaseNames(composable, node, makeParameters, parameters, stateParameter),
                            composable,
                            stateParameterName,
                            stateParameter,
                            makeParameters
                        ).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun getFrameworkMetadataFromCompilerArgs(): List<FrameworkMetadata> {
        val paramsFile = File("build/$FILE_NAME_ARGS")
        val properties = Properties()
        if (paramsFile.exists()) {
            paramsFile.inputStream().use { properties.load(it) }
        }

        val filteredProperties = properties.filter { (key, _) -> key.toString().startsWith("$frameworkBaseNameAnnotationParameter-") }
        val metadata = filteredProperties.map { (key, value) ->
            FrameworkMetadata(key.toString(), value.toString())
        }
        return metadata.ifEmpty { throw EmptyFrameworkBaseNameException() }
    }

    private fun getFrameworkBaseNameFromAnnotations(node: KSAnnotated): String {
        val annotation = node.annotations.firstOrNull { it.shortName.asString() == composeUIViewControllerAnnotationName.name() }
        if (annotation != null) {
            val argument = annotation.arguments.firstOrNull { it.name?.asString() == frameworkBaseNameAnnotationParameter }
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

    private fun getFrameworkBaseNames(
        composable: KSFunctionDeclaration,
        node: KSAnnotated,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>,
        stateParameter: KSValueParameter? = null
    ): List<String> {
        val frameworkBaseNames = mutableListOf<String>()
        frameworkBaseNames.addAll(
            retrieveFrameworkBaseNames(
                composable,
                getFrameworkMetadataFromCompilerArgs(),
                makeParameters,
                parameters,
                stateParameter
            )
        )
        frameworkBaseNames.ifEmpty { frameworkBaseNames.add(getFrameworkBaseNameFromAnnotations(node)) }
        return frameworkBaseNames
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
        val imports = generateImports(packageName, makeParameters, parameters, stateParameter)
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
        frameworkBaseName: List<String>,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>
    ): String {
        val frameworks = frameworkBaseName.joinToString("\n") { "import ${it.name()}" }
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") { "let ${it.name()}: ${kotlinTypeToSwift(it.type)}" }
        val code = """
            import SwiftUI
            $frameworks

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
        frameworkBaseName: List<String>,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
    ): String {
        val frameworks = frameworkBaseName.joinToString("\n") { "import ${it.name()}" }
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") { "let ${it.name()}: ${kotlinTypeToSwift(it.type)}" }
        val code = """
            import SwiftUI
            $frameworks

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

    internal companion object {
        internal const val FILE_NAME_ARGS = "args.properties"
    }
}
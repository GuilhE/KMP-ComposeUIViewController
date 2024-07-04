package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.File
import java.util.Locale
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
                val moduleName = findModuleNameFromPath(file)
                for (composable in file.declarations.filterIsInstance<KSFunctionDeclaration>().filter {
                    it.annotations.any { annotation -> annotation.shortName.asString() == composeUIViewControllerAnnotationName.name() }
                }) {
                    val parameters: List<KSValueParameter> = composable.parameters
                    val stateParameter = getStateParameter(parameters, composable).firstOrNull()
                    val makeParameters = if (stateParameter == null) {
                        parameters
                            .filterComposableFunctions()
                            .also { if (parameters.size != it.size) throw InvalidParametersException() }
                    } else {
                        parameters
                            .filterNot { it.type == stateParameter.type }
                            .filterComposableFunctions()
                            .also { if (parameters.size != it.size + 1) throw InvalidParametersException() }
                    }
                    val packageName = file.packageName.asString()
                    val imports = extractImportsFromExternalPackages(packageName, makeParameters, parameters)
                    val externalModuleTypes = buildExternalModuleParameters(moduleName, imports)

                    if (stateParameter == null) {
                        createKotlinFileWithoutState(packageName, imports, composable, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }

//                        val frameworkBaseNames = getFrameworkBaseNames(composable, node, makeParameters, parameters)
                        val currentFramework = trimFrameworkBaseNames(node, packageName)
                        createSwiftFileWithoutState(listOf(currentFramework), composable, makeParameters, externalModuleTypes).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    } else {
                        val stateParameterName = stateParameter.name()
                        createKotlinFileWithState(
                            packageName, imports, composable, stateParameterName, stateParameter, makeParameters, parameters
                        ).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }

//                        val frameworkBaseNames = getFrameworkBaseNames(composable, node, makeParameters, parameters)
                        val currentFramework = trimFrameworkBaseNames(node, packageName)
                        createSwiftFileWithState(
                            listOf(currentFramework), composable, stateParameterName, stateParameter, makeParameters, externalModuleTypes
                        ).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun findModuleNameFromPath(file: KSFile): String {
        val pathParts = file.filePath.split(File.separator)
        val srcIndex = pathParts.indexOf("src")
        return if (srcIndex > 0) pathParts[srcIndex - 1] else throw UnkownModuleException(file)
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

    /**
     * This exists because of KMP current implementation bla bla bla. When fixed this will become deprecated and substituted by [getFrameworkBaseNames]
     */
    private fun trimFrameworkBaseNames(node: KSAnnotated, packageName: String): String {
        val metadata = getFrameworkMetadataFromArgsProperties()
        if (metadata.isEmpty()) {
            val framework = getFrameworkBaseNameFromAnnotation(node) ?: throw EmptyFrameworkBaseNameException()
            return framework
        } else {
            val framework =
                metadata.firstOrNull { it.packageName == packageName }?.baseName?.removePrefix("$frameworkBaseNameAnnotationParameter-") ?: ""
            framework.ifEmpty { return getFrameworkBaseNameFromAnnotation(node) ?: throw EmptyFrameworkBaseNameException() }
            return framework
        }
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
            extractFrameworkBaseNames(
                composable,
                getFrameworkMetadataFromArgsProperties(),
                makeParameters,
                parameters,
                stateParameter
            )
        )
        frameworkBaseNames.ifEmpty {
            getFrameworkBaseNameFromAnnotation(node)?.let { frameworkBaseNames.add(it) }
        }
        frameworkBaseNames.ifEmpty { throw EmptyFrameworkBaseNameException() }
        return frameworkBaseNames
    }

    private fun getFrameworkMetadataFromArgsProperties(): List<FrameworkMetadata> {
        val paramsFile = File("./build/$FILE_NAME_ARGS")
        val properties = Properties()
        if (paramsFile.exists()) {
            paramsFile.inputStream().use { properties.load(it) }
        }

        val filteredProperties = properties.filter { (key, _) -> key.toString().startsWith("$frameworkBaseNameAnnotationParameter-") }
        val metadata = filteredProperties.map { (key, value) ->
            FrameworkMetadata(key.toString(), value.toString())
        }
        return metadata
    }

    private fun getFrameworkBaseNameFromAnnotation(node: KSAnnotated): String? {
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
        return null
    }

    private fun createKotlinFileWithoutState(
        packageName: String,
        imports: List<String>,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>
    ): String {
        val importsParsed = imports.joinToString("\n") { "import $it" }
        val code = """
            @file:Suppress("unused")
            package $packageName

            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            $importsParsed

            object ${composable.name()}UIViewController {
                fun make(${makeParameters.joinToString()}): UIViewController {
                    return ComposeUIViewController {
                        ${composable.name()}(${parameters.toComposableParameters()})
                    }
                }
            }
        """.trimIndent()
        val updatedCode = indentParameters(code, importsParsed)
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
        imports: List<String>,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>
    ): String {
        val importsParsed = imports.joinToString("\n") { "import $it" }
        val code = """
            @file:Suppress("unused")
            package $packageName

            import androidx.compose.runtime.mutableStateOf
            import androidx.compose.ui.window.ComposeUIViewController
            import platform.UIKit.UIViewController
            $importsParsed

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
        val updatedCode = indentParameters(code, importsParsed)
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewController",
            ).write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun buildExternalModuleParameters(moduleName: String, imports: List<String>): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        val capitalized = moduleName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val replaced = capitalized.replace("-", "_")
        imports.forEach {
            val type = it.split(".").last()
            result[type] = "$replaced$type"
        }
        return result
    }

    private fun createSwiftFileWithoutState(
        frameworkBaseName: List<String>,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>,
        externalParameters: Map<String, String>
    ): String {
        val frameworks = frameworkBaseName.joinToString("\n") { "import ${it.name()}" }
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") {
            val type = kotlinTypeToSwift(it.type)
            val finalType = if (externalParameters.containsKey(type)) {
                externalParameters[type]
            } else type
            "let ${it.name()}: $finalType"
        }
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
        val updatedCode = indentParameters(indentParameters(code, frameworks), letParameters)
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
        externalParameters: Map<String, String>
    ): String {
        val frameworks = frameworkBaseName.joinToString("\n") { "import ${it.name()}" }
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") {
            val type = kotlinTypeToSwift(it.type)
            val finalType = if (externalParameters.containsKey(type)) {
                externalParameters[type]
            } else type
            "let ${it.name()}: $finalType"
        }
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
        val updatedCode = indentParameters(indentParameters(code, frameworks), letParameters)
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
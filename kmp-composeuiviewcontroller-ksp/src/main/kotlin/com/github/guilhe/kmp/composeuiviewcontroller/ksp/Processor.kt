@file:Suppress("RedundantVisibilityModifier")

package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.github.guilhe.kmp.composeuiviewcontroller.common.TEMP_FILES_FOLDER
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import kotlinx.serialization.json.Json
import java.io.File

public class ProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return Processor(environment.codeGenerator, environment.logger)
    }
}

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
                    val makeParameters = if (stateParameter == null) {
                        parameters
                            .filterNotComposableFunctions()
                            .also { if (parameters.size != it.size) throw InvalidParametersException() }
                    } else {
                        parameters
                            .filterNot { it.type == stateParameter.type }
                            .filterNotComposableFunctions()
                            .also { if (parameters.size != it.size + 1) throw InvalidParametersException() }
                    }
                    val externalImports = extractImportsFromExternalPackages(packageName, makeParameters, parameters)
                    val modulesMetadata = getFrameworkMetadataFromDisk()
                    val swiftExportEnabled: Boolean = modulesMetadata.firstOrNull()?.swiftExport ?: false
                    val externalModuleTypes = if (swiftExportEnabled) {
                        buildExternalModuleParameters(modulesMetadata, externalImports)
                    } else {
                        emptyMap()
                    }
                    val frameworkBaseNames = if(swiftExportEnabled) {
                        getFrameworkBaseNames(composable, node, makeParameters, parameters)
                    } else {
                        listOf(trimFrameworkBaseNames(node, modulesMetadata, packageName))
                    }

                    if (stateParameter == null) {
                        createKotlinFileWithoutState(packageName, externalImports, composable, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithoutState(frameworkBaseNames, composable, makeParameters, externalModuleTypes).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    } else {
                        val stateParameterName = stateParameter.name()
                        createKotlinFileWithState(
                            packageName, externalImports, composable, stateParameterName, stateParameter, makeParameters, parameters
                        ).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithState(
                            frameworkBaseNames, composable, stateParameterName, stateParameter, makeParameters, externalModuleTypes
                        ).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    }
                }
            }
        }
        return emptyList()
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

    private fun getFrameworkMetadataFromDisk(): List<ModuleMetadata> {
        val file = if (System.getProperty("user.dir").endsWith("Pods")) {
            File("../../build/$TEMP_FILES_FOLDER/$FILE_NAME_ARGS")
        } else File("./build/$TEMP_FILES_FOLDER/$FILE_NAME_ARGS")
        val moduleMetadata = try {
            Json.decodeFromString<List<ModuleMetadata>>(file.readText())
        } catch (e: Exception) {
            throw ModuleDecodeException(e)
        }
        return moduleMetadata
    }

    private fun buildExternalModuleParameters(moduleMetadata: List<ModuleMetadata>, imports: List<String>): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        imports.forEach { it ->
            val type = it.split(".").last()
            val import = it.split(".$type").first()
            moduleMetadata
                .filter { module -> module.packageNames.contains(import) }
                .forEach { module -> result[type] = type }
        }
        return result
    }

    private fun trimFrameworkBaseNames(node: KSAnnotated, moduleMetadata: List<ModuleMetadata>, packageName: String): String {
        if (moduleMetadata.isEmpty()) {
            val framework = getFrameworkBaseNameFromAnnotation(node) ?: throw EmptyFrameworkBaseNameException()
            return framework
        } else {
            val framework = moduleMetadata.firstOrNull { it.packageNames.any { p -> p.startsWith(packageName) } }?.frameworkBaseName ?: ""
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
                getFrameworkMetadataFromDisk(),
                makeParameters,
                parameters,
                stateParameter
            )
        )
        frameworkBaseNames.removeIf { it.isBlank() }
        frameworkBaseNames.ifEmpty { getFrameworkBaseNameFromAnnotation(node)?.let { frameworkBaseNames.add(it) } }
        frameworkBaseNames.ifEmpty { throw EmptyFrameworkBaseNameException() }
        return frameworkBaseNames
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
                fun make(${makeParameters.joinToStringDeclaration()}): UIViewController {
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

                fun make(${makeParameters.joinToStringDeclaration()}): UIViewController {
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

    private fun createSwiftFileWithoutState(
        frameworkBaseName: List<String>,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>,
        externalParameters: Map<String, String>
    ): String {
        val frameworks = frameworkBaseName.joinToString("\n") { "import ${it.name()}" }
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") {
            val type = it.resolveType(toSwift = true)
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
            val type = it.resolveType(toSwift = true)
            val finalType = externalParameters[type] ?: type
            "let ${it.name()}: $finalType"
        }
        val stateType = externalParameters[stateParameter.type.resolve().toString()] ?: stateParameter.type
        val code = """
            import SwiftUI
            $frameworks

            public struct ${composable.name()}Representable: UIViewControllerRepresentable {
                @Binding var $stateParameterName: $stateType
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
}

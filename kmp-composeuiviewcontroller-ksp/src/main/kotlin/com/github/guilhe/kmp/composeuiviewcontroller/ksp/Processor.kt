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

private data class TypeAliasInfo(
    val name: String,
    val packageName: String,
)

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
        val modulesMetadata = getFrameworkMetadataFromDisk()
        val swiftExportEnabled: Boolean = modulesMetadata.firstOrNull()?.swiftExportEnabled ?: false

        val accumulatedTypeAliases = mutableSetOf<TypeAliasInfo>()
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
                    val externalModuleTypes = if (swiftExportEnabled) {
                        buildExternalModuleParameters(modulesMetadata, externalImports)
                    } else {
                        emptyMap()
                    }
                    val frameworkBaseNames = if (swiftExportEnabled) {
                        getFrameworkBaseNames(composable, node, makeParameters, parameters, modulesMetadata, stateParameter)
                    } else {
                        listOf(trimFrameworkBaseNames(node, modulesMetadata, packageName))
                    }

                    if (stateParameter == null) {
                        createKotlinFileWithoutState(packageName, externalImports, composable, makeParameters, parameters).also {
                            logger.info("${composable.name()}UIViewController created!")
                        }
                        createSwiftFileWithoutState(
                            frameworkBaseNames = frameworkBaseNames,
                            composable = composable,
                            makeParameters = makeParameters,
                            externalParameters = externalModuleTypes,
                            swiftExportEnabled = swiftExportEnabled
                        ).also {
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
                            frameworkBaseNames = frameworkBaseNames,
                            composable = composable,
                            stateParameterName = stateParameterName,
                            stateParameter = stateParameter,
                            makeParameters = makeParameters,
                            externalParameters = externalModuleTypes,
                            swiftExportEnabled = swiftExportEnabled
                        ).also {
                            logger.info("${composable.name()}Representable created!")
                        }
                    }

                    if (swiftExportEnabled) {
                        collectTypeAliases(
                            externalImports = externalImports,
                            packageName = packageName,
                            composableName = composable.name(),
                            modulesMetadata = modulesMetadata,
                            accumulatedTypeAliases = accumulatedTypeAliases
                        )
                    }
                }
            }
        }

        if (accumulatedTypeAliases.isNotEmpty()) {
            createConsolidatedSwiftFileWithTypeAlias(accumulatedTypeAliases.toList()).also {
                logger.info("TypeAliasForExternalDependencies.swift created with $accumulatedTypeAliases typealias")
            }
        }
        return emptyList()
    }

    private fun getStateParameter(parameters: List<KSValueParameter>, composable: KSFunctionDeclaration): List<KSValueParameter> {
        val stateParameters = parameters.filter {
            it.annotations.any { annotation ->
                annotation.shortName.getShortName() == composeUIViewControllerStateAnnotationName.name()
            }
        }
        when {
            stateParameters.size > 1 -> throw MultipleComposeUIViewControllerStateException(composable)
        }
        return stateParameters
    }

    private fun collectTypeAliases(
        externalImports: List<String>,
        packageName: String,
        composableName: String,
        modulesMetadata: List<ModuleMetadata>,
        accumulatedTypeAliases: MutableSet<TypeAliasInfo>
    ) {
        val unflattenedModules = modulesMetadata.filter { !it.flattenPackageConfigured }
        if (unflattenedModules.isEmpty()) return

        val packageToModule = unflattenedModules
            .flatMap { module -> module.packageNames.map { it to module } }
            .toMap()

        val module = packageToModule[packageName] ?: packageToModule.entries.firstOrNull { (key, _) ->
            key.startsWith(packageName) || packageName.startsWith(key)
        }?.value

        if (module != null) {
            accumulatedTypeAliases.add(
                TypeAliasInfo(name = "${composableName}UIViewController", packageName = packageName)
            )
        }

        for (import in externalImports) {
            val lastDotIndex = import.lastIndexOf('.')
            if (lastDotIndex == -1) continue

            val typeName = import.substring(lastDotIndex + 1)
            val pkg = import.take(lastDotIndex)

            val importModule = packageToModule[pkg] ?: packageToModule.entries.firstOrNull { (key, _) ->
                key.startsWith(pkg) || pkg.startsWith(key)
            }?.value

            if (importModule != null) {
                accumulatedTypeAliases.add(TypeAliasInfo(name = typeName, packageName = pkg))
            }
        }
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

    private fun buildExternalModuleParameters(moduleMetadata: List<ModuleMetadata>, imports: List<String>): Map<String, String> {
        val knownPackages = moduleMetadata
            .flatMap { it.packageNames }
            .toSet()

        return imports
            .mapNotNull { import ->
                val lastDotIndex = import.lastIndexOf('.')
                if (lastDotIndex == -1) return@mapNotNull null

                val typeName = import.substring(lastDotIndex + 1)
                val packageName = import.take(lastDotIndex)

                if (packageName in knownPackages) typeName to typeName else null
            }
            .toMap()
    }

    private fun trimFrameworkBaseNames(node: KSAnnotated, moduleMetadata: List<ModuleMetadata>, packageName: String): String {
        val framework = moduleMetadata.firstOrNull { it.packageNames.any { p -> p.startsWith(packageName) } }?.frameworkBaseName ?: ""
        framework.ifEmpty { return getFrameworkBaseNameFromAnnotation(node) ?: throw EmptyFrameworkBaseNameException() }
        return framework
    }

    private fun getFrameworkBaseNames(
        composable: KSFunctionDeclaration,
        node: KSAnnotated,
        makeParameters: List<KSValueParameter>,
        parameters: List<KSValueParameter>,
        modulesMetadata: List<ModuleMetadata>,
        stateParameter: KSValueParameter? = null
    ): List<String> {
        return extractFrameworkBaseNames(composable, modulesMetadata, makeParameters, parameters, stateParameter)
            .filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(getFrameworkBaseNameFromAnnotation(node)) }
            .ifEmpty { throw EmptyFrameworkBaseNameException() }
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
            // This file is auto-generated by KSP. Do not edit manually.
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
        parameters: List<KSValueParameter>,
    ): String {
        val importsParsed = imports.joinToString("\n") { "import $it" }
        val code = """
            // This file is auto-generated by KSP. Do not edit manually.
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
                        $stateParameterName.value?.let { ${composable.name()}(${parameters.toComposableParameters(stateParameterName)}) }
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
            )
            .write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun createSwiftFileWithoutState(
        frameworkBaseNames: List<String>,
        composable: KSFunctionDeclaration,
        makeParameters: List<KSValueParameter>,
        externalParameters: Map<String, String>,
        swiftExportEnabled: Boolean
    ): String {
        val frameworks = (frameworkBaseNames.map { "import ${it.name()}" } + "import SwiftUI").sorted().joinToString("\n")
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") {
            val type = it.resolveType(toSwift = true, withSwiftExport = swiftExportEnabled)
            val finalType = externalParameters[type] ?: type
            "let ${it.name()}: $finalType"
        }
        val code = """
            // This file is auto-generated by KSP. Do not edit manually.
            $frameworks
            
            public struct ${composable.name()}Representable: UIViewControllerRepresentable {
                ${if (letParameters.isNotEmpty()) "$letParameters\n" else ""}
                public func makeUIViewController(context _: Context) -> UIViewController {
                    ${composable.name()}UIViewController${if (swiftExportEnabled) ".shared" else "()"}.make($makeParametersParsed)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context _: Context) {
                    // unused
                }
            }
        """.trimIndent()
        val indentedCode = indentParameters(indentParameters(code, frameworks), letParameters)
        val updatedCode = removeEmptyLineBetweenStructAndFunc(indentedCode)
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "${composable.name()}UIViewControllerRepresentable",
                extensionName = "swift"
            )
            .write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun createSwiftFileWithState(
        frameworkBaseNames: List<String>,
        composable: KSFunctionDeclaration,
        stateParameterName: String,
        stateParameter: KSValueParameter,
        makeParameters: List<KSValueParameter>,
        externalParameters: Map<String, String>,
        swiftExportEnabled: Boolean
    ): String {
        val frameworks = (frameworkBaseNames.map { "import ${it.name()}" } + "import SwiftUI").sorted().joinToString("\n")
        val makeParametersParsed = makeParameters.joinToString(", ") { "${it.name()}: ${it.name()}" }
        val letParameters = makeParameters.joinToString("\n") {
            val type = it.resolveType(toSwift = true, withSwiftExport = swiftExportEnabled)
            val finalType = externalParameters[type] ?: type
            "let ${it.name()}: $finalType"
        }
        val stateType = stateParameter.resolveType(toSwift = true, withSwiftExport = swiftExportEnabled)
        val finalStateType = externalParameters[stateType] ?: stateType
        val code = """
            // This file is auto-generated by KSP. Do not edit manually.
            $frameworks
            
            public struct ${composable.name()}Representable: UIViewControllerRepresentable {
                @Binding var $stateParameterName: $finalStateType
                ${if (letParameters.isNotEmpty()) "$letParameters\n" else ""}
                public func makeUIViewController(context _: Context) -> UIViewController {
                    ${composable.name()}UIViewController${if (swiftExportEnabled) ".shared" else "()"}.make($makeParametersParsed)
                }

                public func updateUIViewController(_ uiViewController: UIViewController, context _: Context) {
                    ${composable.name()}UIViewController${if (swiftExportEnabled) ".shared" else "()"}.update($stateParameterName: $stateParameterName)
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
            )
            .write(updatedCode.toByteArray())
        return updatedCode
    }

    private fun createConsolidatedSwiftFileWithTypeAlias(info: List<TypeAliasInfo>): String {
        val warning = """
            // This file is auto-generated by KSP. Do not edit manually.
            // It contains typealias for external dependencies used in @${composeUIViewControllerAnnotationName.name()} composables.
            // If you get errors about missing types, consider using the 'flattenPackage' property in KMP swiftExport settings.
        """.trimIndent()
        val importStatement = "import ExportedKotlinPackages"

        val types = info.joinToString("\n") {
            val hint = "//This typealias can be avoided if you use the `flattenPackage = " +
                    "\"${it.packageName.split(".${it.name}").first()}\"` in KMP swiftExport settings"
            "${hint}\ntypealias ${it.name} = ExportedKotlinPackages.${it.packageName}.${it.name}"
        }

        val code = buildString {
            appendLine(warning)
            append(importStatement)
            append("\n\n")
            append(types)
        }

        codeGenerator
            .createNewFile(
                dependencies = Dependencies(true),
                packageName = "",
                fileName = "TypeAliasForExternalDependencies",
                extensionName = "swift"
            )
            .write(code.toByteArray())

        return code
    }
}
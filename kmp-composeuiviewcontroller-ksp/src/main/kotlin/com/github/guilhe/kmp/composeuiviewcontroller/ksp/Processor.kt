package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.Module
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
import java.util.Locale

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
                    val modules = getFrameworkMetadataFromJson()
                    val externalModuleTypes = buildExternalModuleParameters(modules, imports)

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

    private fun getFrameworkMetadataFromJson(): List<Module> {
        val file = File("./build/$TEMP_FILES_FOLDER/$FILE_NAME_ARGS")
        val modules = try {
            Json.decodeFromString<List<Module>>(file.readText())
        } catch (e: Exception) {
            throw ModuleDecodeException()
        }
        return modules
    }

    /**
     * Theres an [actual limitation](https://kotlinlang.slack.com/archives/C3SGXARS6/p1719961104891399) on Kotlin Multiplatform where each binary framework is compiled as a "closed world“, meaning it's not possible to pass custom type between two frameworks even it’s the same in Kotlin.
     *
     * Let’s say I have two modules, `shared` and `shared-models`, each providing their own binary frameworks: “Shared” and “SharedModels” respectively. The `shared-models` contains a `data class Hello`, and the `shared` module `implements(project(":shared-models"))` and has a public method that takes `Hello` as a parameter.
     *
     * When these modules are exported to Swift, we see the following:
     *
     * SharedModels: `public class Hello : KotlinBase`
     *
     * Shared: `public class Shared_modelsHello : KotlinBase`
     *
     * Shared: `open func update(state: Shared_modelsHello)`
     *
     * Instead of:
     *
     * SharedModels: `public class Hello : KotlinBase`
     *
     * Shared: `open func update(state: Hello)`
     *
     * It means that the "Shared" framework will include all this external dependencies (from the "SharedModel" in this case) sand will generate new types to reference those external types. That's why we endup having `Shared_modelsHello` instead of just `Hello`.
     *
     * [https://stackoverflow.com/a/78707072/1423773](https://stackoverflow.com/a/78707072/1423773)
     */
    private fun buildExternalModuleParameters(modules: List<Module>, imports: List<String>): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        imports.forEach { it ->
            val type = it.split(".").last()
            val import = it.split(".$type").first()
            modules
                .filter { module -> module.packageName == import }
                .forEach { module ->
                    val capitalized = module.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    val replaced = capitalized.replace("-", "_")
                    result[type] = "$replaced$type"
                }
        }
        return result
    }

    /**
     * This will be needed until [buildExternalModuleParameters] is needed to. When (if) KPM limitation is addressed this will become deprecated and substituted by [getFrameworkBaseNames]
     */
    private fun trimFrameworkBaseNames(node: KSAnnotated, packageName: String): String {
        val metadata = getFrameworkMetadataFromJson()
        if (metadata.isEmpty()) {
            val framework = getFrameworkBaseNameFromAnnotation(node) ?: throw EmptyFrameworkBaseNameException()
            return framework
        } else {
            val framework =
                metadata.firstOrNull { it.packageName == packageName }?.frameworkBaseName?: ""
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
                getFrameworkMetadataFromJson(),
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
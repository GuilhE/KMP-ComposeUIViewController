package com.github.guilhe.ksp.composeuiviewcontroller

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

public class Processor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getSymbolsWithAnnotation(ComposeUIViewController::class.qualifiedName.toString())
        if (!candidates.iterator().hasNext()) {
            logger.info("No @${ComposeUIViewController::class.simpleName.toString()} found!")
            return emptyList()
        }

        val trimmedCandidates = candidates.distinctBy { it.containingFile?.fileName }
        for (node in trimmedCandidates) {
            node.containingFile?.let { file ->
                val packageName = file.packageName.asString()
                for (composable in file.declarations.filterIsInstance<KSFunctionDeclaration>()) {
                    val parameters: List<KSValueParameter> = composable.parameters
                    val stateParameters = parameters.filter {
                        it.annotations
                            .filter { annotation -> annotation.shortName.getShortName() == ComposeUIViewControllerState::class.simpleName.toString() }
                            .toList()
                            .isNotEmpty()
                    }

                    when {
                        stateParameters.size > 1 -> throw IllegalArgumentException(
                            "The composable ${composable.name()} has more than one parameter annotated " +
                                    "with @${ComposeUIViewControllerState::class.simpleName.toString()}."
                        )

                        stateParameters.isEmpty() -> throw IllegalArgumentException(
                            "The composable ${composable.name()} is annotated with @${ComposeUIViewController::class.simpleName.toString()} " +
                                    "but it's missing the ui state parameter annotated with @${ComposeUIViewControllerState::class.simpleName.toString()}."
                        )
                    }

                    val stateParameter = stateParameters.first()
                    val stateParameterName = stateParameter.name()
                    val makeParameters = parameters.filterNot { it.type == stateParameter.type }.filterFunctions()

                    if (parameters.size != makeParameters.size + 1) {
                        throw IllegalArgumentException(
                            "Only 1 @${ComposeUIViewControllerState::class.simpleName.toString()} and " +
                                    "N high-order function parameters (excluding @Composable content: () -> Unit) are allowed."
                        )
                    }

                    val code = """
                       package $packageName
                        
                       import androidx.compose.runtime.mutableStateOf
                       import androidx.compose.ui.window.ComposeUIViewController
                       import platform.UIKit.UIViewController
                       import $packageName.${composable.name()}
                       import $packageName.${stateParameter.type}
                       
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

                    logger.info("\n$code")

                    codeGenerator
                        .createNewFile(
                            dependencies = Dependencies(true),
                            packageName = packageName,
                            fileName = "${composable.name()}UIViewController",
                        ).write(code.toByteArray())

                    logger.info("\n${composable.name()}UIViewController created!")
                }
            }
        }
        return emptyList()
    }

    private fun List<KSValueParameter>.toComposableParameters(stateParameterName: String): String =
        joinToString(", ") { if (it.name() == stateParameterName) "${it.name()}.value" else it.name() }

    private fun List<KSValueParameter>.filterFunctions(): List<KSValueParameter> =
        filter { it.type.resolve().isFunctionType && it.annotations.none { annotation -> annotation.shortName.getShortName() == "Composable" } }

    private fun List<KSValueParameter>.joinToString(): String = joinToString(", ") { "${it.name!!.getShortName()}: ${it.type}" }

    private fun KSFunctionDeclaration.name(): String = qualifiedName!!.getShortName()

    private fun KSValueParameter.name(): String = name!!.getShortName()
}
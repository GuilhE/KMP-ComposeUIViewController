package com.github.guilhe.ksp.composeuiviewcontroller

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

public class Processor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getSymbolsWithAnnotation(ComposeUIViewController::class.qualifiedName.toString())
        if (!candidates.iterator().hasNext()) {
            logger.info("No @${ComposeUIViewController::class.simpleName.toString()} found!")
            return emptyList()
        }

        val trimmedCandidates = candidates.distinctBy { it.containingFile?.fileName }
        for (symbol in trimmedCandidates) {
            symbol.containingFile?.let { file ->
                val packageName = file.packageName.asString()
                for (composable in file.declarations.filterIsInstance<KSFunctionDeclaration>()) {
                    val parameters: List<KSValueParameter> = composable.parameters
                    val stateParameters = parameters.filter { p ->
                        p.annotations.filter { it.shortName.getShortName() == ComposeUIViewControllerState::class.simpleName.toString() }.toList()
                            .isNotEmpty()
                    }

                    when {
                        stateParameters.size > 1 -> throw IllegalArgumentException(
                            "The composable ${composable.qualifiedName!!.getShortName()} has more than one parameter annotated with @${ComposeUIViewControllerState::class.simpleName.toString()}."
                        )

                        stateParameters.isEmpty() -> throw IllegalArgumentException(
                            "The composable ${composable.qualifiedName!!.getShortName()} is annotated with @${ComposeUIViewController::class.simpleName.toString()} but it's missing the ui state parameter annotated with @${ComposeUIViewControllerState::class.simpleName.toString()}."
                        )
                    }

                    val stateParameter = stateParameters.first()
                    val stateParameterName = stateParameter.name!!.getShortName()
                    val makeParameters = parameters.filterNot { it.type == stateParameter.type }.filterFunctions()

                    if (parameters.size != makeParameters.size + 1) {
                        throw IllegalArgumentException("Only 1 @${ComposeUIViewControllerState::class.simpleName.toString()} and N high-order function parameters (excluding @Composable content: () -> Unit) are allowed.")
                    }

                    val generatedCode = """
                       package $packageName
                        
                       import androidx.compose.runtime.collectAsState
                       import androidx.compose.runtime.mutableStateOf
                       import androidx.compose.ui.window.ComposeUIViewController
                       import platform.UIKit.UIViewController
                       import $packageName.${composable.qualifiedName!!.getShortName()}
                       
                       object ${composable.qualifiedName!!.getShortName()}UIViewController {
                           private val $stateParameterName = mutableStateOf(${stateParameter.type}())

                           fun make(${makeParameters.joinToString()}): UIViewController {
                               return ComposeUIViewController {
                                   ${composable.qualifiedName!!.getShortName()}(${parameters.joinToString(", ") { if (it.name!!.getShortName() == stateParameterName) "${it.name!!.getShortName()}.value" else it.name!!.getShortName() }})
                               }
                           }

                           fun update($stateParameterName: ${stateParameter.type}) {
                               this.$stateParameterName.value = $stateParameterName
                           }
                       }
                   """.trimIndent()

                    logger.info("\n"+generatedCode)

                    codeGenerator
                        .createNewFile(
                            dependencies = Dependencies(true),
                            packageName = packageName,
                            fileName = "${composable.qualifiedName!!.getShortName()}UIViewController",
                        ).write(generatedCode.toByteArray())
                }
            }
        }
        return emptyList()
    }

    private fun List<KSValueParameter>.filterFunctions(): List<KSValueParameter> {
        return filter { p -> p.type.resolve().isFunctionType && p.annotations.none { it.shortName.getShortName() == "Composable" } }
    }

    private fun List<KSValueParameter>.joinToString(): String {
        return joinToString(", ") { "${it.name!!.getShortName()}: ${it.type}" }
    }
}
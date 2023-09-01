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
                    val parameters = composable.parameters
                    val stateParameter = parameters
                        .find { p ->
                            p.annotations.firstOrNull {
                                it.shortName.getShortName() == ComposeUIViewControllerState::class.simpleName.toString()
                            } != null
                        }
                        ?: throw IllegalStateException(
                            "The composable ${composable.qualifiedName!!.getShortName()} is annotated with @${ComposeUIViewController::class.simpleName.toString()} but it's missing the ui state parameter annotated with @${ComposeUIViewControllerState::class.simpleName.toString()}."
                        )

                    val stateParameterName = stateParameter.name!!.getShortName()
                    val makeParameters = parameters.filterNot { it.type == stateParameter.type }.filterFunctions()
                    val updateParameters = "$stateParameterName: ${stateParameter.type}"
                    val generatedCode = """
                       package $packageName
                        
                       import androidx.compose.runtime.collectAsState
                       import androidx.compose.runtime.mutableStateOf
                       import androidx.compose.ui.window.ComposeUIViewController
                       import platform.UIKit.UIViewController
                       import $packageName.${composable.qualifiedName!!.getShortName()}
                       
                       object ${composable.qualifiedName!!.getShortName()}UIViewController {
                           private val $stateParameterName = mutableStateOf(${stateParameter.type}())

                           fun make($makeParameters): UIViewController {
                               return ComposeUIViewController {
                                   ${composable.qualifiedName!!.getShortName()}(${parameters.joinToString(", ") { if (it.name!!.getShortName() == stateParameterName) "${it.name!!.getShortName()}.value" else it.name!!.getShortName() }})
                               }
                           }

                           fun update($updateParameters) {
                               this.$stateParameterName.value = $stateParameterName
                           }
                       }
                   """.trimIndent()

                    logger.info(generatedCode)

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

    private fun List<KSValueParameter>.filterFunctions(): String {
        return filter { parameter ->
            parameter.type.resolve().isFunctionType && parameter.type.annotations.none { it.shortName.getShortName() == "Composable" }
        }.joinToString(", ") { "${it.name!!.getShortName()}: ${it.type}" }
    }
}
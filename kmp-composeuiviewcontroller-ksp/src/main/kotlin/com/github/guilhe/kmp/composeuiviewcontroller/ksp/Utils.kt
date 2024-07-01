package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * @param type Kotlin type to be converted to Swift type
 * @return String with Swift type
 * @see https://kotlinlang.org/docs/apple-framework.html#generated-framework-headers
 */
@Suppress("KDocUnresolvedReference")
internal fun kotlinTypeToSwift(type: KSTypeReference): String {
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

internal fun indentParameters(code: String, parameters: String): String {
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

internal fun generateImports(
    packageName: String,
    makeParameters: List<KSValueParameter>,
    parameters: List<KSValueParameter>,
    stateParameter: KSValueParameter? = null
): String {
    val parameterSet = setOf<KSValueParameter>()
        .plus(makeParameters)
        .plus(parameters)
    stateParameter?.let { parameters.plus(it) }
    return parameterSet
        .mapNotNull {
            val resolvedType = it.type.resolve()
            val typeDeclaration = resolvedType.declaration
//            println(">> Type: ${it.type}, Resolved: $resolvedType, Declaration: $typeDeclaration")
            val typePackage = (typeDeclaration as? KSClassDeclaration)?.packageName?.asString()
//            println(">> Type Package: $typePackage")
            if (typePackage != null && packageName != typePackage && !typePackage.startsWith("kotlin")) {
                "$typePackage.${resolvedType.declaration.simpleName.asString()}"
            } else null
        }
        .distinct()
        .joinToString("\n") { "import $it" }
}

internal fun retrieveFrameworkBaseNames(
    frameworkMetadata: List<FrameworkMetadata>,
    makeParameters: List<KSValueParameter>,
    parameters: List<KSValueParameter>,
    stateParameter: KSValueParameter? = null
): List<String> {
    val parameterSet = setOf<KSValueParameter>()
        .plus(makeParameters)
        .plus(parameters)
    stateParameter?.let { parameters.plus(it) }

    val parameterPackages = parameterSet
        .mapNotNull {
            val resolvedType = it.type.resolve()
            val typeDeclaration = resolvedType.declaration
            (typeDeclaration as? KSClassDeclaration)?.packageName?.asString()
        }
        .filterNot { it.startsWith("kotlin") }
        .distinct()

    println(">>>> frameworkMetadata >>>> $frameworkMetadata")
    println(">>>> parameterPackages >>>> $parameterPackages")
    return parameterPackages.mapNotNull { pkg ->
        frameworkMetadata.find { it.packageName == pkg }?.baseName?.removePrefix("$frameworkBaseNameAnnotationParameter-")
    }.distinct()
}

internal fun String.name() = split(".").last()

internal fun List<KSValueParameter>.toComposableParameters(stateParameterName: String): String =
    joinToString(", ") { if (it.name() == stateParameterName) "it" else it.name() }

internal fun List<KSValueParameter>.toComposableParameters(): String = joinToString(", ") { it.name() }

internal fun List<KSValueParameter>.filterComposableFunctions(): List<KSValueParameter> =
    filter { it.annotations.none { annotation -> annotation.shortName.getShortName() == "Composable" } }

internal fun List<KSValueParameter>.joinToString(): String = joinToString(", ") { "${it.name!!.getShortName()}: ${it.type}" }

internal fun KSFunctionDeclaration.name(): String = qualifiedName!!.getShortName()

internal fun KSValueParameter.name(): String = name!!.getShortName()

internal data class FrameworkMetadata(val baseName: String, val packageName: String)

internal class EmptyFrameworkBaseNameException : IllegalArgumentException(
    "@${composeUIViewControllerAnnotationName.name()} requires a non-null and non-empty value for $frameworkBaseNameAnnotationParameter"
)

internal class MultipleComposeUIViewControllerStateException(composable: KSFunctionDeclaration) : IllegalArgumentException(
    "The composable ${composable.name()} has more than one parameter annotated with @${composeUIViewControllerStateAnnotationName.name()}."
)

internal class InvalidParametersException : IllegalArgumentException(
    "Only 1 @${composeUIViewControllerStateAnnotationName.name()} and " +
            "N high-order function parameters (excluding @Composable content: () -> Unit) are allowed."
)
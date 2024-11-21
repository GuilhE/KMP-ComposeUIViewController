package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * Resolves KSValueParameter type
 * @param toSwift If true, transforms Kotlin types into their Swift representation. [Apple framework generated framework headers](https://kotlinlang.org/docs/apple-framework.html#generated-framework-headers)
 * @return String with type resolved
 * @throws ValueParameterResolutionError when type cannot be resolved
 */
internal fun KSValueParameter.resolveType(toSwift: Boolean = false): String {
    //println(">> KSValueParameter type: ${type}")
    val resolvedType = type.resolve()
    return if (resolvedType.isFunctionType) {
        buildString {
            append("(")
            append(resolvedType.arguments.dropLast(1).joinToString(", ") { arg ->
                val argType = arg.type?.resolve()
                if (argType == null || argType.isError) {
                    throw ValueParameterResolutionError(this@resolveType)
                } else {
                    convertGenericType(argType, toSwift)
                }
            })
            append(") -> ")
            val returnType = resolvedType.arguments.last().type?.resolve()
            val returnTypeName = if (returnType == null || returnType.isError) {
                throw ValueParameterResolutionError(this@resolveType)
            } else {
                convertGenericType(returnType, toSwift)
            }
            append(returnTypeName)
        }
    } else {
        convertGenericType(resolvedType, toSwift)
    }
}

private fun convertGenericType(type: KSType, toSwift: Boolean): String {
    val baseType = type.declaration.simpleName.asString()
    val convertedBaseType = if (toSwift) convertToSwift(baseType) else baseType
    if (type.arguments.isEmpty()) return convertedBaseType
    val generics = type.arguments.joinToString(", ") { arg ->
        arg.type?.resolve()?.let { convertGenericType(it, toSwift) } ?: throw TypeResolutionError(type)
    }
    return "$convertedBaseType<$generics>"
}

private fun convertToSwift(baseType: String): String {
    return when (baseType) {
        "Unit" -> "Void"
        "List" -> "Array"
        "MutableList" -> "NSMutableArray"
        "Set" -> "Set"
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
        else -> baseType
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

/**
 * Iterates all parameters and returns package names that do not belong to the module's [packageName].
 *
 * @param packageName Module package name
 * @param makeParameters List of parameters to be used in the UIViewController make function
 * @param stateParameter Parameter representing UIViewController state (for advanced cases)
 * @return List of package names that do not belong to the current module
 * @throws [ValueParameterResolutionError] If type not found or invalid
 */
internal fun extractImportsFromExternalPackages(
    packageName: String,
    makeParameters: List<KSValueParameter>,
    parameters: List<KSValueParameter>,
    stateParameter: KSValueParameter? = null
): List<String> {
    val parameterSet = setOf<KSValueParameter>()
        .plus(makeParameters)
        .plus(parameters)
    stateParameter?.let { parameters.plus(it) }
    return parameterSet
        .mapNotNull {
            val resolvedType = it.type.resolve()
            if (resolvedType.isError) throw ValueParameterResolutionError(it)
            val typeDeclaration = resolvedType.declaration
//            println(">> Type: ${it.type}, Resolved: $resolvedType, Declaration: $typeDeclaration")
            val typePackage = (typeDeclaration as? KSClassDeclaration)?.packageName?.asString()
//            println(">> Type Package: $typePackage")
            if (typePackage != null && packageName != typePackage && !typePackage.startsWith("kotlin")) {
                "$typePackage.${resolvedType.declaration.simpleName.asString()}"
            } else null
        }
        .distinct()
}

/**
 * Iterates all elements package names and returns the distinct and respective frameworkBaseName
 *
 * @param composable Composable [KSFunctionDeclaration]
 * @param moduleMetadata List of [ModuleMetadata] containing all project's modules metadata
 * @param makeParameters List of parameters to be used in the UIViewController make function
 * @param parameters List of parameters to be used in Swift the UIViewControllerRepresentable file
 * @param stateParameter Parameter representing UIViewController state (for advanced cases)
 * @return List of frameworkBaseName
 * @throws [ValueParameterResolutionError] If type not found or invalid
 */
internal fun extractFrameworkBaseNames(
    composable: KSFunctionDeclaration,
    moduleMetadata: List<ModuleMetadata>,
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
            if (resolvedType.isError) throw ValueParameterResolutionError(it)
            (resolvedType.declaration as? KSClassDeclaration)?.packageName?.asString()
        }
        .filterNot { it.startsWith("kotlin") }
        .distinct()
        .toMutableList()

    parameterPackages.add(composable.packageName.asString())

    return parameterPackages
        .mapNotNull { pkg -> moduleMetadata.find { it.packageNames.any { packageName -> packageName.startsWith(pkg) } }?.frameworkBaseName }
        .distinct()
}

internal fun String.name() = split(".").last()

internal fun List<KSValueParameter>.toComposableParameters(stateParameterName: String): String =
    joinToString(", ") { if (it.name() == stateParameterName) "it" else it.name() }

internal fun List<KSValueParameter>.toComposableParameters(): String = joinToString(", ") { it.name() }

internal fun List<KSValueParameter>.filterComposableFunctions(): List<KSValueParameter> =
    filter { it.annotations.none { annotation -> annotation.shortName.getShortName() == "Composable" } }

internal fun List<KSValueParameter>.joinToStringDeclaration(separator: CharSequence = ", "): String = joinToString(separator) {
    "${it.name!!.getShortName()}: ${it.resolveType()}"
}

internal fun KSFunctionDeclaration.name(): String = qualifiedName!!.getShortName()

internal fun KSValueParameter.name(): String = name!!.getShortName()

internal class EmptyFrameworkBaseNameException : IllegalArgumentException(
    "@${composeUIViewControllerAnnotationName.name()} requires a non-null and non-empty value for $frameworkBaseNameAnnotationParameter"
)

internal class MultipleComposeUIViewControllerStateException(composable: KSFunctionDeclaration) : IllegalArgumentException(
    "The composable ${composable.name()} has more than one parameter annotated with @${composeUIViewControllerStateAnnotationName.name()}."
)

internal class InvalidParametersException : IllegalArgumentException("@Composable functions are not allowed as parameter")

internal class ValueParameterResolutionError(parameter: KSValueParameter) : IllegalArgumentException(
    "Cannot resolve type for parameter ${parameter.name()} from ${parameter.location}. Check your file imports"
)

internal class TypeResolutionError(parameter: KSType) : IllegalArgumentException("Cannot resolve type for parameter $parameter.")

internal class ModuleDecodeException(e: Exception) :
    IllegalArgumentException("Could not decode $FILE_NAME_ARGS file with exception: ${e.localizedMessage}")
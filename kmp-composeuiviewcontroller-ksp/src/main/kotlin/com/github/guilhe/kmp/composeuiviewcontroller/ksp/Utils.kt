package com.github.guilhe.kmp.composeuiviewcontroller.ksp

import com.github.guilhe.kmp.composeuiviewcontroller.common.FILE_NAME_ARGS
import com.github.guilhe.kmp.composeuiviewcontroller.common.ModuleMetadata
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * Resolves KSValueParameter type
 * @param toSwift If true, transforms Kotlin types into their Swift representation.
 * @param withSwiftExport If true, will use [Swift-interop instead of ObjC interop](https://kotlinlang.org/docs/native-objc-interop.html#swift-exported-symbols)
 * @return String with type resolved
 * @throws ValueParameterResolutionError when type cannot be resolved
 */
internal fun KSValueParameter.resolveType(toSwift: Boolean = false, withSwiftExport: Boolean = false): String {
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
                    // When inside a function type, primitives need wrappers in ObjC export
                    convertGenericType(argType, toSwift, withSwiftExport, insideFunctionType = true)
                }
            })
            append(") -> ")
            val returnType = resolvedType.arguments.last().type?.resolve()
            val returnTypeName = if (returnType == null || returnType.isError) {
                throw ValueParameterResolutionError(this@resolveType)
            } else {
                convertGenericType(returnType, toSwift, withSwiftExport, insideFunctionType = true)
            }
            append(returnTypeName)
        }
    } else {
        // Direct parameter - primitives use native types in ObjC export
        convertGenericType(resolvedType, toSwift, withSwiftExport, insideFunctionType = false)
    }
}

private fun convertGenericType(type: KSType, toSwift: Boolean, withSwiftExport: Boolean, insideFunctionType: Boolean = false): String {
    val baseType = type.declaration.simpleName.asString()
    val isNullable = type.isMarkedNullable

    if (toSwift && type.arguments.isNotEmpty() && baseType in listOf("List", "MutableList", "Set", "MutableSet", "Map", "MutableMap")) {
        return convertCollectionType(type, baseType, isNullable, withSwiftExport)
    }

    val convertedBaseType = if (toSwift) convertToSwift(baseType, withSwiftExport, isNullable, insideFunctionType) else baseType

    if (type.arguments.isEmpty()) {
        return if (isNullable) "$convertedBaseType?" else convertedBaseType
    }

    // When we have generics, primitives inside them need wrappers (e.g., GenericData<Int> -> GenericData<KotlinInt>)
    val generics = type.arguments.joinToString(", ") { arg ->
        arg.type?.resolve()?.let { convertGenericType(it, toSwift, withSwiftExport, insideFunctionType = true) } ?: throw TypeResolutionError(type)
    }

    val result = "$convertedBaseType<$generics>"
    return if (isNullable) "$result?" else result
}

private val PRIMITIVE_TYPES = setOf(
    "Byte", "UByte", "Short", "UShort", "Int", "UInt",
    "Long", "ULong", "Float", "Double", "Boolean", "Char", "String"
)

private fun isPrimitiveType(type: String): Boolean = type in PRIMITIVE_TYPES

/**
 * Handle collection types with primitive elements according to ObjC/Swift export rules
 * Collections with primitive types (except String) require wrapper types:
 * - List<Int> -> Array<KotlinInt>
 * - List<String> -> Array<String> (exception)
 * - List<Char> -> Array<Any> (exception for ObjC export)
 *
 * [Collections with basic types](https://github.com/kotlin-hands-on/kotlin-swift-interopedia/blob/main/docs/types/Collections%20with%20basic%20types.md)
 * [Collections with custom types](https://github.com/kotlin-hands-on/kotlin-swift-interopedia/blob/main/docs/types/Collections%20with%20custom%20types.md)
 * [Mutable, immutable collections](https://github.com/kotlin-hands-on/kotlin-swift-interopedia/blob/main/docs/types/Mutable%2C%20immutable%20collections.md)
 */
private fun convertCollectionType(type: KSType, baseType: String, isNullable: Boolean, withSwiftExport: Boolean): String {
    val generics = type.arguments.map { arg ->
        arg.type?.resolve()?.let { argType ->
            val argBaseType = argType.declaration.simpleName.asString()
            val argIsNullable = argType.isMarkedNullable

            if (isPrimitiveType(argBaseType)) {
                convertPrimitiveInCollection(argBaseType, argIsNullable, withSwiftExport)
            } else {
                // Inside collections, always use wrappers for nested types
                convertGenericType(argType, true, withSwiftExport, insideFunctionType = true)
            }
        } ?: throw TypeResolutionError(type)
    }

    val convertedCollection = when (baseType) {
        "List" -> "Array"
        "MutableList" -> if (withSwiftExport) "Array" else "NSMutableArray"
        "Set" -> "Set"
        "MutableSet" -> if (withSwiftExport) "Set" else "KotlinMutableSet"
        "Map" -> "Dictionary"
        "MutableMap" -> if (withSwiftExport) "Dictionary" else "NSMutableDictionary"
        else -> baseType
    }

    // NSMutableArray and NSMutableDictionary DO support generics in Swift (they are bridged from ObjC)
    val result = "$convertedCollection<${generics.joinToString(", ")}>"

    return if (isNullable) "$result?" else result
}

/**
 * Convert primitive types when used inside collections
 * According to ObjC export rules, primitives in collections are wrapped (except String)
 *
 * [Collections with basic types](https://github.com/kotlin-hands-on/kotlin-swift-interopedia/blob/main/docs/types/Collections%20with%20basic%20types.md)
 *
 */
private fun convertPrimitiveInCollection(primitiveType: String, isNullable: Boolean, withSwiftExport: Boolean): String {
    if (withSwiftExport) {
        return convertToSwiftFromSwiftExport(primitiveType) + if (isNullable) "?" else ""
    }

    val converted = when (primitiveType) {
        "String" -> "String"  // Exception: String doesn't need wrapper
        "Char" -> "Any"       // Exception: Char becomes Any in collections
        else -> "Kotlin$primitiveType"  // All other primitives get Kotlin prefix
    }

    return if (isNullable) "$converted?" else converted
}

private fun convertToSwift(baseType: String, withSwiftExport: Boolean, isNullable: Boolean = false, insideFunctionType: Boolean = false): String {
    return if (withSwiftExport) {
        convertToSwiftFromSwiftExport(baseType)
    } else {
        convertToSwiftFromObjcExport(baseType, isNullable, insideFunctionType)
    }
}

private val OBJC_COLLECTION_TYPES = mapOf(
    "Unit" to null,
    "List" to null,
    "MutableList" to "NSMutableArray",
    "Set" to null,
    "MutableSet" to "KotlinMutableSet",
    "Map" to null,
    "MutableMap" to "NSMutableDictionary",
    "String" to null
)

private val OBJC_PRIMITIVE_WRAPPERS = setOf(
    "Byte", "UByte", "Short", "UShort", "Int", "UInt",
    "Long", "ULong", "Float", "Double", "Boolean"
)

/**
 *  [Kotlin to Swift from objcExport](https://github.com/kotlin-hands-on/kotlin-swift-interopedia/tree/main/docs/types)
 *  Note:
 *  - Nullable primitive types are wrapped in special Kotlin wrapper types (e.g., Int? -> KotlinInt?)
 *  - Non-nullable primitives inside functions/closures need wrappers (e.g., (Int) -> Unit becomes (KotlinInt) -> Void)
 *  - Non-nullable primitives as direct parameters use native Swift types (e.g., Int -> Int32, Boolean -> Bool)
 *  Exceptions: String? -> String?, Char? -> Any?
 */
private fun convertToSwiftFromObjcExport(baseType: String, isNullable: Boolean = false, insideFunctionType: Boolean = false): String {
    OBJC_COLLECTION_TYPES[baseType]?.let { return it }
    if (baseType in OBJC_COLLECTION_TYPES) return convertToSwiftFromSwiftExport(baseType)

    if (baseType == "Char") {
        return if (isNullable || insideFunctionType) "Any" else "unichar"
    }

    if (baseType in OBJC_PRIMITIVE_WRAPPERS) {
        return when {
            isNullable || insideFunctionType -> "Kotlin$baseType"
            else -> convertToSwiftFromSwiftExport(baseType)
        }
    }

    return baseType
}

/**
 *  [Kotlin to Swift mapping - Built-in types](https://github.com/JetBrains/kotlin/blob/master/docs/swift-export/language-mapping.md#built-in-types)
 */
private fun convertToSwiftFromSwiftExport(baseType: String): String {
    return when (baseType) {
        "Unit" -> "Void"
        "List" -> "Array"
        "MutableList" -> "Array"
        "Set" -> "Set"
        "MutableSet" -> "Set"
        "Map" -> "Dictionary"
        "MutableMap" -> "Dictionary"
        "Byte" -> "Int8"
        "UByte" -> "UInt8"
        "Short" -> "Int16"
        "UShort" -> "UInt16"
        "Int" -> "Int32"
        "UInt" -> "UInt32"
        "Long" -> "Int64"
        "ULong" -> "UInt64"
        "Float" -> "Float"
        "Double" -> "Double"
        "Boolean" -> "Bool"
        "String" -> "String"
        "Char" -> "Unicode.UTF16.CodeUnit"
        "Nothing" -> "Never"
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
    val allParameters = buildList {
        addAll(makeParameters)
        addAll(parameters)
        stateParameter?.let { add(it) }
    }

    val seen = mutableSetOf<String>()
    return allParameters
        .mapNotNull {
            val resolvedType = it.type.resolve()
            if (resolvedType.isError) throw ValueParameterResolutionError(it)
            val typeDeclaration = resolvedType.declaration
            val typePackage = (typeDeclaration as? KSClassDeclaration)?.packageName?.asString()
            if (typePackage != null && packageName != typePackage && !typePackage.startsWith("kotlin")) {
                "$typePackage.${resolvedType.declaration.simpleName.asString()}"
            } else null
        }
        .filter { seen.add(it) }
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
    val allParameters = buildList {
        addAll(makeParameters)
        addAll(parameters)
        stateParameter?.let { add(it) }
    }

    val parameterPackages = allParameters
        .mapNotNull {
            val resolvedType = it.type.resolve()
            if (resolvedType.isError) throw ValueParameterResolutionError(it)
            (resolvedType.declaration as? KSClassDeclaration)?.packageName?.asString()
        }
        .filterNot { it.startsWith("kotlin") }
        .distinct()
        .plus(composable.packageName.asString())

    return parameterPackages
        .mapNotNull { pkg -> moduleMetadata.find { it.packageNames.any { packageName -> packageName.startsWith(pkg) } }?.frameworkBaseName }
        .distinct()
}

internal fun String.name(): String {
    val lastDotIndex = lastIndexOf('.')
    return if (lastDotIndex == -1) this else substring(lastDotIndex + 1)
}

internal fun List<KSValueParameter>.toComposableParameters(stateParameterName: String): String =
    joinToString(", ") { if (it.name() == stateParameterName) "it" else it.name() }

internal fun List<KSValueParameter>.toComposableParameters(): String = joinToString(", ") { it.name() }

internal fun List<KSValueParameter>.filterNotComposableFunctions(): List<KSValueParameter> =
    filter { valueParameter ->
        valueParameter.annotations.none { annotation ->
            val isComposableByName = annotation.shortName.getShortName() == "Composable"
            val isErrorType = annotation.annotationType.resolve().isError
            isComposableByName || isErrorType
        }
    }

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
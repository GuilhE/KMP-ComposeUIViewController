@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.common

import kotlinx.serialization.Serializable

@Serializable
public data class ModuleMetadata(
    val name: String,
    val packageNames: Set<String>,
    val frameworkBaseName: String,
    val swiftExport: Boolean
)

public const val TEMP_FILES_FOLDER: String = "composeuiviewcontroller"
public const val FILE_NAME_ARGS: String = "modules.json"

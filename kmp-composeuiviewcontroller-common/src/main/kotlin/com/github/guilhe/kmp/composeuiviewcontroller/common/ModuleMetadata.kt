package com.github.guilhe.kmp.composeuiviewcontroller.common

import kotlinx.serialization.Serializable

@Serializable
public data class ModuleMetadata(val name: String, val packageName: Set<String>, val frameworkBaseName: String)

public const val TEMP_FILES_FOLDER: String = "composeuiviewcontroller"
public const val FILE_NAME_ARGS: String = "modules.json"

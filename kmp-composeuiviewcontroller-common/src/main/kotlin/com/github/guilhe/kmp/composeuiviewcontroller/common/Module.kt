package com.github.guilhe.kmp.composeuiviewcontroller.common

import kotlinx.serialization.Serializable

@Serializable
public data class Module(val name: String, val packageName: String, val frameworkBaseName: String)

public const val FILE_NAME_ARGS: String = "modules.json"
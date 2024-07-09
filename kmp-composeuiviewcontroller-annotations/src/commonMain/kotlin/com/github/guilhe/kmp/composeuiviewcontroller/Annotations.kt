package com.github.guilhe.kmp.composeuiviewcontroller

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HidesFromObjC

/**
 * Used to annotate the @Composable as a desired ComposeUIViewController to be used by the iOS app. While the plugin typically attempts to retrieve this name automatically, you can use this parameter to enforce a specific name if the automatic retrieval fails.
 * [More here](https://github.com/GuilhE/KMP-ComposeUIViewController?tab=readme-ov-file#kmp-module)
 * @param frameworkBaseName Kotlin Multiplatform library iOS targets framework base name
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@OptIn(ExperimentalObjCRefinement::class)
@HidesFromObjC
public annotation class ComposeUIViewController(val frameworkBaseName: String = "")

/**
 * Used to annotate the parameter as the composable state variable (for [advanced](https://github.com/GuilhE/KMP-ComposeUIViewController?tab=readme-ov-file#advanced) use cases). Only 0 or 1 [ComposeUIViewControllerState] and an arbitrary number of parameter types (excluding Composable functions) are allowed in [ComposeUIViewController] functions.
 * [More here](https://github.com/GuilhE/KMP-ComposeUIViewController?tab=readme-ov-file#kmp-module)
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ComposeUIViewControllerState
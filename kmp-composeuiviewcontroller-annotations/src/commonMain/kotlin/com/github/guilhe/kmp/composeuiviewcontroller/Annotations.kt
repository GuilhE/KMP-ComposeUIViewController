package com.github.guilhe.kmp.composeuiviewcontroller

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HidesFromObjC

/**
 * Used to annotate the `@Composable` as a desired `ComposeUIViewController` to be used by the iOS app.
 * The `@ComposeUIViewController` has a `frameworkBaseName` parameter to manually set the framework name. This parameter will only be used if detection fails within the Processor.
 * [More here](https://github.com/GuilhE/KMP-ComposeUIViewController?tab=readme-ov-file#kmp-module)
 * @param frameworkBaseName Kotlin Multiplatform library iOS targets framework base name
 * @param opaque Determines whether the Compose view should have an opaque background. Warning: disabling opaque layer may affect performance.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@OptIn(ExperimentalObjCRefinement::class)
@HidesFromObjC
public annotation class ComposeUIViewController(val frameworkBaseName: String = "", val opaque: Boolean = true)

/**
 * Used to annotate the parameter as the composable state variable (for [advanced](https://github.com/GuilhE/KMP-ComposeUIViewController?tab=readme-ov-file#advanced) use cases). Only 0 or 1 [ComposeUIViewControllerState] and an arbitrary number of parameter types (excluding `@Composable`) are allowed in [ComposeUIViewController] functions.
 * [More here](https://github.com/GuilhE/KMP-ComposeUIViewController?tab=readme-ov-file#kmp-module)
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ComposeUIViewControllerState
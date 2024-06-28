package com.github.guilhe.kmp.composeuiviewcontroller

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HidesFromObjC

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@OptIn(ExperimentalObjCRefinement::class)
@HidesFromObjC
/**
 * @param frameworkName: shared library framework's base name
 */
public annotation class ComposeUIViewController(val frameworkName: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ComposeUIViewControllerState
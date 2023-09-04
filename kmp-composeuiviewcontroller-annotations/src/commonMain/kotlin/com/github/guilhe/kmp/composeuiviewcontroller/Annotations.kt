package com.sample.sharedui.kmp.composeuiviewcontroller

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HidesFromObjC

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@OptIn(ExperimentalObjCRefinement::class)
@HidesFromObjC
public annotation class ComposeUIViewController

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
public annotation class ComposeUIViewControllerState
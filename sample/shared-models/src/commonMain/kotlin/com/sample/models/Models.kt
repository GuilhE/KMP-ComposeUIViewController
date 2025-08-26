package com.sample.models

import androidx.compose.ui.graphics.Color
import kotlin.experimental.ExperimentalObjCRefinement

data class ScreenStateExternal(val startColor: Long, val endColor: Long) {
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    val colors: List<Color> = listOf(Color(startColor), Color(endColor))
}
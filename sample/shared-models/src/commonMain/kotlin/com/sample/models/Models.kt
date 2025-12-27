package com.sample.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.experimental.ExperimentalObjCRefinement

@Stable
data class ScreenState(val startColor: Long, val endColor: Long) {
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    val colors: ImmutableList<Color> = persistentListOf(Color(startColor), Color(endColor))
}
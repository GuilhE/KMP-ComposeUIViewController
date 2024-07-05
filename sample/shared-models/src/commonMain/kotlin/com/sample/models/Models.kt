package com.sample.models

import androidx.compose.ui.graphics.Color

data class ScreenStateExternal(val startColor: Long, val endColor: Long) {
    val colors: List<Color> = listOf(Color(startColor), Color(endColor))
}
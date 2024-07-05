package com.sample.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Gradient(colors: List<Color>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors))
    ) { }
}
package com.github.guilhe.ksp.sample.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

public data class ScreenState(val colors: List<Color> = listOf(Color.Red, Color.Blue))

@ComposeUIViewController
@Composable
public fun SampleScreen(@ComposeUIViewControllerState state: ScreenState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(state.colors))
    ) { }
}
package com.github.guilhe.ksp.sample.composables

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
@Preview
public fun GradientScreen() {
    Gradient(listOf(Color.Red, Color.Blue))
}
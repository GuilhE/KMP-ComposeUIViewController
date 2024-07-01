package com.sample.shared

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
@Preview
fun GradientScreen() {
    Gradient(listOf(Color.Red, Color.Blue))
}
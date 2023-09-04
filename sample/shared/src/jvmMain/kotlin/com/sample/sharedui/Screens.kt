package com.sample.sharedui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sample.sharedui.Gradient

@Composable
@Preview
public fun GradientScreen() {
    Gradient(listOf(Color.Red, Color.Blue))
}
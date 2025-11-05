package com.sample.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf

@Composable
@Preview
fun GradientScreen() {
    Gradient(persistentListOf(Color.Red, Color.Blue))
}
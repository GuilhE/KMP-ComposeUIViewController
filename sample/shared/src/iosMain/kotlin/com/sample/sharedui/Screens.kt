package com.sample.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewControllerState

data class ScreenState(val colors: List<Color> = listOf(Color.Red, Color.Blue))

@ComposeUIViewController
@Composable
fun GradientScreen(@ComposeUIViewControllerState state: ScreenState, randomize: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Gradient(state.colors)
        Button(onClick = { randomize() }) {
            Text(text = "Randomize!")
        }
    }
}
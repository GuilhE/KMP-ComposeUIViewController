@file:Suppress("unused")
package com.sample.sharedui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewControllerState

data class ScreenState(val startColor: Long = 0xFFFF0000, val endColor: Long = 0xFF0000FF) {
    val colors: List<Color> = listOf(Color(startColor), Color(endColor))
}

@ComposeUIViewController
@Composable
fun GradientScreen(@ComposeUIViewControllerState state: ScreenState, randomize: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.colors)
        }
        Button(onClick = { randomize() }) {
            Text(text = "Shuffle")
        }
    }
}
@file:Suppress("unused")

package com.sample.shared

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewControllerState
import com.sample.models.ScreenState
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

private fun getCurrentMillis(): Long = NSDate().timeIntervalSince1970.toLong() * 1000

@ComposeUIViewController
@Composable
internal fun GradientScreenCompose(@ComposeUIViewControllerState state: ScreenState, randomize: (Long) -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.colors)
        }
        Button(onClick = { randomize(getCurrentMillis()) }) {
            Text(text = "Shuffle")
        }
    }
}

@ComposeUIViewController
@Composable
internal fun GradientScreenSwift(controller: UIViewController) {
    UIKitViewController(
        factory = { controller },
        modifier = Modifier.fillMaxSize()
    )
}

@ComposeUIViewController
@Composable
internal fun GradientScreenMixed(
    @ComposeUIViewControllerState state: ScreenState,
    controller: UIViewController,
) {
    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.colors)
        }
        UIKitViewController(
            factory = { controller },
        )
    }
}
@file:Suppress("unused")
@file:OptIn(ExperimentalComposeUiApi::class)

package com.sample.shared

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewControllerState
import com.sample.models.ScreenState
import kotlinx.collections.immutable.toImmutableList
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

/**
 * A screen rendered entirely in Compose (UI+state) and embedded in iOS.
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenCompose() {
    val availableColors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta)
    var state by remember { mutableStateOf(listOf(Color.Red, Color.Blue)) }

    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.toImmutableList())
        }
        Button(onClick = {
            val (start, end) = availableColors.shuffled().take(2)
            state = listOf(start, end)
            val formatter = NSDateFormatter().apply { dateFormat = "dd-MM-yyyy HH:mm:ss" }
            val timestamp = formatter.stringFromDate(NSDate())
            println("Shuffled at $timestamp Compose")
        }) {
            Text(text = "Shuffle")
        }
    }
}

/**
 * A screen rendered entirely in Compose with its state controlled by iOS.
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenMixedA(@ComposeUIViewControllerState state: ScreenState, randomize: (Long) -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.colors)
        }
        Button(onClick = { randomize(NSDate().timeIntervalSince1970.toLong() * 1000) }) {
            Text(text = "Shuffle")
        }
    }
}

/**
 * A screen rendered in Compose with a SwiftUI View embedded in it, and the state controlled by iOS;
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenMixedB(@ComposeUIViewControllerState state: ScreenState, controller: UIViewController) {
    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.colors)
        }
        UIKitViewController(
            factory = {
                controller.view.backgroundColor = UIColor.clearColor
                controller
            },
            properties = UIKitInteropProperties(placedAsOverlay = true)
        )
    }
}

/**
 * A screen rendered entirely in Swift (UI+state) and embedded in Compose.
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenSwift(controller: UIViewController) {
    UIKitViewController(
        factory = { controller },
        modifier = Modifier.fillMaxSize()
    )
}
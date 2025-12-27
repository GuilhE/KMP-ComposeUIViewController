@file:Suppress("unused")

package com.sample.shared

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewController
import com.github.guilhe.kmp.composeuiviewcontroller.ComposeUIViewControllerState
import com.sample.models.ScreenState
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController

/**
 * A screen rendered entirely in Compose with its state controlled by iOS.
 * The `randomize` function is called when the button is clicked, delegating to iOS the emission of a new state.
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenCompose(@ComposeUIViewControllerState state: ScreenState, randomize: (Long) -> Unit) {
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
 * A screen rendered in Compose with a Swift UIViewController embedded in it.
 * The state is controlled by the UIViewController and passed to Compose to render the gradient background.
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenMixed(@ComposeUIViewControllerState state: ScreenState, controller: UIViewController) {
    Box(contentAlignment = Alignment.Center) {
        Crossfade(targetState = state) {
            Gradient(it.colors)
        }
        UIKitViewController(factory = { controller })
    }
}

/**
 * A screen rendered entirely in Swift and embedded in Compose.
 * The UIViewController is created in Swift and manages it's state. It's then passed to Compose to be rendered.
 */
@ComposeUIViewController
@Composable
internal fun GradientScreenSwift(controller: UIViewController) {
    UIKitViewController(
        factory = { controller },
        modifier = Modifier.fillMaxSize()
    )
}
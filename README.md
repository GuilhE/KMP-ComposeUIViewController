<img alt="icon" src="/media/icon.png" width="100" align="right"></br>

# KMP-ComposeUIViewController

KSP library and Gradle plugin for generating `ComposeUIViewController` and `UIViewControllerRepresentable` files when using [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) for iOS.

| Version                                                                                                                                                                                                                     |     Kotlin     |  KSP  | Compose Multiplatform | Xcode  |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------:|:-----:|:---------------------:|:------:|
| `2.2.21-1.9.1`                                                                                                                                                                                                              |     2.2.20     | 2.3.0 |         1.9.1         | 26.0.0 |
| [![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/io.github.guilhe.kmp.plugin-composeuiviewcontroller)](https://plugins.gradle.org/plugin/io.github.guilhe.kmp.plugin-composeuiviewcontroller) | 2.3.0-Beta2-91 | 2.3.0 |    1.10.0-alpha03     | 26.0.0 |

[![Android Weekly](https://androidweekly.net/issues/issue-583/badge)](https://androidweekly.net/issues/issue-583) [![Featured in Kotlin Weekly - Issue #378](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23378-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-378) [![Featured in Kotlin Weekly - Issue #389](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23389-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-389) <a href="https://jetc.dev/issues/177.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23177-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #177"></a> <a href="https://jetc.dev/issues/188.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23188-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #188"></a>

> [!TIP]
> For Swift Export support, until the official release of Kotlin 2.3.0, use `2.3.0-dev-*`.
> Don't forget to change `embedAndSignAppleFrameworkForXcode` to `embedSwiftExportForXcode` in your `project.pbxproj`, and delete the `Derived Data` (recommended when switching between modes)

## Motivation
As the project expands, the codebase required naturally grows, which can quickly become cumbersome and susceptible to errors. To mitigate this challenge, this library leverages [Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html) to automatically generate the necessary Kotlin and Swift code for you.

It can be used for **simple** and **advanced** use cases.

### Simple
`@Composable` UI state is managed inside the common code from the KMP module.

### Advanced
`@Composable` UI state is managed by the iOS app.

Kotlin Multiplatform and Compose Multiplatform are built upon the philosophy of incremental adoption and sharing only what you require. Consequently, the support for this specific use-case - in my opinion - is of paramount importance, especially in its capacity to entice iOS developers to embrace Compose Multiplatform.

> [!NOTE]
> This library takes care of the heavy lifting for you, but if you're interested in understanding how it works, the detailed approach is explained here: [Compose Multiplatform — Managing UI State on iOS](https://proandroiddev.com/compose-multiplatform-managing-ui-state-on-ios-45d37effeda9).

## Installation

Configure the `plugins` block with the following three plugins. Once added, you can use the `ComposeUiViewController` block to set up the plugin’s configuration.

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.google.devtools.ksp")
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller") version "$LASTEST_VERSION"
}

ComposeUiViewController {
    iosAppName = "Gradient"
    targetName = "Gradient"
}
```

With this setup, all necessary configurations are automatically applied. You only need to adjust the `ComposeUiViewController` block to match your 
project settings (e.g. `iosAppName` and `targetName`). If you wish to change the default values, you can configure its parameters:

<details><summary>Parameters available</summary>

- `iosAppFolderName` name of the folder containing the iosApp in the root's project tree;
- `iosAppName` name of the iOS project (`name.xcodeproj`);
- `targetName` name of the iOS project's target;
- `exportFolderName` name of the destination folder inside iOS project (`iosAppFolderName`) where the `UIViewControllerRepresentable` files will be copied to when `autoExport` is `true`;
- `autoExport` enables auto export generated files to Xcode project. If set to `false`, you will find the generated files under `/build/generated/ksp/`;

[Default values](kmp-composeuiviewcontroller-gradle-plugin/src/main/kotlin/com/github/guilhe/kmp/composeuiviewcontroller/gradle/ComposeUiViewControllerParameters.kt).

</details>

## Code generation

### KMP module

Inside `iosMain` we can take advantage of two annotations:

`@ComposeUIViewController`:  
To annotate the `@Composable` as a desired `ComposeUIViewController` to be used by the  iOS app.

`@ComposeUIViewControllerState`:  
To annotate the parameter as the composable state variable (for **advanced** use cases).

> [!IMPORTANT]
>  Only 0 or 1 `@ComposeUIViewControllerState` and an arbitrary number of parameter types (excluding `@Composable`) are allowed in `@ComposeUIViewController` functions.

#### Examples

<details><summary>Simple</summary>

```kotlin
//iosMain

@ComposeUIViewController
@Composable
internal fun ComposeSimpleView() { }
```
will produce a `ComposeSimpleViewUIViewController`:
```kotlin
object ComposeSimpleViewUIViewController {
    fun make(): UIViewController {
        return ComposeUIViewController {
            ComposeSimpleView()
        }
    }
}
```
and also a `ComposeSimpleViewRepresentable`:
```swift
import SwiftUI
import Shared

public struct ComposeSimpleViewRepresentable: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        ComposeSimpleViewUIViewController().make()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        //unused
    }
}
```    
</details>

<details><summary>Advanced</summary>

```kotlin
//iosMain

data class ViewState(val isLoading: Boolean)

@ComposeUIViewController
@Composable
internal fun ComposeAdvancedView(@ComposeUIViewControllerState viewState: ViewState, callback: () -> Unit) { }
```
will produce a `ComposeAdvancedViewUIViewController`:
```kotlin
object ComposeAdvancedViewUIViewController {
    private val viewState = mutableStateOf<ViewState?>(null)

    fun make(callback: () -> Unit): UIViewController {
        return ComposeUIViewController {
            viewState.value?.let { ComposeAdvancedView(it, callback) }
        }
    }

    fun update(viewState: ViewState) {
        this.viewState.value = uiState
    }
}
```
and also a `ComposeAdvancedViewRepresentable`:
```swift
import SwiftUI
import Shared

public struct ComposeAdvancedViewRepresentable: UIViewControllerRepresentable {
    @Binding var viewState: ViewState
    let callback: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        ComposeAdvancedViewUIViewController().make(callback: callback)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        ComposeAdvancedViewUIViewController().update(viewState: viewState)
    }
}
```
</details>

> [!TIP]
> The `@ComposeUIViewController` has a `frameworkBaseName` parameter to manually set the framework name. This parameter will only be used <ins>if detection fails within the Processor</ins>.

### iOSApp

After a successful build the `UIViewControllerRepresentable` files are included and referenced in the `xcodeproj` ready to be used:

```swift
import SwiftUI
import Shared

struct SomeView: View {
    @State private var state: ViewState = ViewState(isLoading: false)
    var body: some View {
        VStack {
            ComposeSimpleViewRepresentable()
            ComposeAdvancedViewRepresentable(viewState: $state, callback: {})
        }
    }
}
```
> [!IMPORTANT]
> Avoid deleting `iosApp/Representables` without first using Xcode to `Remove references`.

## Sample
For a working [sample](sample) open `iosApp/Gradient.xcodeproj` in Xcode and run standard configuration or use KMP plugin for Android Studio and choose `iosApp` in run configurations.

```bash
> Task :shared:kspKotlinIosSimulatorArm64
note: [ksp] loaded provider(s): [com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider]
note: [ksp] GradientScreenUIViewController created!
note: [ksp] GradientScreenUIViewControllerRepresentable created!

> Task :CopyFilesToXcode
> Copying files to iosApp/Representables/
> Checking for new references to be added to xcodeproj
> GradientScreenUIViewControllerRepresentable.swift added!
> Done
```

<p align="center">
<img alt="outputs" src="/media/outputs.png" height="800"/></br></br>
You can also find other working samples in:</br></br>
<a href="https://github.com/GuilhE/Expressus" target="_blank"><img alt="Expressus" src="https://raw.githubusercontent.com/GuilhE/Expressus/main/media/icon.png" height="100"/></a> <a href="https://github.com/GuilhE/WhosNext" target="_blank"><img alt="WhosNext" src="https://raw.githubusercontent.com/GuilhE/WhosNext/main/media/icon.png" height="100"/></a>
</p>

## LICENSE

Copyright (c) 2023-present GuilhE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy
of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
the License.

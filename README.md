<img alt="icon" src="/media/icon.png" width="100" align="right"></br>

# KMP-ComposeUIViewController

KSP library for generating `ComposeUIViewController` and `UIViewControllerRepresentable` implementations when using [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) for iOS.

| Version                                                                                                                                                                                                                                                                                                                                                                                                                                                             |    Kotlin    |  KSP   | Compose Multiplatform |        Xcode        |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------:|:------:|:---------------------:|:-------------------:|
| [![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/io.github.guilhe.kmp.plugin-composeuiviewcontroller)](https://plugins.gradle.org/plugin/io.github.guilhe.kmp.plugin-composeuiviewcontroller) </br> [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.guilhe.kmp/kmp-composeuiviewcontroller-ksp/badge.svg)](https://search.maven.org/artifact/com.github.guilhe.kmp/kmp-composeuiviewcontroller-ksp)| 2.0.20-Beta1 | 1.0.22 |        1.6.11         | 15.3.0, 16.0.0-Beta |

The suffix `-ALPHA` or `-BETA` will be added to reflect JetBrain's [Compose Multiplatform iOS stability level](https://www.jetbrains.com/help/kotlin-multiplatform-dev/supported-platforms.html#current-platform-stability-levels-for-compose-multiplatform-ui-framework), until it becomes `STABLE`.

[![Android Weekly](https://androidweekly.net/issues/issue-583/badge)](https://androidweekly.net/issues/issue-583) [![Featured in Kotlin Weekly - Issue #378](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23378-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-378) [![Featured in Kotlin Weekly - Issue #389](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23389-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-389) <a href="https://jetc.dev/issues/177.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23177-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #177"></a> <a href="https://jetc.dev/issues/188.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23188-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #188"></a>

## Motivation
As the project expands, the codebase required naturally grows, which can quickly become cumbersome and susceptible to errors. To mitigate this challenge, this library leverages [Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html) to automatically generate the necessary Kotlin and Swift code for you.

It can be used for **simple** and **advanced** use cases.

### Simple
`@Composable` UI state is managed inside the common code from the KMP module.

### Advanced
`@Composable` UI state is managed by the iOS app.

> [!NOTE]
> This library takes care of the heavy lifting for you, but if you're interested in understanding how it works, the detailed approach is explained here: [Compose Multiplatform — Managing UI State on iOS](https://proandroiddev.com/compose-multiplatform-managing-ui-state-on-ios-45d37effeda9).

Kotlin Multiplatform and Compose Multiplatform are built upon the philosophy of incremental adoption and sharing only what you require. Consequently, the support for this specific use-case - in my opinion - is of paramount importance, especially in its capacity to entice iOS developers to embrace Compose Multiplatform.

## Setup

### Automatic

The recommended approach is to use the Gradle plugin in the shared module, where all configurations will be applied automatically. If you wish to change the default values, you can configure its parameters using the available  [extension](kmp-composeuiviewcontroller-gradle-plugin/src/main/kotlin/com/github/guilhe/kmp/composeuiviewcontroller/gradle/ComposeUiViewControllerParameters.kt).

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

### Manually

<details>
    <summary><b>Step 1 - </b>Dependencies setup</summary>

#### KMP module

First we need to import the KSP plugin:
```kotlin
plugins {
    id("com.google.devtools.ksp") version "${Kotlin}-${KSP}"
}
```
Then configure **iosMain** target to import `kmp-composeuiviewcontroller-annotations`:
```kotlin
kotlin {
    sourceSets {
        iosMain.dependencies {
            implementation("com.github.guilhe.kmp:kmp-composeuiviewcontroller-annotations:${LASTEST_VERSION}")
        }
    }
}
```
and also the `kmp-composeuiviewcontroller-ksp`:
```kotlin
listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
    val targetName = target.name.replaceFirstChar { it.uppercaseChar() }
    dependencies.add("ksp$targetName", "com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:${LASTEST_VERSION}")
}
```
Finish it by adding the following configuration in the end of the file:
- If using XCFramework:
```kotlin
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach { finalizedBy(":addFilesToXcodeproj") }
```
- If using Cocoapods:
```kotlin
tasks.matching { it.name == "syncFramework" }.configureEach { finalizedBy(":addFilesToXcodeproj") }
```
You can find a full setup example [here](https://github.com/GuilhE/KMP-ComposeUIViewController/blob/5ae770b84fc99cf29bc53bc6f4290511d8474251/sample/shared/build.gradle.kts).

</details>

<details>
    <summary><b>Step 2 - </b>Setup auto export to Xcode</summary>

#### Project root

Having all the files created by KSP, the next step is to make sure all the `UIViewControllerRepresentable` files are referenced in `xcodeproj` for the desire `target`:

1. Make sure you have [Xcodeproj](https://github.com/CocoaPods/Xcodeproj) installed;
2. Copy the [exportToXcode.sh](kmp-composeuiviewcontroller-gradle-plugin/src/main/resources/exportToXcode.sh) file to the **project's root** and run `chmod +x ./exportToXcode.sh`
3. Copy the following gradle task to the project's root `build.gradle.kts`:
```kotlin
tasks.register<Exec>("addFilesToXcodeproj") {
    workingDir(layout.projectDirectory)
    commandLine("bash", "-c", "./exportToXcode.sh")
}
```

**Warning:** if you change the default names of **shared** module, **iosApp** folder, **iosApp.xcodeproj** file and **iosApp** target, you'll have to adjust the `exportToXcode.sh` accordingly (in `# DEFAULT VALUES` section).

Occasionally, if you experience `iosApp/Representables` files not being updated after a successful build, try to run the following command manually:

`./gradlew addFilesToXcodeproj`

This could be due to gradle caches not being properly invalidated upon file updates.

If necessary, disable automatic files export to Xcode and instead include them manually, all while keeping the advantages of code generation. Simply comment the following line:
```kotlin
//...configureEach { finalizedBy(":addFilesToXcodeproj") }
```
You will find the generated files under `[module]/build/generated/ksp/`.

**Warning:** avoid deleting `iosApp/Representables` without first using Xcode to `Remove references`.

</details>

## Code generation

### KMP module

Inside `iosMain` we can take advantage of two annotations:

`@ComposeUIViewController`:  
To annotate the `@Composable` as a desired `ComposeUIViewController` to be used by the  iOS app.

> [!NOTE]
>  If you choose to opt-out of using the gradle `plugin-composeuiviewcontroller`, you will be responsible for ensuring that the `frameworkName` [parameter](https://github.com/GuilhE/KMP-ComposeUIViewController/blob/c821f0945c8a9e18da869df9d45dd5e7da1bbb83/sample/shared/src/iosMain/kotlin/com/sample/sharedui/Screens.kt#L21) in all `@ComposeUIViewController` annotations, matches the KMP module framework's [base name](https://github.com/GuilhE/KMP-ComposeUIViewController/blob/c821f0945c8a9e18da869df9d45dd5e7da1bbb83/sample/shared/build.gradle.kts#L25).


`@ComposeUIViewControllerState`:  
To annotate the parameter as the composable state variable (for **advanced** use cases).

> [!NOTE]
>  Only 0 or 1 `@ComposeUIViewControllerState` and an arbitrary number of parameter types (excluding `@Composable`) are allowed in `@ComposeUIViewController` functions.


For more information consult the [ProcessorTest.kt](kmp-composeuiviewcontroller-ksp/src/test/kotlin/composeuiviewcontroller/ProcessorTest.kt) file from `kmp-composeuiviewcontroller-ksp`.

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
import SharedUI

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
import SharedUI

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

### iOSApp

After a successful build the `UIViewControllerRepresentable` files are included and referenced in the `xcodeproj` ready to be used:

```swift
import SwiftUI
import SharedUI

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

## Sample
For a working [sample](sample/iosApp/Gradient/SharedView.swift) run `iosApp` by opening `iosApp/Gradient.xcodeproj` in Xcode and run standard configuration or use KMM plugin for Android Studio and choose `iosApp` in run configurations.

```bash
> Task :shared:kspKotlinIosSimulatorArm64
note: [ksp] loaded provider(s): [com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider]
note: [ksp] GradientScreenUIViewController created!
note: [ksp] GradientScreenRepresentable created!

> Task :CopyFilesToXcodeTask
> Copying files to iosApp/SharedRepresentables/
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

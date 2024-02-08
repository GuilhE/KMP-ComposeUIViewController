<img alt="icon" src="/media/icon.png" width="100" align="right">

# KMP-ComposeUIViewController
[![Android Weekly](https://androidweekly.net/issues/issue-583/badge)](https://androidweekly.net/issues/issue-583) [![Featured in Kotlin Weekly - Issue #378](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23378-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-378) [![Featured in Kotlin Weekly - Issue #389](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23389-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-389) <a href="https://jetc.dev/issues/188.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23188-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #188"></a>  

KSP library for generating `ComposeUIViewController` and `UIViewControllerRepresentable` implementations when using [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) for iOS.

## Motivation

When employing Compose Multiplatform for iOS, if the goal is to effectively manage the UI state within the iOS app, it's essential to adopt the approach detailed here: [Compose Multiplatform — Managing UI State on iOS](https://proandroiddev.com/compose-multiplatform-managing-ui-state-on-ios-45d37effeda9).

As the project expands, the codebase required naturally grows, which can quickly become cumbersome and susceptible to errors. To mitigate this challenge, this library leverages [Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html) to automatically generate the necessary code for you.

Kotlin Multiplatform and Compose Multiplatform are built upon the philosophy of incremental adoption and sharing only what you require. Consequently, the support for this specific use-case - in my opinion - is of paramount importance, especially in its capacity to entice iOS developers to embrace Compose Multiplatform.

## Compatibility

| Version                                                                                                                                                                                                                       | Kotlin |  KSP   | K2  | Compose Multiplatform | Xcode  |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------:|:------:|:---:|:---------------------:|:------:|
| [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.guilhe.kmp/kmp-composeuiviewcontroller-ksp/badge.svg)](https://search.maven.org/artifact/com.github.guilhe.kmp/kmp-composeuiviewcontroller-ksp) | 1.9.22 | 1.0.17 | Yes |     1.6.0-beta01      | 15.2.0 |

The suffix `-ALPHA` and `-BETA` will be added to reflect JetBrain's [Compose Multiplatform stability levels](https://www.jetbrains.com/help/kotlin-multiplatform-dev/supported-platforms.html#current-platform-stability-levels-for-compose-multiplatform-ui-framework), until it becomes `STABLE`.

It's important to note that this addresses the [current](https://github.com/JetBrains/compose-multiplatform/issues/3478) Compose Multiplatform API design. Depending on JetBrains' future implementations, this may potentially become deprecated. 

## Configurations

Steps to follow:

1. [KMP shared module](#kmp-shared-module)
2. [KMP project](#kmp-project)
3. [iOSApp](#iosapp)

### KMP shared module
#### Gradle
First we need to import the ksp plugin:
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
Finish it by adding this `task` configuration in the end of the file:
- If using XCFramework:
```kotlin
tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach { finalizedBy(":addFilesToXcodeproj") }
```
- If using Cocoapods:
```kotlin
tasks.matching { it.name == "syncFramework" }.configureEach { finalizedBy(":addFilesToXcodeproj") }
```
You can find a full setup example [here](sample/shared/build.gradle.kts).

Now we can take advantage of two annotations:
- `@ComposeUIViewController`: it will mark the `@Composable` as a desired `ComposeUIViewController` to be used by the **iosApp**;
- `@ComposeUIViewControllerState`: it will specify the composable state variable.

#### Rules and considerations
1. `@ComposeUIViewController` will always require a unique `@ComposeUIViewControllerState`;
2. `@ComposeUIViewController` has a `frameworkName` parameter that must be used to specify the shared library framework's base name;
3. `@ComposeUIViewControllerState` can only be applied once per `@Composable`;
4. The state variable of your choosing must have default values in it's initialization;
5. Only 1 `@ComposeUIViewControllerState` and * function parameters (excluding `@Composable`) are allowed in `@ComposeUIViewController` functions.

For more information consult the [ProcessorTest.kt](kmp-composeuiviewcontroller-ksp/src/test/kotlin/composeuiviewcontroller/ProcessorTest.kt) file from `kmp-composeuiviewcontroller-ksp`.

#### Code generation
```kotlin
data class ViewState(val status: String = "default")

@ComposeUIViewController("SharedUI")
@Composable
fun ComposeView(@ComposeUIViewControllerState viewState: ViewState, callback: () -> Unit) { }
```
will produce a `ComposeViewUIViewController`:
```kotlin
public object ComposeViewUIViewController {
    private val viewState = mutableStateOf(ViewState())

    public fun make(callback: () -> Unit): UIViewController {
        return ComposeUIViewController {
            ComposeView(viewState.value, callback)
        }
    }

    public fun update(viewState: ViewState) {
        this.viewState.value = uiState
    }
}
```
and also a `ComposeViewRepresentable`:
```swift
import SwiftUI
import SharedUI

public struct ComposeViewRepresentable: UIViewControllerRepresentable {
    @Binding var viewState: ScreenState
    let callback: () -> Void
    
    func makeUIViewController(context: Context) -> UIViewController {
        return ComposeViewUIViewController().make(callback: callback)
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        ComposeViewUIViewController().update(viewState: viewState)
    }
}
```

### KMP project

Having all the files created by KSP, the next step is to make sure all the `UIViewControllerRepresentable` files are referenced in `xcodeproj` for the desire `target`:

1. Make sure you have [Xcodeproj](https://github.com/CocoaPods/Xcodeproj) installed;
2. Copy the [exportToXcode.sh](./exportToXcode.sh) file to the project's root and run `chmod +x ./exportToXcode.sh`
3. Copy the following gradle task to the project's root `build.gradle.kts`:
```kotlin
tasks.register<Exec>("addFilesToXcodeproj") {
    workingDir(layout.projectDirectory)
    commandLine("bash", "-c", "./exportToXcode.sh")
}
```

**note:** if you change the default names of **shared** module, **iosApp** folder, **iosApp.xcodeproj** file and **iosApp** target, you'll have to adjust the `exportToXcode.sh` accordingly (in `# DEFAULT VALUES` section).

### iOSApp

Now that the `UIViewControllerRepresentable` files are included and referenced in the `xcodeproj`, they are ready to be used:
```swift
struct SharedView: View {
    @State private var state: ViewState = ViewState(status: "default")        
    var body: some View {
        ComposeViewRepresentable(viewState: $state, callback: {})
    }
}
```
Pretty simple right? 😊

For a working [sample](sample/iosApp/iosApp/SharedView.swift) run **iosApp** by opening `iosApp/iosApp.xcodeproj` in Xcode and run standard configuration or use KMM plugin for Android Studio and choose `iosApp` in run configurations.

## Outputs
```bash
> Task :shared:kspKotlinIosSimulatorArm64
note: [ksp] loaded provider(s): [com.github.guilhe.kmp.composeuiviewcontroller.ksp.ProcessorProvider]
note: [ksp] GradientScreenUIViewController created!
note: [ksp] GradientScreenRepresentable created!

> Task :addFilesToXcodeproj
> Copying files to iosApp/SharedRepresentables/
> Checking for new references to be added to xcodeproj
> GradientScreenUIViewControllerRepresentable.swift added!
> Done
```

<p align="center">
<img alt="outputs" src="/media/outputs.png" height="800"/></br></br>
It's an example of a happy path 🙌🏼</br></br>
You can also find another working sample in <b>Expressus App</b>:</br>
<a href="https://github.com/GuilhE/Expressus" target="_blank"><img alt="Expressus" src="https://raw.githubusercontent.com/GuilhE/Expressus/main/media/icon.png" height="100"/></a>
</p>

## Stability

| Operation              | Status |
|------------------------|:------:|
| Android Studio Run     |   🟢   |
| Xcode Run              |   🟢   |
| Xcode Preview          |   🟢   |

Occasionally, if you experience `iosApp/SharedRepresentables` files not being updated after a successful build, try to run the following command manually:

`./gradlew addFilesToXcodeproj`

This could be due to gradle caches not being properly invalidated upon file updates.  

If necessary, disable `swift` files automatically export to Xcode and instead include them manually, all while keeping the advantages of code generation. Simply comment the following line:
```kotlin
//...configureEach { finalizedBy(":addFilesToXcodeproj") }
```
You will find the generated files under `{shared-module}/build/generated/ksp/`.

**Warning:** avoid deleting `iosApp/SharedRepresentables` whithout first using Xcode to `Remove references`.

## LICENSE

Copyright (c) 2023-present GuilhE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy
of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
the License.

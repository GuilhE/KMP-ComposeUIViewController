# KMP-ComposeUIViewController

KSP library for generating `ComposeUIViewController` and `ComposeUIViewControllerRepresentable` implementations when using [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) for iOS.

## Motivation

When employing Compose Multiplatform for iOS, if the goal is to effectively manage the UI state within the iOS app, it's essential to adopt the approach detailed here:  
[Compose Multiplatform — Managing UI State on iOS](https://proandroiddev.com/compose-multiplatform-managing-ui-state-on-ios-45d37effeda9).  

As your project expands, the codebase required naturally grows, which can quickly become cumbersome and susceptible to errors. To mitigate this challenge, this library leverages [Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html) to automatically generate the necessary code for you, at least for the Compose side, for the time being.

Kotlin Multiplatform and Compose Multiplatform are built upon the philosophy of incremental adoption and sharing only what you require. Consequently, the support for this specific use-case - in my opinion - is of paramount importance, especially in its capacity to entice iOS developers to embrace Compose Multiplatform.

## Compatibility

| Version                        |   Kotlin   |    KSP     | Compose Multiplatform |
|--------------------------------|:----------:|:----------:|:---------------------:|
| 1.1.0-ALPHA                    | **1.9.10** | **1.0.13** |         alpha         |
| 1.0.0-ALPHA-1                  |   1.9.10   |   1.0.13   |         alpha         |
| 1.0.0-APLHA-1 (🤦🏽‍️ typo...) |   1.9.10   |   1.0.13   |         alpha         |

It's important to note that this addresses the [current](https://github.com/JetBrains/compose-multiplatform/issues/3478) Compose Multiplatform API design. Depending on JetBrains' future implementations, this may potentially become deprecated.

## How does it work

### KMP module
First we need to import the ksp plugin:
```kotlin
plugins {
    id("com.google.devtools.ksp") version "${Kotlin}-${KSP}"
}
```
Then configure our **iosMain** target to import `kmp-composeuiviewcontroller-annotations`:
```kotlin
kotlin {
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    sourceSets {
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("com.github.guilhe.kmp:kmp-composeuiviewcontroller-annotations:${LASTEST_VERSION}")
            }
        }
    }
}
```
and also the `kmp-composeuiviewcontroller-ksp`:
```kotlin
listOf(iosX64, iosArm64, iosSimulatorArm64).forEach { target ->
    getByName("${target.targetName}Main") {
        dependsOn(iosMain)
    }

    val kspConfigName = "ksp${target.name.replaceFirstChar { it.uppercaseChar() }}"
    dependencies.add(kspConfigName, "com.github.guilhe.kmp:kmp-composeuiviewcontroller-ksp:${LASTEST_VERISON}")
}
```
You can find a full setup example [here](sample/shared/build.gradle.kts).

**Tip:** if you would like to make the IDE aware of the generated code (not mandatory), after the previous ksp configuration, add this:
```kotlin
all {
    //https://kotlinlang.org/docs/ksp-quickstart.html#make-ide-aware-of-generated-code
    kotlin.srcDir("build/generated/ksp/${target.targetName}/${target.targetName}Main/kotlin")
}
```
Now we can take advantage of two annotations:
- `@ComposeUIViewController`: it will mark our `@Composable` as a desired `ComposeUIViewController` to be used by the **iosApp**;
- `@ComposeUIViewControllerState`: it will specify our composable state variable.

#### Considerations
- `@ComposeUIViewController` will always require a unique `@ComposeUIViewControllerState`;
- `@ComposeUIViewControllerState` can only be applied once per `@Composable`;
- The state variable of your choosing must implement default values in it's initialization;
- Only 1 `@ComposeUIViewControllerState` and * function parameters (excluding `@Composable`) are allowed in `@ComposeUIViewController` functions.

For more information consult the [ProcessorTest.kt](kmp-composeuiviewcontroller-ksp/src/test/kotlin/composeuiviewcontroller/ProcessorTest.kt) file from `kmp-composeuiviewcontroller-ksp`.

#### Outputs:
```kotlin
data class ViewState(val status: String = "default")

@ComposeUIViewController
@Composable
fun ComposeView(@ComposeUIViewControllerState uiState: ViewState, callback: () -> Unit) { }
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

    public fun update(uiState: ViewState) {
        this.viewState.value = uiState
    }
}
```
and also a `ComposeViewRepresentable`:
```swift
public struct ComposeViewRepresentable: UIViewControllerRepresentable {
    @Binding var viewState: ScreenState
    let callback: () -> Void
    
    func makeUIViewController(context: Context) -> UIViewController {
        return ScreenUIViewController().make(callback: callback)
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        ScreenUIViewController().update(viewState: viewState)
    }
}
```

### iOSApp

Final configuration step is to make the iOS project aware of the `UIViewControllerRepresentable` files. To do so we must:

1. Create a group in the root of your project, ex: iosApp/**SharedRepresentables**;
2. Create a new `Run Script Phase` on XCode for our `target` and add the following script:
```bash
cd "$SRCROOT/.."
find {module}/build/generated/ksp/ -type f -name '*.swift' -exec rsync -a --checksum {} {destination}/{folder}/ \;
```
where:
- {module}: is the name of the multiplatform module containing the composables;
- {destination}: destination path in the iOS project;
- {folder}: group created in step 1.

default:
```bash
cd "$SRCROOT/.."
find shared/build/generated/ksp/ -type f -name '*.swift' -exec rsync -a --checksum {} iosApp/iosApp/SharedRepresentables/ \;
```
3. Make XCode recognize the files by ...


Now that the `UIViewControllerRepresentable` files are included in the iOS project, we just need to use them:
```swift
struct SharedView: View {
    @State private var state: ViewState = ViewState(status: "default")        
    var body: some View {
        ComposeViewRepresentable(viewState: $state, callback: {})
    }
}
```
Pretty simple right? 😊  

For a working [sample](sample/iosApp/iosApp/SharedView.swift) run **iosApp** by opening `iosApp/iosApp.xcworkspace` in Xcode and run standard configuration or use KMM plugin for Android Studio and choose `iosApp` in run configurations.

## LICENSE

Copyright (c) 2023-present GuilhE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy
of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
the License.

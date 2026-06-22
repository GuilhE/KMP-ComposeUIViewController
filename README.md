<img alt="icon" src="/media/icon.png" width="100" align="right"></br>

# KMP-ComposeUIViewController

KSP library and Gradle plugin for generating `ComposeUIViewController` and `UIViewControllerRepresentable` files when using [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) for iOS.

### Compatibility

| [Version](https://plugins.gradle.org/plugin/io.github.guilhe.kmp.plugin-composeuiviewcontroller) | [Kotlin](https://github.com/JetBrains/kotlin/releases) | [KSP](https://github.com/Google/KSP/releases) | [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/releases) | Xcode  |
|--------------------------------------------------------------------------------------------------|:------------------------------------------------------:|:---------------------------------------------:|:------------------------------------------------------------------------------------:|:------:|
| 2.4.0-1.11.1-4                                                                                   |                         2.4.0                          |                     2.3.9                     |                                        1.11.1                                        | 26.5.0 |

[![Android Weekly](https://androidweekly.net/issues/issue-583/badge)](https://androidweekly.net/issues/issue-583) [![Featured in Kotlin Weekly - Issue #378](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23378-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-378) [![Featured in Kotlin Weekly - Issue #389](https://img.shields.io/badge/Featured_in_Kotlin_Weekly-Issue_%23389-7878b4)](https://mailchi.mp/kotlinweekly/kotlin-weekly-389) <a href="https://jetc.dev/issues/177.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23177-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #177"></a> <a href="https://jetc.dev/issues/188.html"><img src="https://img.shields.io/badge/As_Seen_In-jetc.dev_Newsletter_Issue_%23188-blue?logo=Jetpack+Compose&amp;logoColor=white" alt="As Seen In - jetc.dev Newsletter Issue #188"></a>

## Motivation
As the project expands, the codebase required naturally grows, which can quickly become cumbersome and susceptible to errors. To mitigate this challenge, this library leverages [Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html) to automatically generate the necessary Kotlin and Swift code for you.

It can be used for **simple** and **advanced** use cases.

### Simple
In simple scenarios, the rendering and state management of the `@Composable` or `UIViewController` are handled entirely within a single platform 
— either in the shared KMP module or directly in the iOS app. It's simply a matter of embedding the component in one platform or the other, with 
no cross-platform coordination required.

### Advanced
In advanced scenarios, rendering and state management are collaborative between platforms. Certain operations — such as emitting state updates or 
embedding a `@Composable` or `UIViewController` — can be delegated from one platform to the other. This enables more complex integrations, where 
state and UI responsibilities are shared or transferred as needed between the KMP module and the iOS app.

Kotlin Multiplatform and Compose Multiplatform are built upon the philosophy of incremental adoption and sharing only what you require. 
 Consequently, the support for this specific use-case - in my opinion - is of paramount importance, especially in its capacity to entice iOS developers to embrace Compose Multiplatform.

> [!NOTE]
> This library takes care of the heavy lifting for you, but if you're interested in understanding how it works, the detailed approach is explained here: [Compose Multiplatform — Managing UI State on iOS](https://proandroiddev.com/compose-multiplatform-managing-ui-state-on-ios-45d37effeda9).

## Installation

Configure the `plugins` block with the following. Once added, you can use the `ComposeUiViewController` block to set up the plugin's configuration.

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller") version "$LASTEST_VERSION"
}

ComposeUiViewController {
    iosAppName = "Gradient"
    targetName = "Gradient"
}
```

With this setup, all necessary configurations are automatically applied. You only need to adjust the `ComposeUiViewController` block to match your
project settings (e.g. `iosAppName` and `targetName`). If you wish to change the default values, check the available parameters.

<details><summary>Parameters available</summary>§

- `iosAppFolderName` name of the folder containing the iosApp in the root's project tree;
- `iosAppName` name of the iOS project (`name.xcodeproj`);
- `targetName` name of the iOS project's target;
- `exportFolderName` name of the destination folder inside iOS project (`iosAppFolderName`) where the `UIViewControllerRepresentable` files will be copied to when `autoExport` is `true`;
- `autoExport` enables auto export generated files to Xcode project. If set to `false`, you will find the generated files under `/build/generated/ksp/`;

- `experimentalSpmExport` when `true`, generates a local Swift Package instead of manipulating `xcodeproj`. Requires Swift Export to be configured. See [Experimental: SPM export](#experimental-spm-export);
- `iosDeploymentTarget` minimum iOS version for the generated `Package.swift`;
- `swiftToolsVersion` Swift tools version for the generated `Package.swift`.

[Default values](kmp-composeuiviewcontroller-gradle-plugin/src/main/kotlin/com/github/guilhe/kmp/composeuiviewcontroller/gradle/PluginParameters.kt).

</details>

<details><summary>Troubleshooting</summary>

Sometimes the files are correctly generated and copied, but Android Studio doesn't recognize them. Select the `iosApp` project folder,
right-click, and choose **"Reload from Disk"**. Once the files become visible, the build should succeed.

If Representables are not found in Xcode, run the diagnostic task to inspect the full pipeline without triggering a build:

```bash
 ./gradlew validateRepresentables
```

It checks and reports `[OK]`, `[WARN]`, or `[FAIL]` for:
1. KSP output — Swift files in `build/generated/ksp/`
2. Destination — Swift files in `{iosAppFolderName}/{exportFolderName}/`
3. Sync — KSP output and destination match
4. xcodeproj — all Representables are referenced in `project.pbxproj`

If validation fails, the most common fix is:
```bash
 ./gradlew clean --no-build-cache
```
Then rebuild it again.

</details>

<details><summary>Build output (sample)</summary>

When building the project, you should see output similar to this:
```bash
> Task :shared:copyFilesToXcode
  > Using xcodeproj gem version 1.27.0 (minimum: 1.27.0)
  > Starting smart sync process
  > KSP output: 4 Swift file(s) found
  > New file: GradientScreenSwiftUIViewControllerRepresentable.swift
  > New file: GradientScreenMixedAUIViewControllerRepresentable.swift
  > New file: GradientScreenMixedBUIViewControllerRepresentable.swift
  > New file: GradientScreenComposeUIViewControllerRepresentable.swift
  > Summary: 0 unchanged, 4 copied, 0 removed
  > Detected changes. Rebuilding Xcode references
  > Created new group "Representables"
  > Adding: GradientScreenComposeUIViewControllerRepresentable.swift
  > Adding: GradientScreenMixedAUIViewControllerRepresentable.swift
  > Adding: GradientScreenMixedBUIViewControllerRepresentable.swift
  > Adding: GradientScreenSwiftUIViewControllerRepresentable.swift
  > Summary: 4 added, 0 removed, 0 unchanged
  > Xcodeproj saved successfully
  > Done
```

</details>

### Swift Export
  
To enable Swift Export support, just follow the official [documentation](https://kotlinlang.org/docs/native-swift-export.html).

When using dependencies from other modules:
```kotlin
swiftExport {
    moduleName = "Shared"
    export(projects.otherModule) { ... }
}
```
Don't forget to import the plugin in [each module](https://github.com/GuilhE/KMP-ComposeUIViewController/blob/00b24949caea0bba1fab7cf2807f0005dd570544/sample-swift-export/shared-models/build.gradle.kts#L5). Check the [sample-swift-export](sample-swift-export).

> [!IMPORTANT]
> When switching between modes - `embedAndSignAppleFrameworkForXcode` to `embedSwiftExportForXcode` or vice versa - it's recommended to follow this 
> steps:
> 1. Delete the `Derived Data` using Xcode or DevCleaner app;
> 2. Run `./gradlew clean --no-build-cache`.

### SPM support - experimental
The SPM export mode works with both **Swift Export** and **ObjC Export** . It generates a local Swift Package with the generated `UIViewControllerRepresentable` files, replacing the `xcodeproj` gem approach. Enable it in the `ComposeUiViewController` block:

```kotlin
ComposeUiViewController {
    iosAppName = "Gradient"
    targetName = "Gradient"
    experimentalSpmExport = true
}
```

#### One-time setup

Run this task once after enabling `experimentalSpmExport`. It creates the local Swift Package stub and automatically adds the package reference to your Xcode project — no manual Xcode changes needed:

```bash
./gradlew :shared:createRepresentablesPackage
```
After running it, you should see a Swift Package Dependency in your project. The package is empty at this point, but it's ready to be populated with the generated `UIViewControllerRepresentable` files on every build.

> [!NOTE]
> This task is idempotent: safe to re-run after `./gradlew clean`.

#### Fresh install

To fully remove the generated SPM package and its Xcode project reference (e.g. to reset and start over), run:

```bash
./gradlew :shared:deleteRepresentablesPackage
```

This will remove the Package containing the Representables. Afterward, run `createRepresentablesPackage` to set it up again.

<details><summary>How it works</summary>

The xcodeproj gem approach manipulates `project.pbxproj` on every build (adding/removing file
references). SPM local packages are resolved by Xcode natively — once the `XCLocalSwiftPackageReference`
is in the project, Xcode handles everything. The xcodeproj gem is only needed once (during setup).

On every Xcode build, the plugin hooks into `embedAndSignAppleFrameworkForXcode` or `embedSwiftExportForXcode` and runs the `exportToSpm` task, which adapts automatically to the export mode in use:

**Swift Export** (`swiftExport { moduleName = "..." }`):
1. Creates a stable symlink `build/SPMBuild/SwiftInterfaces → {arch}/{config}/dd-interfaces`.
2. Updates `Package.swift` with `unsafeFlags(["-I", "SwiftInterfaces"])` pointing to pre-compiled Swift module interfaces. This avoids Xcode recompiling the KMP source package with a mismatched deployment target.

**ObjC Export** (`binaries.framework { baseName = "..." }`):
1. Creates a stable symlink `build/xcode-frameworks/current → {config}/{platform}`.
2. Updates `Package.swift` with both `swiftSettings: [.unsafeFlags(["-F", "xcode-frameworks/current"])]` and `linkerSettings: [.unsafeFlags(["-F", "xcode-frameworks/current", "-framework", "{frameworkName}"])]`. The `swiftSettings` flag lets Swift resolve `import {frameworkName}` from the pre-compiled framework; the explicit `linkerSettings` flag avoids intermittent `Undefined symbols for architecture` linker errors caused by Swift auto-linking being fragile in SPM static-library contexts.

**Both modes:**
3. Syncs the KSP-generated `.swift` files into `{exportFolderName}/Sources/{exportFolderName}/`.

Declaring the KMP module as an SPM source dependency (`.package(path: "...")`) causes Xcode to
**recompile the KMP source code** with SPM's own toolchain and a low default deployment target
(iOS 12 for swift-tools-version 5.9). This causes availability errors (`UnsafeCurrentTask` requires
iOS 13+). Using `unsafeFlags` points the compiler to **pre-compiled artifacts** instead.

Using pre-compiled build artifacts (instead of declaring the KMP module as an SPM source dependency) is what keeps `Package.swift` stable across simulator/device switches and eliminates deployment target mismatch errors.

After `./gradlew clean`, the `Package.swift` is automatically reset to a stub (no external dependencies) so Xcode can still open the project without resolution errors. Re-run `createRepresentablesPackage` to restore the full setup.

</details>

<details><summary>Troubleshooting</summary>

If Representables are not found in Xcode, run the diagnostic task to inspect the full pipeline without triggering a build:

```bash
 ./gradlew validateRepresentables
```

It checks and reports `[OK]`, `[WARN]`, or `[FAIL]` for:
1. KSP output — Swift files in `build/generated/ksp/`
2. Destination — Swift files in `{iosAppFolderName}/{exportFolderName}/`
3. Package.swift — exists at `{exportFolderName}/Package.swift`
4. Sources — Swift files in `{exportFolderName}/Sources/{exportFolderName}/`

If validation fails, the most common fix is:
```bash
 ./gradlew clean --no-build-cache
```
Then rebuild it again.

</details>

<details><summary>Build output (sample)</summary>

When building the project, you should see output similar to this:

```bash
> Task :shared:exportToSpm
  > Arch: iosSimulatorArm64, Config: Debug
  > Swift Export: interfaces linked → iosSimulatorArm64/Debug/dd-interfaces
  > Package.swift updated
  > Starting smart sync process
  > KSP output: 4 Swift file(s) found
  > New file: GradientScreenSwiftUIViewControllerRepresentable.swift
  > New file: GradientScreenMixedAUIViewControllerRepresentable.swift
  > New file: GradientScreenMixedBUIViewControllerRepresentable.swift
  > New file: GradientScreenComposeUIViewControllerRepresentable.swift
  > Summary: 0 unchanged, 4 copied, 0 removed
  > Done
```
Or, when using ObjC Export:
```bash
  > Task :shared:exportToSpm
  > Arch: iosSimulatorArm64, Config: Debug
  > ObjC Export: framework linked → Debug/iphonesimulator26.5
  ...
```

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
import Shared
import SwiftUI

public struct ComposeSimpleViewRepresentable: UIViewControllerRepresentable {
    public init() {}

    public func makeUIViewController(context _: Context) -> UIViewController {
        ComposeSimpleViewUIViewController().make()
    }

    public func updateUIViewController(_: UIViewController, context _: Context) {
        // unused
    }
}
```    
</details>

<details><summary>Advanced</summary>

```kotlin
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
import Shared
import SwiftUI

public struct ComposeAdvancedViewRepresentable: UIViewControllerRepresentable {
    @Binding public var viewState: ViewState
    public let callback: () -> Void

    public init(viewState: Binding<ViewState>, callback: @escaping () -> Void) {
        self._viewState = viewState
        self.callback = callback
    }

    public func makeUIViewController(context _: Context) -> UIViewController {
        ComposeAdvancedViewUIViewController().make(callback: callback)
    }

    public func updateUIViewController(_: UIViewController, context _: Context) {
        ComposeAdvancedViewUIViewController().update(viewState: viewState)
    }
}
```
</details>

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
> When not using SPM export mode, always delete `iosApp/Representables` through Xcode.

## Sample
For working samples check [sample-objc-export](sample-objc-export), [sample-objc-export-spm](sample-objc-export-spm), [sample-swift-export](sample-swift-export) or [sample-swift-export-spm](sample-swift-export-spm). 
Open `iosApp/Gradient.xcodeproj` in Xcode and run standard configuration or use KMP plugin for Android Studio and choose `Gradient` in run 
configurations.

You'll find different use cases:
- `GradientScreenCompose`: A screen rendered entirely in Compose (UI+state) and embedded in iOS;
- `GradientScreenMixedA`: A screen rendered entirely in Compose with its state controlled by iOS;
- `GradientScreenMixedB`: A screen rendered in Compose with a SwiftUI View embedded in it, and the state controlled by iOS;
- `GradientScreenSwift`: A screen rendered entirely in Swift (UI+state) and embedded in Compose.

You can also find other working samples in:  

<a href="https://github.com/GuilhE/Expressus" target="_blank"><img alt="Expressus" src="https://raw.githubusercontent.com/GuilhE/Expressus/main/media/icon.png" height="100"/></a> <a href="https://github.com/GuilhE/WhosNext" target="_blank"><img alt="WhosNext" src="https://raw.githubusercontent.com/GuilhE/WhosNext/main/media/icon.png" height="100"/></a>

## LICENSE

Copyright (c) 2023-present GuilhE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy
of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under
the License.

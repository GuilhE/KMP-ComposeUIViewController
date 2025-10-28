# Changelog

## [2.3.0-Beta2-1.10.0-alpha03]

- Kotlin 2.3.0-Beta2

---

## [2.3.0-Beta2-91-1.10.0-alpha03]

- Kotlin 2.3.0-Beta2-91
- CMP 1.10.0-alpha03
- KSP 2.3.0

---

## [2.2.21-1.9.1]

- Kotlin 2.0.21
- CMP 1.9.1
- KSP 2.3.0

---

## [2.3.0-Beta1-1.9.0]

- Kotlin 2.3.0-Beta1

---

## [2.2.20-1.9.0]

- CMP 1.9.0

---

## [2.3.0-dev-5897-1.9.0]

- CMP 1.9.0

---

## [2.2.20-1.9.0-rc01]

- Kotlin 2.2.20
- SerializationPlugin 2.2.20

---

## [2.3.0-dev-5897-1.9.0-rc01]

- Kotlin 2.3.0-dev-5897
- CMP 1.9.0-rc01

---

## [2.2.20-RC2-1.9.0-rc01]

- Kotlin 2.2.20-RC2
- CMP 1.9.0-rc01

---

## [2.3.0-dev-4778-1.9.0-beta03-1]

Fixes bug in flattenConfigured logic

---

## [2.3.0-dev-4778-1.9.0-beta03]

- Kotlin 2.3.0-dev-4778

__Note:__ This development version is required for Swift Export support until the official release of Kotlin 2.3.0.

---

## [2.2.20-RC-1.9.0-beta03-1]

- Kotlin 2.2.20-RC
- Adds experimental support to Swift Export

**Note:** Kotlin 2.2.20-RC has the following issues with Swift Export: [KT-80347](https://youtrack.jetbrains.com/issue/KT-80347/Swift-Export-IllegalArgumentException-Collection-contains-more-than-one-matching-element), [KT-79889](https://youtrack.jetbrains.com/issue/KT-79889/K-N-swift-export-fails-under-several-different-conditions).  
Use `2.3.0-dev-4778-1.9.0-beta03` if you need Swift Export support.

---

## [2.2.10-1.9.0-beta03]

- Kotlin 2.2.10
- CMP 1.9.0-beta03

---

## [2.2.0-1.8.2]

- Kotlin 2.2.0
- KSP 2.0.2
- CMP 1.8.2

---

## [2.1.21-1.8.1]

- CMP 1.8.1

---

## [2.1.21-1.8.0]

- CMP 1.8.0 is [stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) for iOS 🎉
- Kotlin 2.1.21
- KSP 2.0.1
- Kotlin 2.1.21
- Serialization 1.8.1
- SerializationPlugin 2.1.21

---

## [2.1.20-1.8.0-beta02-BETA]

- KSP 2.0.0
- CMP 1.8.0-beta02

---

## [2.1.20-1.8.0-beta01-BETA]

- Kotlin 2.1.20
- CMP 1.8.0-beta01

---

## [2.1.10-1.8.0-alpha04-BETA]

- KSP 1.0.31
- CMP 1.8.0-alpha04

---

## [2.1.10-1.8.0-alpha03-BETA]

- Kotlin 2.1.10
- KSP 1.0.30
- CMP 1.8.0-alpha03

---

## [2.1.0-1.8.0-alpha02-BETA]

- CMP 1.8.0-alpha02
- Serialization 1.8.0

---

## [2.1.0-1.7.2-BETA]

- CMP 1.7.2

---

## [2.1.0-1.7.1-BETA]

- Kotlin 2.1.0
- KSP 1.0.29
- CMP 1.7.1
- Removes junit dependencies
- Migrates to [ZacSweers/kotlin-compile-testing](https://github.com/ZacSweers/kotlin-compile-testing)

---

## [2.0.21-1.7.0-BETA-1]

- Fixes type resolve for Functions when `ksp.useKSP2=true`
- Adds generics to List, Map, MutableList and MutableMap

---

## [2.0.21-1.7.0-BETA]

- CMP 1.7.0

---

## [2.0.21-1.7.0-rc01-BETA]

- Kotlin 2.0.21
- KSP 1.0.25

---

## [2.0.20-1.7.0-rc01-BETA-1]

- CMP 1.7.0-rc01

## [2.0.20-1.7.0-beta01-BETA-2]

- Adds exception print to ModuleDecodeException
- Fixes `getFrameworkMetadataFromDisk()` throwing not found for cocoapods projects

---

## [2.0.20-1.7.0-beta01-BETA-1]

- CMP 1.7.0-beta01
- KSP 1.0.25

---

## [2.0.20-1.7.0-alpha03-BETA-1]

- Kotlin 2.0.20
- KSP 1.0.24

## [2.0.20-Beta1-1.6.11-BETA-7]

- Adds `experimentalNamespaceFeature` flag to enable/disable experimental feature to import types from external modules. Read more in [CHANGELOG.md#2020-beta1-1611-beta-4](CHANGELOG.md#2020-beta1-1611-beta-4)

---

## [2.0.20-Beta1-1.6.11-BETA-6]

- Fixes EmptyFrameworkBaseNameException bug (incorrectly using `contains` instead of `startsWith`)

---

## [2.0.20-Beta1-1.6.11-BETA-5]

- Fixes EmptyFrameworkBaseNameException bug

---

## [2.0.20-Beta1-1.6.11-BETA-4]

- Adds experimental feature to import types from external modules.

> [!CAUTION]
> Theres an [actual limitation](https://kotlinlang.slack.com/archives/C3SGXARS6/p1719961104891399) on Kotlin Multiplatform where each binary framework is compiled as a "closed world“, meaning it's not possible to pass custom type between two frameworks even it’s the same in Kotlin.
>
> Let’s say I have two modules, `shared` and `shared-models`, each providing their own binary frameworks: “Shared” and “SharedModels” respectively. The `shared-models` contains a `data class Hello`, and the `shared` module `implements(project(":shared-models"))` and has a public method that takes `Hello` as a parameter.
>
> When these modules are exported to Swift, we see the following:
>
> SharedModels: `public class Hello : KotlinBase`  
> Shared: `public class Shared_modelsHello : KotlinBase`  
> Shared: `open func update(state: Shared_modelsHello)`
>
> Instead of:
>
> SharedModels: `public class Hello : KotlinBase`  
> Shared: `open func update(state: Hello)`
>
> It means that the "Shared" framework will include all this external dependencies (from the "SharedModel" in this case) and will generate new types to reference those external types. That's why we endup having `Shared_modelsHello` instead of just `Hello`.
>
> A workaround to "solve" this limitation is to use the `export()` function (inside `binaries.framework` configuration) to add a dependency to be exported in the framework.
> ```
> iosTarget.binaries.framework {
>    baseName = "Shared"
>    export(projects.sharedModels)
> }
> ```
> When using this approach this experimental feature should be disabled.

Modules that provide external dependencies must include the plugin in their `build.gradle`:

```
plugins {
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

ComposeUiViewController {
    autoExport = false
}
```

---

## [2.0.20-Beta1-1.6.11-BETA-3]

- Adds all KMP targets available to `kmp-composeuiviewcontroller-annotations`

---

## [2.0.20-Beta1-1.6.11-BETA-2]

- Compatible with Plugin 1.1.0

---

## [2.0.20-Beta1-1.6.11-BETA-1]

- Prepares Processor for Plugin 1.1.0 where it will be capable of checking for compilerArgs

---

## [2.0.20-Beta1-1.6.11-BETA]

- Kotlin 2.0.20-Beta1
- KSP 1.0.22

---

## [2.0.0-1.6.11-BETA-1]

- Improves `exportToXcode.sh`
- Renames iosApp sample to Gradient

---

## [2.0.0-1.6.11]

- Compose Multiplatform 1.6.11

---

## [2.0.0-1.6.10-BETA-4]

Reverts `@ComposeUIViewController` default parameter to `SharedComposables`

---

## [2.0.0-1.6.10-BETA-3]

`@ComposeUIViewController` uses `ComposeApp` as default parameter to match [Kotlin Multiplatform Wizard](https://kmp.jetbrains.com/)

---

## [2.0.0-1.6.10-BETA-2]

Removes the necessity for default values of `@ComposeUIViewControllerState`

---

## [2.0.0-1.6.10-BETA-1]

Allows `@ComposeUIViewController` without `@ComposeUIViewControllerState` for simples wrapper cases.

---

## [2.0.0-1.6.10-BETA]

- Gradle wrapper 8.6

From now on `KMP-ComposeUIViewController` version will map `Kotlin` and `Compose Multiplatform` versions

---

## [1.8.0-ALPHA]

- Kotlin 2.0.0
- KSP 1.0.21

---

## [1.8.0-ALPHA]

- Kotlin 2.0.0-RC3
- Compose 1.6.10

---

## [1.7.0-ALPHA]

- Kotlin 2.0.0-RC2

---
## [1.6.0-ALPHA]

- Kotlin 2.0.0-RC1
- KSP 1.0.20

---

## [1.5.0-ALPHA]

- Kotlin 1.9.23
- KSP 1.0.19

---

## [1.4.4-ALPHA]

- Adds `indentParameters` and `removeAdjacentEmptyLines` for better code indentation in `.swift` files.

---

## [1.4.3-ALPHA]

- Fixes Processor bug in `getFrameworkNameFromAnnotations()` function that was accepting empty values

---

## [1.4.2-ALPHA]

- Fixes Processor bug where functions that should be discarded from the analysis were not
- Sample updated with a use-case with a function parameter with a Kotlin primitive type

---

## [1.4.1-ALPHA]

Adds `kotlinTypeToSwift` function to reflect https://kotlinlang.org/docs/apple-framework.html#generated-framework-headers

---

## [1.4.0-ALPHA]

- Kotlin 1.9.22
- KSP 1.0.17

---

## [1.3.0-ALPHA]

- Kotlin 1.9.21
- KSP 1.0.15

---

## [1.2.0-ALPHA]

- Kotlin 1.9.20
- KSP 1.0.13

---

## [1.1.0-ALPHA]

- Adds capability to generate `.swift` files with `UIViewControllerRepresentables`.
- Adds script to include those generated files into `xcodeproj` to be accessible in iOS project;

---

## [1.0.0-ALPHA-1]

Hello world!
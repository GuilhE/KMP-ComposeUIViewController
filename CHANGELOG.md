# Changelog
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

# Changelog
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

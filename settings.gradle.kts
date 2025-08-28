@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/dev")
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/dev")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ComposeUIViewController"
include(":kmp-composeuiviewcontroller-ksp")
include(":kmp-composeuiviewcontroller-annotations")
include(":kmp-composeuiviewcontroller-gradle-plugin")
include(":kmp-composeuiviewcontroller-common")
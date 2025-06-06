@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ComposeUIViewController"
include(":kmp-composeuiviewcontroller-ksp")
include(":kmp-composeuiviewcontroller-annotations")
include(":kmp-composeuiviewcontroller-gradle-plugin")
include(":kmp-composeuiviewcontroller-common")
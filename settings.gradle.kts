@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "ComposeUIViewController"
include(":kmp-composeuiviewcontroller-ksp")
include(":kmp-composeuiviewcontroller-annotations")
include(":kmp-composeuiviewcontroller-gradle-plugin")
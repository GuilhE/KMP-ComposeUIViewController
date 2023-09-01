@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("..")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

includeBuild("..") {
    dependencySubstitution {
        listOf("annotations", "compiler", "compiler-embeddable", "core", "ksp").forEach { _ ->
            substitute(module("com.github.guilhe:compose-uiviewcontroller-ksp"))
                .using(project(":compose-uiviewcontroller-ksp"))
        }
    }
}

rootProject.name = "sample"
include(":shared")
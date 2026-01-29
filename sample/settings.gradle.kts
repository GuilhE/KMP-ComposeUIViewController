@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("..")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/dev")
    }
    versionCatalogs {
        create("global") { from(files("../gradle/libs.versions.toml")) }
        create("local") { from(files("./gradle/libs.versions.toml")) }
    }
}

includeBuild("..") {
    dependencySubstitution {
        listOf("annotations", "ksp").forEach {
            substitute(module("com.github.guilhe.kmp:kmp-composeuiviewcontroller-$it"))
                .using(project(":kmp-composeuiviewcontroller-$it"))
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Gradient"
include(":shared")
include(":shared-models")
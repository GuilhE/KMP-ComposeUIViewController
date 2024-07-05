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
        listOf("annotations", "ksp").forEach {
            substitute(module("com.github.guilhe.kmp:kmp-composeuiviewcontroller-$it"))
                .using(project(":kmp-composeuiviewcontroller-$it"))
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "sample"
include(":shared")
include(":shared-models")
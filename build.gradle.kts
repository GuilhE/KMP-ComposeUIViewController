plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gradle.publish) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    group = "com.github.guilhe.kmp"
    version = "2.0.20-Beta1-1.6.11-BETA-4"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishAllPublicationsToSonatypeRepository")

    tasks.named(":kmp-composeuiviewcontroller-annotations:publishAllPublicationsToSonatypeRepository").configure {
        finalizedBy(":kmp-composeuiviewcontroller-common:publishAllPublicationsToSonatypeRepository")
    }

    tasks.named(":kmp-composeuiviewcontroller-common:publishAllPublicationsToSonatypeRepository").configure {
        finalizedBy(":kmp-composeuiviewcontroller-ksp:publishAllPublicationsToSonatypeRepository")
    }
}
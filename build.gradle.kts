plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gradle.publish) apply false
    alias(libs.plugins.kotlin.dokka)
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    group = "com.github.guilhe.kmp"
    version = "2.1.0-1.8.0-alpha02-BETA"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-common:publishAllPublicationsToSonatypeRepository")
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishAllPublicationsToSonatypeRepository")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishAllPublicationsToSonatypeRepository")
}
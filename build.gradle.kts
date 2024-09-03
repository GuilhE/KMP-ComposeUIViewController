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
    version = "2.0.20-1.7.0-alpha03-BETA-1"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-common:publishAllPublicationsToSonatypeRepository")
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishAllPublicationsToSonatypeRepository")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishAllPublicationsToSonatypeRepository")
}
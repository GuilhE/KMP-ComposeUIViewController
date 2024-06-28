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
    version = "2.0.20-Beta1-1.6.11-BETA-1"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishAllPublicationsToSonatypeRepository")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishAllPublicationsToSonatypeRepository")
}

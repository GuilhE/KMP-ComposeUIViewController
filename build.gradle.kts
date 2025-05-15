plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gradle.publish) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.vanniktech.maven) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    group = "com.github.guilhe.kmp"
    version = "2.1.21-1.8.0"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-common:publishToMavenCentral")
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishToMavenCentral")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishToMavenCentral")
}
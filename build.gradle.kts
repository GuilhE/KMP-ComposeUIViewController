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
    version = "2.1.21-1.8.0"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-common:publishAndReleaseToMavenCentral")
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishAndReleaseToMavenCentral")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishAndReleaseToMavenCentral")
}
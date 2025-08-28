plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gradle.publish) apply false
    alias(libs.plugins.vanniktech.maven) apply false //https://github.com/vanniktech/gradle-maven-publish-plugin/issues/670#issuecomment-1839097676
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
    version = "2.2.20-RC-1.9.0-beta03"
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-common:publishToMavenCentral")
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishToMavenCentral")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishToMavenCentral")
}
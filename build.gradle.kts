plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    group = "com.github.guilhe.ksp"
    version = "1.0.0"
}
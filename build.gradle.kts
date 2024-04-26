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
    group = "com.github.guilhe.kmp"
    version = "1.6.0-ALPHA"
}

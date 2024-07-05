plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}

dependencies {
    implementation(libs.kotlinx.serialization)
}
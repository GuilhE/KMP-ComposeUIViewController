plugins {
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.compose)
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(compose.ui)
        }
    }
}
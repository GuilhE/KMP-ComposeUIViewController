plugins {
    alias(global.plugins.kotlin.multiplatform)
    alias(local.plugins.compose.compiler)
    alias(local.plugins.compose.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(local.kotlinx.collections)
            implementation(local.jetbrains.compose.ui)
        }
    }
}
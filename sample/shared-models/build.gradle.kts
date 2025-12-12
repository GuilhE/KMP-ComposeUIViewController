plugins {
    alias(global.plugins.kotlin.multiplatform)
    alias(local.plugins.compose.compiler)
    alias(local.plugins.compose.multiplatform)
}

kotlin {
//    iosArm64()
//    iosSimulatorArm64()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target -> target.binaries.framework { baseName = "Models" } }
    sourceSets {
        commonMain.dependencies {
            implementation(local.kotlinx.collections)
            implementation(local.jetbrains.compose.ui)
        }
    }
}
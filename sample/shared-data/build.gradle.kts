plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "SharedData" }
    }
}
plugins {
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.compose)
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

ComposeUiViewController {
    autoExport = false
}

kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "Models" }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.ui)
        }
    }
}
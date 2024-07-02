plugins {
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.compose)
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

ComposeUiViewController {
    iosAppName = "Gradient"
    targetName = "Gradient"
}

kotlin {
    jvm()
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "Composables" }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        jvmMain.dependencies { implementation(compose.preview) }
        iosMain.dependencies { implementation(project(":shared-models")) }
    }
}
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

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
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalSwiftExportDsl::class)
    swiftExport {
        moduleName = "Composables"
        flattenPackage = "com.sample.shared"
        export(projects.sharedModels) {
            moduleName = "Models"
            flattenPackage = "com.sample.models"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        jvmMain.dependencies { implementation(compose.preview) }
        iosMain.dependencies { api(projects.sharedModels) }
    }
}
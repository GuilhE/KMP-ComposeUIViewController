import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
    alias(global.plugins.kotlin.multiplatform)
    alias(local.plugins.compose.compiler)
    alias(local.plugins.compose.multiplatform)
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

ComposeUiViewController {
    iosAppName = "Gradient"
    targetName = "Gradient"
}

kotlin {
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
            api(projects.sharedModels)
            implementation(local.kotlinx.collections)
            implementation(local.bundles.jetbrains.compose)
        }
    }
}
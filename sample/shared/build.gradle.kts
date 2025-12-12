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
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Composables"
            export(projects.sharedModels)
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
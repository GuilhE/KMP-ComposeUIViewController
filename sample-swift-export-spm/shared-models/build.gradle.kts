@file:OptIn(ExperimentalSwiftExportDsl::class)

import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
    alias(global.plugins.kotlin.multiplatform)
    alias(local.plugins.compose.compiler)
    alias(local.plugins.compose.multiplatform)
    id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

kotlin {
	iosArm64()
	iosSimulatorArm64()
	export {
		swift {
			moduleName = "Models"
			rootPackage = "com.sample.models"
		}
	}
    sourceSets {
        commonMain.dependencies {
            implementation(local.kotlinx.collections)
            implementation(local.jetbrains.compose.ui)
        }
    }
}
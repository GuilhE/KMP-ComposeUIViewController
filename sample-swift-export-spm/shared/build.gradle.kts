@file:OptIn(ExperimentalSwiftExportDsl::class)

import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
	alias(global.plugins.kotlin.multiplatform)
	alias(local.plugins.compose.compiler)
	alias(local.plugins.compose.multiplatform)
	id("io.github.guilhe.kmp.plugin-composeuiviewcontroller")
}

ComposeUiViewController {
	targetName = "Gradient"
	iosAppName = "Gradient"
}

kotlin {
	iosArm64()
	iosSimulatorArm64()
	export {
		swift {
			moduleName = "Composables"
			rootPackage = "com.sample.shared"
			swiftPackageIntegration()
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
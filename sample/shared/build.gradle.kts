plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.6.1"
}

compose {
    kotlinCompilerPlugin.set("1.5.10.1")
//    kotlinCompilerPluginArgs.add("suppressKotlinVersionCompatibilityCheck=1.9.23")
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        jvmMain.dependencies { implementation(compose.preview) }
        iosMain.dependencies { implementation(libs.composeuiviewcontroller.annotations) }

        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework { baseName = "SharedComposables" }
            dependencies.add("ksp${target.name.replaceFirstChar { it.uppercaseChar() }}", libs.composeuiviewcontroller.ksp)
        }
    }
}

tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach { finalizedBy(":addFilesToXcodeproj") }
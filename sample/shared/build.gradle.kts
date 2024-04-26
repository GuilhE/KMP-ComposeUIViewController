plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.6.10-beta02"
}

compose {
    kotlinCompilerPlugin.set("1.5.11-kt-2.0.0-RC1")
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
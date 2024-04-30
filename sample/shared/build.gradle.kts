plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.google.ksp)
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
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("com.android.library")
    id("org.jetbrains.compose") version "1.5.0"
}

version = "1.0"

android {
    namespace = "com.github.guilhe.ksp.sample"
    compileSdk = 34
}

compose {
    kotlinCompilerPlugin.set("1.5.2-beta01")
}

kotlin {
    jvmToolchain(11)
    explicitApi()

    androidTarget()
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
        }

        val composeSource by creating {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
            }
        }

        val commonMain by getting {
            dependsOn(composeSource)
            dependencies {
//                configurations["ksp"].dependencies.add(implementation("com.github.guilhe:kmp-composeuiviewcontroller-ksp:1.0.0"))
            }
        }

        val iosMain by creating {
            dependsOn(composeSource)
            dependencies {
                implementation("com.github.guilhe:kmp-composeuiviewcontroller-annotations:1.0.0")
            }
        }
        listOf(iosX64, iosArm64, iosSimulatorArm64).forEach {
            it.binaries.framework {
                baseName = "UIViewControllerSampleShared"
            }
            getByName("${it.targetName}Main") { dependsOn(iosMain) }
        }
    }
}
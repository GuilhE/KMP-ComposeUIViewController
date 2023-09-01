plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.5.0"
}

version = "1.0"

compose {
    kotlinCompilerPlugin.set("1.5.2-beta01")
}

kotlin {
    jvmToolchain(11)
    explicitApi()

    jvm()

//    val iosX64 = iosX64()
//    val iosArm64 = iosArm64()
//    val iosSimulatorArm64 = iosSimulatorArm64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
        }
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)

                configurations["ksp"].dependencies.add(implementation("com.github.guilhe:compose-uiviewcontroller-ksp:1.0.0"))
                implementation("com.github.guilhe:compose-uiviewcontroller-ksp:1.0.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.preview)
            }
        }

//        val iosMain by creating {
//            dependsOn(commonMain)
//        }
//        listOf(iosX64, iosArm64, iosSimulatorArm64).forEach {
//            it.binaries.framework {
//                baseName = "UIViewControllerSampleShared"
//            }
//            getByName("${it.targetName}Main") { dependsOn(iosMain) }
//        }
    }
}
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

    val iosArm64 = iosArm64()
    val iosX64 = iosX64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
        }
        val commonMain by getting {
            dependencies {
//                configurations["ksp"].dependencies.add(implementation("com.github.guilhe:compose-uiviewcontroller-ksp:1.0.0"))
//                implementation("com.github.guilhe:compose-uiviewcontroller-ksp:1.0.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.test.kotlin)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        listOf(iosArm64, iosX64, iosSimulatorArm64).forEach {
            it.binaries.framework {
                baseName = "UIViewControllerSampleShared"
            }
            getByName("${it.targetName}Main") { dependsOn(iosMain) }
            getByName("${it.targetName}Test") { dependsOn(iosTest) }
        }
    }
}
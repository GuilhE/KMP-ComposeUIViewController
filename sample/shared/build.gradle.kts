plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.5.0"
}

compose {
    kotlinCompilerPlugin.set("1.5.2-beta01")
}

kotlin {
    jvmToolchain(11)
    explicitApi()

    jvm()
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

        val commonMain by getting {
            dependsOn(composeSource)
        }

        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.preview)
            }
        }

        val iosMain by creating {
            dependsOn(composeSource)
            dependsOn(commonMain)
            dependencies {
                implementation("com.github.guilhe:kmp-composeuiviewcontroller-annotations:1.0.0")
            }
        }
        listOf(iosX64, iosArm64, iosSimulatorArm64).forEach { target ->
            target.binaries.framework { baseName = "SharedComposables" }
            getByName("${target.targetName}Main") { dependsOn(iosMain) }

            val kspConfigName = "ksp${target.name.replaceFirstChar { it.uppercaseChar() }}"
            dependencies.add(kspConfigName, "com.github.guilhe:kmp-composeuiviewcontroller-ksp:1.0.0")
        }
    }
}
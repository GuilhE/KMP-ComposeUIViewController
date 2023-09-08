import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.5.0"
}

compose {
    kotlinCompilerPlugin.set("1.5.2-beta01")
}

kotlin {
    jvm()
    val iosX64 = iosX64()
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.preview)
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.composeuiviewcontroller.annotations)
            }
        }
        listOf(iosX64, iosArm64, iosSimulatorArm64).forEach { target ->
            target.binaries.framework {
                baseName = "SharedComposables"
            }
            getByName("${target.targetName}Main") {
                dependsOn(iosMain)
            }

            val kspConfigName = "ksp${target.name.replaceFirstChar { it.uppercaseChar() }}"
            dependencies.add(kspConfigName, libs.composeuiviewcontroller.ksp)
            all {
                //https://kotlinlang.org/docs/ksp-quickstart.html#make-ide-aware-of-generated-code
                kotlin.srcDir("build/generated/ksp/${target.targetName}/${target.targetName}Main/kotlin")
            }
        }
    }
}
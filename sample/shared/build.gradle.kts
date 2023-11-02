plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    id("org.jetbrains.compose") version "1.5.10"
}

compose {
    kotlinCompilerPlugin.set("1.5.3")
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
            val targetName = target.name.replaceFirstChar { it.uppercaseChar() }
            dependencies.add("ksp$targetName", libs.composeuiviewcontroller.ksp)

//            all {
//                //https://kotlinlang.org/docs/ksp-quickstart.html#make-ide-aware-of-generated-code
//                kotlin.srcDir("build/generated/ksp/${target.targetName}/${target.targetName}Main/kotlin")
//            }
        }
    }
}

tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach { finalizedBy(":addFilesToXcodeproj") }
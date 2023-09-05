plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `kmp-composeuiviewcontroller-publish`
}

kotlin {
    explicitApi()
    jvmToolchain(11)

    jvm()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
}
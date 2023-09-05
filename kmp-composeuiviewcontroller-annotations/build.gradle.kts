plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `kmp-composeuiviewcontroller-publish`
}

kotlin {
    explicitApi()
    jvmToolchain(11)

    iosArm64()
    iosX64()
    iosSimulatorArm64()
    jvm()
}
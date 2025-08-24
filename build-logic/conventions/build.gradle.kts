plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.gradle.vanniktech)
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        register("KmpComposeUIViewControllerPublishPlugin") {
            id = "kmp-composeuiviewcontroller-publish"
            implementationClass = "KmpComposeUIViewControllerPublishPlugin"
        }
    }
}

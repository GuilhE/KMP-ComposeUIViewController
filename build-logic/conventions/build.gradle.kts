plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.32.0")
}

kotlin {
    jvmToolchain(11)
}

gradlePlugin {
    plugins {
        register("KmpComposeUIViewControllerPublishPlugin") {
            id = "kmp-composeuiviewcontroller-publish"
            implementationClass = "KmpComposeUIViewControllerPublishPlugin"
        }
    }
}

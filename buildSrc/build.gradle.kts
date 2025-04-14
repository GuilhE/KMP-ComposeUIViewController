plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.31.0")
}
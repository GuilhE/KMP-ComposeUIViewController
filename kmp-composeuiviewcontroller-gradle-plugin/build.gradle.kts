@file:Suppress("UnstableApiUsage")

plugins {
    `java-gradle-plugin`
    `kmp-composeuiviewcontroller-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.ksp)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.junit.implementation)
    testRuntimeOnly(libs.test.junit.runtimeOnly)
}

tasks.test {
    useJUnitPlatform()
}

version = "1.1.1"
group = "io.github.guilhe.kmp"

gradlePlugin {
    website = "https://github.com/GuilhE/KMP-ComposeUIViewController"
    vcsUrl = "https://github.com/GuilhE/KMP-ComposeUIViewController.git"
    plugins {
        create("kmpComposeUIViewController") {
            id = "$group.plugin-composeuiviewcontroller"
            implementationClass = "com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPlugin"
            displayName = "KMP-ComposeUIViewController"
            description =
                "Automates configuration for the KMP-ComposeUIViewController KSP library by introducing a Gradle extension for streamlined setup and usage."
            tags = listOf(
                "kotlin",
                "swift",
                "swiftui",
                "kotlinmultiplatform",
                "composemultiplatform",
                "composeuiviewcontroller",
                "uiviewcontroller",
                "uiviewcontrollerrepresentable"
            )
        }
    }
}

sourceSets {
    main {
        resources.srcDir("src/main/resources")
    }
}

tasks.withType(Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("sourcesJar", Jar::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
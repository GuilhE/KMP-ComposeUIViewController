@file:Suppress("UnstableApiUsage")

plugins {
    `java-gradle-plugin`
    id("kmp-composeuiviewcontroller-publish")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.publish)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlin.dokka)
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
    implementation(libs.gradle.ksp)
    implementation(libs.gradle.kotlin)
    implementation(libs.kotlinx.serialization)
    implementation(projects.kmpComposeuiviewcontrollerCommon)

    testImplementation(libs.test.kotlin)
}

version = "1.2.9"
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
        create("kmpComposeUIViewControllerPublish") {
            id = "$group.plugin-composeuiviewcontroller-publish"
            implementationClass = "com.github.guilhe.kmp.composeuiviewcontroller.gradle.KmpComposeUIViewControllerPublishPlugin"
            displayName = "KMP-ComposeUIViewController Publish"
            description = "Configura publicação Maven Central para KMP-ComposeUIViewController."
            tags = listOf("maven", "publish", "central", "kmp", "composeuiviewcontroller")
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
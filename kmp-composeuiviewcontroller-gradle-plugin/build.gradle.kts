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

version = "1.0.0"
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

/**
 * The following configurations address the need for a unique source of truth regarding the library version within this module.
 * Despite the version being initially defined in the root project's `build.gradle.kts` under the `allprojects` extension, direct access from this module is not feasible.
 * To resolve this limitation, the code introduces a Gradle task (`copyVersionTemplate`) responsible for copying a `Version.kt` file from the project directory.
 * It incorporates variable substitution to inject the specified `version` property into the file, storing it in a designated directory (`generated/kmp-composeuiviewcontroller-version/main`).
 * By integrating this task into Kotlin compilation and JAR creation (`sourcesJar`), the approach ensures seamless inclusion of the versioned file in the main source set (`sourceSets`).
 *
 * It also provides `sourceSets` configurations for `resources` to make it possible to access and copy `exportToXcode.sh` file.
 */

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/kmp-composeuiviewcontroller-version/main"))
        resources.srcDir("src/main/resources")
    }
}

private val copyVersionTemplate by tasks.registering(Copy::class) {
    inputs.property("version", version)
    from(layout.projectDirectory.file("Version.kt"))
    into(layout.buildDirectory.dir("generated/kmp-composeuiviewcontroller-version/main"))
    expand("version" to "$version")
    filteringCharset = "UTF-8"
}

tasks.withType(Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("sourcesJar", Jar::class) {
    dependsOn(copyVersionTemplate)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.compileKotlin {
    dependsOn(copyVersionTemplate)
}
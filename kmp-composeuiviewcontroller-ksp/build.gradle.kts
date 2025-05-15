plugins {
    id("kmp-composeuiviewcontroller-publish")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinx.serialization)
    implementation(projects.kmpComposeuiviewcontrollerCommon)

    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.compile.core)
    testImplementation(libs.test.compile.ksp)
    testImplementation(projects.kmpComposeuiviewcontrollerAnnotations)
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.compileKotlin.configure {
    compilerOptions {
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}
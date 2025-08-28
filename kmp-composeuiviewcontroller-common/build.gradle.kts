plugins {
    id("kmp-composeuiviewcontroller-publish")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

tasks.compileKotlin.configure {
    compilerOptions {
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}
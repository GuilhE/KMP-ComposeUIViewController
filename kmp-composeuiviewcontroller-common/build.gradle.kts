plugins {
    `kmp-composeuiviewcontroller-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
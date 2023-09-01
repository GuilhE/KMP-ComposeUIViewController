plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.ksp.api)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.kotlinCompile)
    testImplementation(libs.test.kotlinCompileKsp)
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
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}
plugins {
    `kmp-composeuiviewcontroller-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinx.serialization)
    implementation(projects.kmpComposeuiviewcontrollerCommon)

    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.kotlinCompile)
    testImplementation(libs.test.kotlinCompileKsp)
    testImplementation(libs.test.junit.implementation)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
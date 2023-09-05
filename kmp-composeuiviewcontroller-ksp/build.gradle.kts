plugins {
    alias(libs.plugins.kotlin.jvm)
    `kmp-composeuiviewcontroller-publish`
}

dependencies {
    implementation(libs.ksp.api)
    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.kotlinCompile)
    testImplementation(libs.test.kotlinCompileKsp)
    testImplementation(project(":kmp-composeuiviewcontroller-annotations"))
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
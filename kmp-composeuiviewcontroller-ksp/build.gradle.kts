plugins {
    alias(libs.plugins.kotlin.jvm)
    `kmp-composeuiviewcontroller-publish`
}

dependencies {
    implementation(libs.ksp.api)
    testImplementation(libs.test.kotlin)
    testImplementation(libs.test.kotlinCompile)
    testImplementation(libs.test.kotlinCompileKsp)
    testImplementation(libs.test.junit.implementation)
    testImplementation(project(":kmp-composeuiviewcontroller-annotations"))
}
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.gradle.publish) apply false
    alias(libs.plugins.vanniktech.maven) apply false //https://github.com/vanniktech/gradle-maven-publish-plugin/issues/670#issuecomment-1839097676
    alias(libs.plugins.kotlin.dokka)
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    group = "com.github.guilhe.kmp"
    version = "2.3.0-1.10.0-rc02"
}

dependencies {
    dokka(project(":kmp-composeuiviewcontroller-annotations"))
    dokka(project(":kmp-composeuiviewcontroller-gradle-plugin"))
}

tasks.register("publishLibraryModules") {
    dependsOn(":kmp-composeuiviewcontroller-common:publishToMavenCentral")
    dependsOn(":kmp-composeuiviewcontroller-annotations:publishToMavenCentral")
    finalizedBy(":kmp-composeuiviewcontroller-ksp:publishToMavenCentral")
}


tasks.register("serveDokka") {
    dependsOn("dokkaGenerate")
    doLast {
        val docsDir = file("${rootProject.layout.buildDirectory.asFile.get().path}/dokka/html")
        val port = (project.findProperty("port") as String?)?.toIntOrNull() ?: 8080
        try {
            java.net.ServerSocket(port).use { true }
        } catch (_: Exception) {
            println("📖 Already serving Dokka docs at http://localhost:$port")
            return@doLast
        }

        val process = ProcessBuilder("python3", "-m", "http.server", "$port")
            .directory(docsDir)
            .redirectErrorStream(true)
            .start()
        println("📖 Serving Dokka docs at http://localhost:$port")
        Thread { process.inputStream.bufferedReader().forEachLine { println(it) } }.start()
        process.waitFor()
    }
}
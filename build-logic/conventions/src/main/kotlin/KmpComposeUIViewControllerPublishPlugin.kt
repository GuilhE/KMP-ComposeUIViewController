package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project

class KmpComposeUIViewControllerPublishPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("com.vanniktech.maven.publish")

        val localPropsFile = project.rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.reader()
                .use { java.util.Properties().apply { load(it) } }
                .onEach { (name, value) -> project.extensions.extraProperties[name.toString()] = value }
        }

        fun getExtraString(name: String) = project.extensions.extraProperties[name]?.toString()
        project.extensions.extraProperties["signing.keyId"] = getExtraString("signing.keyId")
        project.extensions.extraProperties["signing.password"] = getExtraString("signing.password")
        project.extensions.extraProperties["signing.secretKey"] = getExtraString("signing.secretKey")
        project.extensions.extraProperties["mavenUsername"] = getExtraString("mavenUsername")
        project.extensions.extraProperties["mavenPassword"] = getExtraString("mavenPassword") ?: getExtraString("mavenPassword")

        project.extensions.configure("mavenPublishing") {
            val mavenPublishing = this
            mavenPublishing.javaClass.getMethod("publishToMavenCentral", SonatypeHost::class.java, Boolean::class.java)
                .invoke(mavenPublishing, SonatypeHost.CENTRAL_PORTAL, true)
            mavenPublishing.javaClass.getMethod("signAllPublications").invoke(mavenPublishing)
            val pom = mavenPublishing.javaClass.getMethod("pom").invoke(mavenPublishing)
            pom.javaClass.getMethod("name", String::class.java).invoke(pom, "KMP-ComposeUIViewController")
            pom.javaClass.getMethod("description", String::class.java).invoke(pom, "KSP library for generating ComposeUIViewController and UIViewControllerRepresentable files when using Compose Multiplatform for iOS")
            pom.javaClass.getMethod("url", String::class.java).invoke(pom, "https://github.com/GuilhE/KMP-ComposeUIViewController")
            val licenses = pom.javaClass.getMethod("licenses").invoke(pom)
            val license = licenses.javaClass.getMethod("license").invoke(licenses)
            license.javaClass.getMethod("name", String::class.java).invoke(license, "The Apache License, Version 2.0")
            license.javaClass.getMethod("url", String::class.java).invoke(license, "http://www.apache.org/licenses/LICENSE-2.0.txt")
            val developers = pom.javaClass.getMethod("developers").invoke(pom)
            val developer = developers.javaClass.getMethod("developer").invoke(developers)
            developer.javaClass.getMethod("id", String::class.java).invoke(developer, "GuilhE")
            developer.javaClass.getMethod("name", String::class.java).invoke(developer, "Guilherme Delgado")
            developer.javaClass.getMethod("email", String::class.java).invoke(developer, "gdelgado@bliss.pt")
            developer.javaClass.getMethod("url", String::class.java).invoke(developer, "https://github.com/GuilhE")
            val scm = pom.javaClass.getMethod("scm").invoke(pom)
            scm.javaClass.getMethod("url", String::class.java).invoke(scm, "https://github.com/GuilhE/KMP-ComposeUIViewController")
            scm.javaClass.getMethod("connection", String::class.java).invoke(scm, "scm:git:github.com/GuilhE/KMP-ComposeUIViewController.git")
            scm.javaClass.getMethod("developerConnection", String::class.java).invoke(scm, "scm:git:ssh://github.com/GuilhE/KMP-ComposeUIViewController.git")
        }
    }
}

@file:Suppress("unused")

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import groovy.lang.Closure
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugins.signing.SigningExtension

class KmpComposeUIViewControllerPublishPlugin : Plugin<Project> {
    companion object {
        private const val LIB_NAME = "KMP-ComposeUIViewController"
        private const val LIB_DESCRIPTION =
            "KSP library for generating ComposeUIViewController and UIViewControllerRepresentable files when using Compose Multiplatform for iOS"
        private const val LIB_URL = "https://github.com/GuilhE/KMP-ComposeUIViewController"
        private const val DEV_ID = "GuilhE"
        private const val DEV_NAME = "Guilherme Delgado"
        private const val DEV_EMAIL = "gdelgado@bliss.pt"
        private const val LICENSE_NAME = "The Apache License, Version 2.0"
        private const val LICENSE_URL = "http://www.apache.org/licenses/LICENSE-2.0.txt"
    }

    override fun apply(project: Project) {
        val localPropsFile = project.rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            val props = java.util.Properties()
            localPropsFile.inputStream().use { props.load(it) }
            props.forEach { (k, v) ->
                if (project.findProperty(k.toString()) == null) {
                    project.extensions.extraProperties[k.toString()] = v
                }
            }
        }

        val hasSigning = project.findProperty("signing.keyId") != null ||
                project.findProperty("signingInMemoryKeyId") != null ||
                System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyId") != null

        if (!hasSigning) {
            project.logger.lifecycle(">> KmpComposeUIViewControllerPublishPlugin [${project.name}] - no signing configuration found, skipping publish setup.")
            return
        }

        project.plugins.apply("signing")
        project.gradle.taskGraph.whenReady(object : Closure<Unit>(project) {
            fun doCall(graph: org.gradle.api.execution.TaskExecutionGraph) {
                val isMavenLocal = graph.allTasks.any { it.name == "publishToMavenLocal" || it.path.endsWith("publishToMavenLocal") }
                project.extensions.getByType(SigningExtension::class.java).isRequired = !isMavenLocal
                project.logger.lifecycle(">> KmpComposeUIViewControllerPublishPlugin [${project.name}] - isMavenLocal = $isMavenLocal")
            }
        })
        project.plugins.apply("com.vanniktech.maven.publish")
        project.extensions.getByType(MavenPublishBaseExtension::class.java).apply {
            publishToMavenCentral(automaticRelease = false)
            signAllPublications()
            pom {
                name.set(LIB_NAME)
                description.set(LIB_DESCRIPTION)
                url.set(LIB_URL)
                licenses {
                    license {
                        name.set(LICENSE_NAME)
                        url.set(LICENSE_URL)
                    }
                }
                developers {
                    developer {
                        id.set(DEV_ID)
                        name.set(DEV_NAME)
                        email.set(DEV_EMAIL)
                    }
                }
                scm { url.set(LIB_URL) }
            }
        }
    }
}
import com.vanniktech.maven.publish.SonatypeHost
import groovy.lang.Closure
import org.gradle.api.Plugin
import org.gradle.api.Project

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

        project.plugins.apply("com.vanniktech.maven.publish")

        val mavenPublishing = project.extensions.getByName("mavenPublishing")
        mavenPublishing.javaClass.getMethod("publishToMavenCentral", SonatypeHost::class.java, Boolean::class.java)
            .invoke(mavenPublishing, SonatypeHost.CENTRAL_PORTAL, false)
        mavenPublishing.javaClass.getMethod("signAllPublications").invoke(mavenPublishing)
        val pomMethod = mavenPublishing.javaClass.methods.first { it.name == "pom" && it.parameterTypes.size == 1 }
        pomMethod.invoke(mavenPublishing, object : Closure<Unit>(this, this) {
            @Suppress("unused")
            fun doCall(pom: Any) {
                pom.javaClass.getMethod("getName").invoke(pom).javaClass.getMethod("set", Any::class.java)
                    .invoke(pom.javaClass.getMethod("getName").invoke(pom), LIB_NAME)
                pom.javaClass.getMethod("getDescription").invoke(pom).javaClass.getMethod("set", Any::class.java)
                    .invoke(
                        pom.javaClass.getMethod("getDescription").invoke(pom),
                        LIB_DESCRIPTION
                    )
                pom.javaClass.getMethod("getUrl").invoke(pom).javaClass.getMethod("set", Any::class.java)
                    .invoke(pom.javaClass.getMethod("getUrl").invoke(pom), LIB_URL)

                pom.javaClass.getMethod("licenses", Closure::class.java).invoke(pom, object : Closure<Unit>(this, this) {
                    @Suppress("unused")
                    fun doCall(licenses: Any) {
                        licenses.javaClass.getMethod("license", Closure::class.java).invoke(licenses, object : Closure<Unit>(this, this) {
                            @Suppress("unused")
                            fun doCall(license: Any) {
                                license.javaClass.getMethod("getName").invoke(license).javaClass.getMethod("set", Any::class.java)
                                    .invoke(license.javaClass.getMethod("getName").invoke(license), LICENSE_NAME)
                                license.javaClass.getMethod("getUrl").invoke(license).javaClass.getMethod("set", Any::class.java)
                                    .invoke(license.javaClass.getMethod("getUrl").invoke(license), LICENSE_URL)
                            }
                        })
                    }
                })
                pom.javaClass.getMethod("developers", Closure::class.java).invoke(pom, object : Closure<Unit>(this, this) {
                    @Suppress("unused")
                    fun doCall(developers: Any) {
                        developers.javaClass.getMethod("developer", Closure::class.java).invoke(developers, object : Closure<Unit>(this, this) {
                            @Suppress("unused")
                            fun doCall(developer: Any) {
                                developer.javaClass.getMethod("getId").invoke(developer).javaClass.getMethod("set", Any::class.java)
                                    .invoke(developer.javaClass.getMethod("getId").invoke(developer), DEV_ID)
                                developer.javaClass.getMethod("getName").invoke(developer).javaClass.getMethod("set", Any::class.java)
                                    .invoke(developer.javaClass.getMethod("getName").invoke(developer), DEV_NAME)
                                developer.javaClass.getMethod("getEmail").invoke(developer).javaClass.getMethod("set", Any::class.java)
                                    .invoke(developer.javaClass.getMethod("getEmail").invoke(developer), DEV_EMAIL)
                            }
                        })
                    }
                })
                pom.javaClass.getMethod("scm", Closure::class.java).invoke(pom, object : Closure<Unit>(this, this) {
                    @Suppress("unused")
                    fun doCall(scm: Any) {
                        scm.javaClass.getMethod("getUrl").invoke(scm).javaClass.getMethod("set", Any::class.java)
                            .invoke(scm.javaClass.getMethod("getUrl").invoke(scm), LIB_URL)
                    }
                })
            }
        })
    }
}
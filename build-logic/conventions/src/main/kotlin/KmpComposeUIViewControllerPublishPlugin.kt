import com.vanniktech.maven.publish.SonatypeHost
import groovy.lang.Closure
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

        val mavenPublishing = project.extensions.getByName("mavenPublishing")
        mavenPublishing.javaClass.getMethod("publishToMavenCentral", SonatypeHost::class.java, Boolean::class.java)
            .invoke(mavenPublishing, SonatypeHost.CENTRAL_PORTAL, true)
        mavenPublishing.javaClass.getMethod("signAllPublications").invoke(mavenPublishing)
        val pomMethod = mavenPublishing.javaClass.methods.first { it.name == "pom" && it.parameterTypes.size == 1 }
        pomMethod.invoke(mavenPublishing, object : Closure<Unit>(this, this) {
            @Suppress("unused")
            fun doCall(pom: Any) {
                pom.javaClass.getMethod("getName").invoke(pom).javaClass.getMethod("set", Any::class.java)
                    .invoke(pom.javaClass.getMethod("getName").invoke(pom), "KMP-ComposeUIViewController")
                pom.javaClass.getMethod("getDescription").invoke(pom).javaClass.getMethod("set", Any::class.java)
                    .invoke(
                        pom.javaClass.getMethod("getDescription").invoke(pom),
                        "KSP library for generating ComposeUIViewController and UIViewControllerRepresentable files when using Compose Multiplatform for iOS"
                    )
                pom.javaClass.getMethod("getUrl").invoke(pom).javaClass.getMethod("set", Any::class.java)
                    .invoke(pom.javaClass.getMethod("getUrl").invoke(pom), "https://github.com/GuilhE/KMP-ComposeUIViewController")

                pom.javaClass.getMethod("licenses", Closure::class.java).invoke(pom, object : Closure<Unit>(this, this) {
                    @Suppress("unused")
                    fun doCall(licenses: Any) {
                        licenses.javaClass.getMethod("license", Closure::class.java).invoke(licenses, object : Closure<Unit>(this, this) {
                            @Suppress("unused")
                            fun doCall(license: Any) {
                                license.javaClass.getMethod("getName").invoke(license).javaClass.getMethod("set", Any::class.java)
                                    .invoke(license.javaClass.getMethod("getName").invoke(license), "The Apache License, Version 2.0")
                                license.javaClass.getMethod("getUrl").invoke(license).javaClass.getMethod("set", Any::class.java)
                                    .invoke(license.javaClass.getMethod("getUrl").invoke(license), "http://www.apache.org/licenses/LICENSE-2.0.txt")
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
                                    .invoke(developer.javaClass.getMethod("getId").invoke(developer), "GuilhE")
                                developer.javaClass.getMethod("getName").invoke(developer).javaClass.getMethod("set", Any::class.java)
                                    .invoke(developer.javaClass.getMethod("getName").invoke(developer), "Guilherme Delgado")
                                developer.javaClass.getMethod("getEmail").invoke(developer).javaClass.getMethod("set", Any::class.java)
                                    .invoke(developer.javaClass.getMethod("getEmail").invoke(developer), "guilherme.delgado@gmail.com")
                            }
                        })
                    }
                })
                pom.javaClass.getMethod("scm", Closure::class.java).invoke(pom, object : Closure<Unit>(this, this) {
                    @Suppress("unused")
                    fun doCall(scm: Any) {
                        scm.javaClass.getMethod("getUrl").invoke(scm).javaClass.getMethod("set", Any::class.java)
                            .invoke(scm.javaClass.getMethod("getUrl").invoke(scm), "https://github.com/GuilhE/KMP-ComposeUIViewController")
                    }
                })
            }
        } /* as groovy.lang.Closure<*> */)
    }
}
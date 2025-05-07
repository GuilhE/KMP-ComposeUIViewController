plugins {
    id("com.vanniktech.maven.publish")
}

val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.reader()
        .use { java.util.Properties().apply { load(it) } }
        .onEach { (name, value) -> ext[name.toString()] = value }
}

fun getExtraString(name: String) = ext[name]?.toString()
project.extra.set("signing.keyId", getExtraString("signing.keyId"))
project.extra.set("signing.password", getExtraString("signing.password"))
project.extra.set("signing.secretKey", getExtraString("signing.secretKey"))
project.extra.set("mavenUsername", getExtraString("mavenUsername"))
project.extra.set("mavenPassword", getExtraString("mavenPassword") ?: getExtraString("mavenPassword"))

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("KMP-ComposeUIViewController")
        description.set("KSP library for generating ComposeUIViewController and UIViewControllerRepresentable files when using Compose Multiplatform for iOS")
        url.set("https://github.com/GuilhE/KMP-ComposeUIViewController")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("GuilhE")
                name.set("Guilherme Delgado")
                email.set("gdelgado@bliss.pt")
                url.set("https://github.com/GuilhE")
            }
        }
        scm {
            url.set("https://github.com/GuilhE/KMP-ComposeUIViewController")
            connection.set("scm:git:github.com/GuilhE/KMP-ComposeUIViewController.git")
            developerConnection.set("scm:git:ssh://github.com/GuilhE/KMP-ComposeUIViewController.git")
        }
    }
}
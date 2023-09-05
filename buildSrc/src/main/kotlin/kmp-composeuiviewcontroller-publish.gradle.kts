plugins {
    `maven-publish`
    signing
}

ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKey"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null
val localPropsFile = project.rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.reader()
        .use { java.util.Properties().apply { load(it) } }
        .onEach { (name, value) -> ext[name.toString()] = value }
} else {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.secretKey"] = System.getenv("SIGNING_SECRET_KEY")
    ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

fun getExtraString(name: String) = ext[name]?.toString()

val signPublications = getExtraString("signing.keyId") != null

publishing {
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
    }

    publications.withType<MavenPublication> {
        if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            artifact(tasks.register("${name}JavadocJar", Jar::class) {
                archiveClassifier.set("javadoc")
                archiveAppendix.set(this@withType.name)
            })
        }
        if (signPublications) signing.sign(this)

        pom {
            name.set("KMP-ComposeUIViewController")
            description.set("KSP library for generating ComposeUIViewController when using ComposeMultiplatform for iOS")
            url.set("https://github.com/GuilhE/KMP-ComposeUIViewController")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
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
}

if (signPublications) {
    signing {
        getExtraString("signing.secretKey")?.let { secretKey ->
            useInMemoryPgpKeys(getExtraString("signing.keyId"), secretKey, getExtraString("signing.password"))
        }
    }
}

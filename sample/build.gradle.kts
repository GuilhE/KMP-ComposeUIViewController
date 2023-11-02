buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.gradle.android.tools)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

tasks.register<Exec>("addFilesToXcodeproj") {
    workingDir(layout.projectDirectory)
    commandLine("bash", "-c", "./exportToXcode.sh")
}
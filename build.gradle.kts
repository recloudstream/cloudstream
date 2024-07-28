buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.gradle)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.dokka.gradle.plugin)
        // Universal build config
        classpath(libs.buildkonfig.gradle.plugin)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

//tasks.register<Delete>("clean") {
//    delete(rootProject.layout.buildDirectory)
//}

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.gradle)
        classpath(libs.jetbrains.kotlin.gradle.plugin)
        classpath(libs.dokka.gradle.plugin)
        // Universal build config
        classpath(libs.buildkonfig.gradle.plugin)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}
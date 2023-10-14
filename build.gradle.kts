// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // we stay on low ver because prerelease build gradle is fucked
        classpath("com.android.tools.build:gradle:7.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.5.0")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle.kts files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.google.devtools.ksp") version("1.8.20-1.0.11") apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
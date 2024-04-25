buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.10")
        // Universal build config
        classpath("com.codingfeline.buildkonfig:buildkonfig-gradle-plugin:0.15.1")
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

import com.codingfeline.buildkonfig.compiler.FieldSpec

plugins {
    kotlin("multiplatform")
    id("maven-publish")
    id("com.android.library")
    id("com.codingfeline.buildkonfig")
}

kotlin {
    version = "1.0.0"
    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP Lib
            implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1") /* JSON Parser
            ^ Don't Bump Jackson above 2.13.1 , Crashes on Android TV's and FireSticks that have Min API
            Level 25 or Less. */
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
        }
    }
}

repositories {
    mavenLocal()
    maven("https://jitpack.io")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

buildkonfig {
    packageName = "com.lagradost.api"
    exposeObjectWithName = "BuildConfig"

    defaultConfigs {
        val isDebug = kotlin.runCatching { extra.get("isDebug") }.getOrNull() == true
        buildConfigField(FieldSpec.Type.BOOLEAN, "DEBUG", isDebug.toString())
    }
}

android {
    compileSdk = 34
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }

    // If this is the same com.lagradost.cloudstream3.R stops working
    namespace = "com.lagradost.api"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
publishing {
    publications {
        withType<MavenPublication> {
            groupId = "com.lagradost.api"
        }
    }
}
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("multiplatform")
    id("maven-publish")
    id("com.android.library")
    id("com.codingfeline.buildkonfig")
    id("org.jetbrains.dokka")
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

kotlin {
    version = "1.0.0"
    androidTarget()
    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.nicehttp) // HTTP Lib
            implementation(libs.jackson.module.kotlin) // JSON Parser
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.fuzzywuzzy) // Match Extractors
            implementation(libs.rhino) // Run JavaScript
            implementation(libs.newpipeextractor)
            implementation(libs.tmdb.java) // TMDB API v3 Wrapper Made with RetroFit
        }
    }
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(javaTarget)
    }
}

buildkonfig {
    packageName = "com.lagradost.api"
    exposeObjectWithName = "BuildConfig"

    defaultConfigs {
        val isDebug = kotlin.runCatching { extra.get("isDebug") }.getOrNull() == true
        if (isDebug) {
            logger.quiet("Compiling library with debug flag")
        } else {
            logger.quiet("Compiling library with release flag")
        }
        buildConfigField(FieldSpec.Type.BOOLEAN, "DEBUG", isDebug.toString())
    }
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // If this is the same com.lagradost.cloudstream3.R stops working
    namespace = "com.lagradost.api"

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaTarget.target)
        targetCompatibility = JavaVersion.toVersion(javaTarget.target)
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }

    lint {
        targetSdk = libs.versions.targetSdk.get().toInt()
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            groupId = "com.lagradost.api"
        }
    }
}

dokka {
    moduleName = "Library"
    dokkaSourceSets {
        configureEach {
            analysisPlatform = KotlinPlatform.AndroidJVM
            documentedVisibilities(
                VisibilityModifier.Public,
                VisibilityModifier.Protected
            )

            sourceLink {
                localDirectory = file("..")
                remoteUrl("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix = "#L"
            }
        }
    }
}
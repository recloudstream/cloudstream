import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.net.URI

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
    moduleName.set("Library")
    dokkaSourceSets {
        configureEach {
            analysisPlatform.set(KotlinPlatform.AndroidJVM)
            documentedVisibilities(
                VisibilityModifier.Public,
                VisibilityModifier.Protected
            )

            sourceLink {
                localDirectory.set(file(".."))
                remoteUrl("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix.set("#L")
            }

            externalDocumentationLinks {
                register("com.fasterxml.jackson.core") {
                    url = URI("https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core/${libs.versions.jacksonModuleKotlin.get()}/")
                    packageListUrl = URI("https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core/${libs.versions.jacksonModuleKotlin.get()}/package-list")
                }
                register("okio") {
                    url = URI("https://square.github.io/okio/3.x/okio/")
                    packageListUrl = URI("https://square.github.io/okio/3.x/okio/okio/package-list")
                }
                register("okhttp") {
                    url = URI("https://square.github.io/okhttp/5.x/okhttp/okhttp3/")
                    packageListUrl = URI("https://square.github.io/okhttp/5.x/package-list")
                }
            }
        }
    }
}
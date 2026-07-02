import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.dokka.gradle.engine.parameters.KotlinPlatform
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("maven-publish") // Gradle core plugin
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.serialization)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

kotlin {
    version = "1.0.1"

    applyDefaultHierarchyTemplate()

    android {
        // If this is the same com.lagradost.cloudstream3.R stops working
        namespace = "com.lagradost.api"

        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(javaTarget)
        }

        lint {
            targetSdk = libs.versions.targetSdk.get().toInt()
        }
    }

    jvm()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xannotation-default-target=param-property"
        )
    }

    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }

        commonMain.dependencies {
            implementation(libs.annotation) // Annotations
            implementation(libs.jackson.module.kotlin) // JSON Parser
            implementation(libs.jsoup) // HTML Parser
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json) // JSON Parser
            implementation(libs.ksoup) // HTML Parser
            implementation(libs.ktor.http)
            implementation(libs.nicehttp) // HTTP Library
            implementation(libs.rhino) // Run JavaScript
            implementation(libs.tmdb.java) // TMDB API v3 Wrapper Made with RetroFit
            implementation(libs.bundles.cryptography) // Cryptography

            // Deprecated; will be removed once extensions have time to migrate from using it
            implementation("me.xdrop:fuzzywuzzy:1.4.0")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kotlin.reflect)
                implementation(libs.newpipeextractor)
            }
        }

        androidMain { dependsOn(jvmCommonMain) }
        jvmMain { dependsOn(jvmCommonMain) }
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
        // Reads local.properties
        val localProperties = gradleLocalProperties(rootDir, project.providers)
        buildConfigField(
            FieldSpec.Type.STRING,
            "MDL_API_KEY",
            (System.getenv("MDL_API_KEY") ?: localProperties["mdl.key"]).toString()
        )

        buildConfigField(
            FieldSpec.Type.STRING,
            "TRAKT_CLIENT_ID", (System.getenv("TRAKT_CLIENT_ID") ?: localProperties["trakt.id"]).toString()
        )
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

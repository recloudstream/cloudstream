import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka")
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

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

val dokkaImplementation: Configuration by configurations.creating {
    // This ensures you can access artifacts
    isCanBeResolved = true
    isTransitive = true
}

dependencies {
    dokkaImplementation(libs.junit.ktx)
    dokkaImplementation(libs.core.ktx)
    dokkaImplementation(libs.appcompat)
    dokkaImplementation(libs.navigation.ui.ktx)
    dokkaImplementation(libs.lifecycle.livedata.ktx)
    dokkaImplementation(libs.lifecycle.viewmodel.ktx)
    dokkaImplementation(libs.navigation.fragment.ktx)
    dokkaImplementation(libs.preference.ktx)
    dokkaImplementation(libs.material)
    dokkaImplementation(libs.constraintlayout)
    dokkaImplementation(libs.swiperefreshlayout)
    dokkaImplementation(libs.guava)
    dokkaImplementation(libs.auto.service.ksp)
    dokkaImplementation(libs.bundles.media3)
    dokkaImplementation(libs.colorpicker) // Subtitle Color Picker
    dokkaImplementation(libs.bundles.nextlibMedia3)
    dokkaImplementation(libs.newpipeextractor)
    dokkaImplementation(libs.juniversalchardet) // Subtitle Decoding
    dokkaImplementation(libs.acra.core)
    dokkaImplementation(libs.acra.toast)
    dokkaImplementation(libs.shimmer) // Shimmering Effect (Loading Skeleton)
    dokkaImplementation(libs.palette.ktx) // Palette for Images -> Colors
    dokkaImplementation(libs.tvprovider)
    dokkaImplementation(libs.overlappingpanels) // Gestures
    dokkaImplementation(libs.biometric) // Fingerprint Authentication
    dokkaImplementation(libs.previewseekbar.media3) // SeekBar Preview
    dokkaImplementation(libs.qrcode.kotlin) // QR Code for PIN Auth on TV
    dokkaImplementation(libs.rhino) // Run JavaScript
    dokkaImplementation(libs.fuzzywuzzy) // Library/Ext Searching with Levenshtein Distance
    dokkaImplementation(libs.safefile) // To Prevent the URI File Fu*kery
    dokkaImplementation(libs.conscrypt.android) // To Fix SSL Fu*kery on Android 9
    dokkaImplementation(libs.tmdb.java) // TMDB API v3 Wrapper Made with RetroFit
    dokkaImplementation(libs.jackson.module.kotlin) // JSON Parser
    dokkaImplementation(libs.work.runtime)
    dokkaImplementation(libs.work.runtime.ktx)
    dokkaImplementation(libs.nicehttp) // HTTP Lib
}

dokka {
    dokkaSourceSets {
        moduleName = "Cloudstream"

        configureEach {
            classpath.from(android.bootClasspath)
            classpath.from(dokkaImplementation.files)

            sourceLink {
                localDirectory = file("..")
                remoteUrl("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix = "#L"
            }

            externalDocumentationLinks {
                register("android") {
                    url = URI("https://developer.android.com/reference/")
                    packageListUrl = URI("https://developer.android.com/reference/package-list")
                }
                register("androidx") {
                    url = URI("https://developer.android.com/reference/androidx/")
                    packageListUrl = URI("https://developer.android.com/reference/androidx/package-list")
                }
                register("android.kotlin") {
                    url = URI("https://developer.android.com/reference/kotlin/packages/")
                    packageListUrl = URI("https://developer.android.com/reference/kotlin/package-list")
                }
                register("androidx.kotlin") {
                    url = URI("https://developer.android.com/reference/kotlin/androidx/packages/")
                    packageListUrl = URI("https://developer.android.com/reference/kotlin/androidx/package-list")
                }
                register("kotlinx.coroutines") {
                    url = URI("https://kotlinlang.org/api/kotlinx.coroutines/")
                }
                register("kotlinx.datetime") {
                    url = URI("https://kotlinlang.org/api/kotlinx-datetime/")
                    packageListUrl = URI("https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/package-list")
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

        register("androidMain") {
            sourceRoots.from("../library/src/androidMain/kotlin")
        }

        register("commonMain") {
            sourceRoots.from("../library/src/commonMain/kotlin")
        }

        register("kotlinMain") {
            sourceRoots.from("../app/src/main/java")
        }
    }
}
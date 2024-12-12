import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka")
}

android {
    compileSdk = 34
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }

    namespace = "com.lagradost.api"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val dokkaImplementation by configurations.creating {
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
    dokkaImplementation(libs.glide.transformations)
    dokkaImplementation(libs.preference.ktx)
    dokkaImplementation(libs.material)
    dokkaImplementation(libs.constraintlayout)
    dokkaImplementation(libs.swiperefreshlayout)
    dokkaImplementation(libs.glide)
    dokkaImplementation(libs.okhttp3.integration)
    dokkaImplementation(libs.guava)
    dokkaImplementation(libs.auto.service.ksp)
    dokkaImplementation(libs.bundles.media3)
    dokkaImplementation(libs.colorpicker) // Subtitle Color Picker
    dokkaImplementation(libs.media.ffmpeg) // Custom FF-MPEG Lib for Audio Codecs
    dokkaImplementation(libs.newpipeextractor)
    dokkaImplementation(libs.juniversalchardet) // Subtitle Decoding
    dokkaImplementation(libs.acra.core)
    dokkaImplementation(libs.acra.toast)
    dokkaImplementation(libs.shimmer) // Shimmering Effect (Loading Skeleton)
    dokkaImplementation(libs.palette.ktx) // Palette For Images -> Colors
    dokkaImplementation(libs.tvprovider)
    dokkaImplementation(libs.overlappingpanels) // Gestures
    dokkaImplementation(libs.biometric) // Fingerprint Authentication
    dokkaImplementation(libs.previewseekbar.media3) // SeekBar Preview
    dokkaImplementation(libs.qrcode.kotlin) // QR code for PIN Auth on TV
    dokkaImplementation(libs.rhino) // run JavaScript
    dokkaImplementation(libs.fuzzywuzzy) // Library/Ext Searching with Levenshtein Distance
    dokkaImplementation(libs.safefile) // To Prevent the URI File Fu*kery
    dokkaImplementation(libs.conscrypt.android) // To Fix SSL Fu*kery on Android 9
    dokkaImplementation(libs.tmdb.java) // TMDB API v3 Wrapper Made with RetroFit
    dokkaImplementation(libs.jackson.module.kotlin) //JSON Parser
    dokkaImplementation(libs.work.runtime)
    dokkaImplementation(libs.work.runtime.ktx)
    dokkaImplementation(libs.nicehttp) // HTTP Lib
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        moduleName = "Cloudstream"
        register("cloudstream") {
            listOf("androidMain", "commonMain").forEach { srcName ->
                sourceRoot("../library/src/$srcName/kotlin")
            }
            sourceRoot(file("../app/src/main/java"))

            classpath.from(android.bootClasspath)
            classpath.from(dokkaImplementation.files)

            sourceLink {
                localDirectory = file("..")
                remoteUrl = URL("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix = "#L"
            }

            dokkaImplementation.dependencies.forEach {
                externalDocumentationLink {
                    url = URL("https://javadoc.io/doc/${it.group}/${it.name}/${it.version}")
                    packageListUrl = URL("https://javadoc.io/doc/${it.group}/${it.name}/${it.version}/package-list")
                }
            }
        }
    }
}
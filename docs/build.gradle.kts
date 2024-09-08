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
    dokkaImplementation("androidx.test.ext:junit-ktx:1.2.1")
    dokkaImplementation("androidx.core:core-ktx:1.13.1")
    dokkaImplementation("androidx.appcompat:appcompat:1.7.0")
    dokkaImplementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    dokkaImplementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.3")
    dokkaImplementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    dokkaImplementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    dokkaImplementation("jp.wasabeef:glide-transformations:4.3.0")
    dokkaImplementation("androidx.preference:preference-ktx:1.2.1")
    dokkaImplementation("com.google.android.material:material:1.12.0")
    dokkaImplementation("androidx.constraintlayout:constraintlayout:2.1.4")
    dokkaImplementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    dokkaImplementation("com.github.bumptech.glide:glide:4.16.0")
    dokkaImplementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    dokkaImplementation("com.google.guava:guava:33.2.1-android")
    dokkaImplementation("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")
    dokkaImplementation("androidx.media3:media3-ui:1.4.0")
    dokkaImplementation("androidx.media3:media3-cast:1.4.0")
    dokkaImplementation("androidx.media3:media3-common:1.4.0")
    dokkaImplementation("androidx.media3:media3-session:1.4.0")
    dokkaImplementation("androidx.media3:media3-exoplayer:1.4.0")
    dokkaImplementation("com.google.android.mediahome:video:1.0.0")
    dokkaImplementation("androidx.media3:media3-exoplayer-hls:1.4.0")
    dokkaImplementation("androidx.media3:media3-exoplayer-dash:1.4.0")
    dokkaImplementation("androidx.media3:media3-datasource-okhttp:1.4.0")
    dokkaImplementation("com.jaredrummler:colorpicker:1.1.0") // Subtitle Color Picker
    dokkaImplementation("com.github.recloudstream:media-ffmpeg:1.1.0") // Custom FF-MPEG Lib for Audio Codecs
    dokkaImplementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.2")
    dokkaImplementation("com.github.albfernandez:juniversalchardet:2.5.0") // Subtitle Decoding
    dokkaImplementation("ch.acra:acra-core:5.11.3")
    dokkaImplementation("ch.acra:acra-toast:5.11.3")
    dokkaImplementation("com.facebook.shimmer:shimmer:0.5.0") // Shimmering Effect (Loading Skeleton)
    dokkaImplementation("androidx.palette:palette-ktx:1.0.0") // Palette For Images -> Colors
    dokkaImplementation("androidx.tvprovider:tvprovider:1.0.0")
    dokkaImplementation("com.github.discord:OverlappingPanels:0.1.5") // Gestures
    dokkaImplementation("androidx.biometric:biometric:1.2.0-alpha05") // Fingerprint Authentication
    dokkaImplementation("com.github.rubensousa:previewseekbar-media3:1.1.1.0") // SeekBar Preview
    dokkaImplementation("io.github.g0dkar:qrcode-kotlin:4.2.0") // QR code for PIN Auth on TV
    dokkaImplementation("org.mozilla:rhino:1.7.15") // run JavaScript
    dokkaImplementation("me.xdrop:fuzzywuzzy:1.4.0") // Library/Ext Searching with Levenshtein Distance
    dokkaImplementation("com.github.LagradOst:SafeFile:0.0.6") // To Prevent the URI File Fu*kery
    dokkaImplementation("org.conscrypt:conscrypt-android:2.5.2") // To Fix SSL Fu*kery on Android 9
    dokkaImplementation("com.uwetrottmann.tmdb2:tmdb-java:2.11.0") // TMDB API v3 Wrapper Made with RetroFit
    dokkaImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    dokkaImplementation("androidx.work:work-runtime:2.9.0")
    dokkaImplementation("androidx.work:work-runtime-ktx:2.9.0")
    dokkaImplementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP Lib
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
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("kotlin-android")
    id("org.jetbrains.dokka")
}

val tmpFilePath = System.getProperty("user.home") + "/work/_temp/keystore/"
val prereleaseStoreFile: File? = File(tmpFilePath).listFiles()?.first()

fun String.execute() = ByteArrayOutputStream().use { baot ->
    if (project.exec {
            workingDir = projectDir
            commandLine = this@execute.split(Regex("\\s"))
            standardOutput = baot
        }.exitValue == 0)
        String(baot.toByteArray()).trim()
    else null
}

android {
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    viewBinding {
        enable = true
    }

    /* disable this for now
    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }*/

    signingConfigs {
        if (prereleaseStoreFile != null) {
            create("prerelease") {
                storeFile = file(prereleaseStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.lagradost.cloudstream3"
        minSdk = 21
        targetSdk = 33 /* Android 14 is Fu*ked
        ^ https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading*/
        versionCode = 64
        versionName = "4.4.0"

        resValue("string", "app_version", "${defaultConfig.versionName}${versionNameSuffix ?: ""}")
        resValue("string", "commit_hash", "git rev-parse --short HEAD".execute() ?: "")
        resValue("bool", "is_prerelease", "false")

        // Reads local.properties
        val localProperties = gradleLocalProperties(rootDir)

        buildConfigField(
            "long",
            "BUILD_DATE",
            "${System.currentTimeMillis()}"
        )
        buildConfigField(
            "String",
            "SIMKL_CLIENT_ID",
            "\"" + (System.getenv("SIMKL_CLIENT_ID") ?: localProperties["simkl.id"]) + "\""
        )
        buildConfigField(
            "String",
            "SIMKL_CLIENT_SECRET",
            "\"" + (System.getenv("SIMKL_CLIENT_SECRET") ?: localProperties["simkl.secret"]) + "\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("exportSchema", "true")
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("state")
    productFlavors {
        create("stable") {
            dimension = "state"
            resValue("bool", "is_prerelease", "false")
        }
        create("prerelease") {
            dimension = "state"
            resValue("bool", "is_prerelease", "true")
            buildConfigField("boolean", "BETA", "true")
            applicationIdSuffix = ".prerelease"
            if (signingConfigs.names.contains("prerelease")) {
                signingConfig = signingConfigs.getByName("prerelease")
            } else {
                logger.warn("No prerelease signing config!")
            }
            versionNameSuffix = "-PRE"
            versionCode = (System.currentTimeMillis() / 60000).toInt()
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
    }

    namespace = "com.lagradost.cloudstream3"
}

dependencies {
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:core")
    implementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Android Core & Lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")

    // Design & UI
    implementation("jp.wasabeef:glide-transformations:4.3.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Glide Module
    ksp("com.github.bumptech.glide:ksp:4.16.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")

    // For KSP -> Official Annotation Processors are Not Yet Supported for KSP
    ksp("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")
    implementation("com.google.guava:guava:33.2.1-android")
    implementation("dev.zacsweers.autoservice:auto-service-ksp:1.2.0")

    // Media 3 (ExoPlayer)
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-cast:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("com.google.android.mediahome:video:1.0.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // PlayBack
    implementation("com.jaredrummler:colorpicker:1.1.0") // Subtitle Color Picker
    implementation("com.github.recloudstream:media-ffmpeg:1.1.0") // Custom FF-MPEG Lib for Audio Codecs
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.24.2") /* For Trailers
    ^ Update to Latest Commits if Trailers Misbehave, github.com/TeamNewPipe/NewPipeExtractor/commits/dev */
    implementation("com.github.albfernandez:juniversalchardet:2.5.0") // Subtitle Decoding

    // Crash Reports (AcraApplication.kt)
    implementation("ch.acra:acra-core:5.11.3")
    implementation("ch.acra:acra-toast:5.11.3")

    // UI Stuff
    implementation("com.facebook.shimmer:shimmer:0.5.0") // Shimmering Effect (Loading Skeleton)
    implementation("androidx.palette:palette-ktx:1.0.0") // Palette For Images -> Colors
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("com.github.discord:OverlappingPanels:0.1.5") // Gestures
    implementation("androidx.biometric:biometric:1.2.0-alpha05") // Fingerprint Authentication
    implementation("com.github.rubensousa:previewseekbar-media3:1.1.1.0") // SeekBar Preview
    implementation("io.github.g0dkar:qrcode-kotlin:4.2.0") // QR code for PIN Auth on TV

    // Extensions & Other Libs
    implementation("org.mozilla:rhino:1.7.15") // run JavaScript
    implementation("me.xdrop:fuzzywuzzy:1.4.0") // Library/Ext Searching with Levenshtein Distance
    implementation("com.github.LagradOst:SafeFile:0.0.6") // To Prevent the URI File Fu*kery
    implementation("org.conscrypt:conscrypt-android:2.5.2") // To Fix SSL Fu*kery on Android 9
    implementation("com.uwetrottmann.tmdb2:tmdb-java:2.11.0") // TMDB API v3 Wrapper Made with RetroFit
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4") //nio flavor needed for NewPipeExtractor
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1") /* JSON Parser
    ^ Don't Bump Jackson above 2.13.1 , Crashes on Android TV's and FireSticks that have Min API
    Level 25 or Less. */

    // Downloading & Networking
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP Lib

    implementation(project(":library") {
        // There does not seem to be a good way of getting the android flavor.
        val isDebug = gradle.startParameter.taskRequests.any { task ->
            task.args.any { arg ->
                arg.contains("debug", true)
            }
        }

        this.extra.set("isDebug", isDebug)
    })
}

tasks.register<Jar>("androidSourcesJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs) // Full Sources
}

tasks.register<Copy>("copyJar") {
    from(
        "build/intermediates/compile_app_classes_jar/prereleaseDebug",
        "../library/build/libs"
    )
    into("build/app-classes")
    include("classes.jar", "library-jvm*.jar")
    // Remove the version
    rename("library-jvm.*.jar", "library-jvm.jar")
}

// Merge the app classes and the library classes into classes.jar
tasks.register<Jar>("makeJar") {
    // Duplicates cause hard to catch errors, better to fail at compile time.
    duplicatesStrategy = DuplicatesStrategy.FAIL
    dependsOn(tasks.getByName("copyJar"))
    from(
        zipTree("build/app-classes/classes.jar"),
        zipTree("build/app-classes/library-jvm.jar")
    )
    destinationDirectory.set(layout.buildDirectory)
    archivesName = "classes"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
    }
}
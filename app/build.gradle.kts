import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
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

    compileSdk = 35
    //  buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.lagradost.cloudstream3"
        minSdk = 21
        targetSdk = 35
        versionCode = 64
        versionName = "4.4.2"

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
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.core)
    implementation(libs.junit.ktx)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Android Core & Lifecycle
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment.ktx)

    // Design & UI
    implementation(libs.preference.ktx)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)

    // Coil Image Loading
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    // Media 3 (ExoPlayer)
    implementation(libs.bundles.media3)
    implementation(libs.video)

    // PlayBack
    implementation(libs.colorpicker) // Subtitle Color Picker
    //implementation(libs.media.ffmpeg) // Custom FF-MPEG Lib for Audio Codecs
    implementation(libs.newpipeextractor) /* For Trailers
    ^ Update to Latest Commits if Trailers Misbehave, github.com/TeamNewPipe/NewPipeExtractor/commits/dev */
    implementation(libs.juniversalchardet) // Subtitle Decoding
    // ffmpeg decoding
    implementation(libs.nextlib.media3ext)
    implementation(libs.nextlib.mediainfo)

    // Crash Reports (AcraApplication.kt)
    implementation(libs.acra.core)
    implementation(libs.acra.toast)

    // UI Stuff
    implementation(libs.shimmer) // Shimmering Effect (Loading Skeleton)
    implementation(libs.palette.ktx) // Palette For Images -> Colors
    implementation(libs.tvprovider)
    implementation(libs.overlappingpanels) // Gestures
    implementation(libs.biometric) // Fingerprint Authentication
    implementation(libs.previewseekbar.media3) // SeekBar Preview
    implementation(libs.qrcode.kotlin) // QR code for PIN Auth on TV

    // Extensions & Other Libs
    implementation(libs.rhino) // run JavaScript
    implementation(libs.fuzzywuzzy) // Library/Ext Searching with Levenshtein Distance
    implementation(libs.safefile) // To Prevent the URI File Fu*kery
    implementation(libs.conscrypt.android) // To Fix SSL Fu*kery on Android 9
    implementation(libs.tmdb.java) // TMDB API v3 Wrapper Made with RetroFit
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio) //nio flavor needed for NewPipeExtractor
    implementation(libs.jackson.module.kotlin) {
        version {
            strictly("2.13.1")
        }
        because("Don't Bump Jackson above 2.13.1 , Crashes on Android TV's and FireSticks that have Min API Level 25 or Less.")
    } //JSON Parser

    // torrent support
    implementation(libs.aria2cstream)

    // Downloading & Networking
    implementation(libs.work.runtime)
    implementation(libs.work.runtime.ktx)
    implementation(libs.nicehttp) // HTTP Lib

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
    archiveBaseName = "classes"
    //archiveName = "classes"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
    }
}
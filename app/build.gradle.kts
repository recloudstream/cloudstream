import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.net.URL

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
        create("prerelease") {
            if (prereleaseStoreFile != null) {
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
            signingConfig = signingConfigs.getByName("prerelease")
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

repositories {
    maven("https://jitpack.io")
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
    implementation(libs.glide.transformations)
    implementation(libs.preference.ktx)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)

    // Glide Module
    ksp(libs.ksp)
    implementation(libs.glide)
    implementation(libs.okhttp3.integration)

    // For KSP -> Official Annotation Processors are Not Yet Supported for KSP
    ksp(libs.auto.service.ksp)
    implementation(libs.guava)
    implementation(libs.auto.service.ksp)

    // Media 3 (ExoPlayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.cast)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.video)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)

    // PlayBack
    implementation(libs.colorpicker) // Subtitle Color Picker
    implementation(libs.media.ffmpeg) // Custom FF-MPEG Lib for Audio Codecs
    implementation(libs.newpipeextractor) /* For Trailers
    ^ Update to Latest Commits if Trailers Misbehave, github.com/TeamNewPipe/NewPipeExtractor/commits/dev */
    implementation(libs.juniversalchardet) // Subtitle Decoding

    // Crash Reports (AcraApplication.kt)
    implementation(libs.acra.core)
    implementation(libs.acra.toast)

    // UI Stuff
    implementation(libs.shimmer) // Shimmering Effect (Loading Skeleton)
    implementation(libs.palette.ktx) // Palette For Images -> Colors
    implementation(libs.tvprovider)
    implementation(libs.overlappingpanels) // Gestures
    implementation (libs.biometric) // Fingerprint Authentication
    implementation(libs.previewseekbar.media3) // SeekBar Preview
    implementation(libs.qrcode.kotlin) // QR code for PIN Auth on TV

    // Extensions & Other Libs
    implementation(libs.rhino) // run JavaScript
    implementation(libs.fuzzywuzzy) // Library/Ext Searching with Levenshtein Distance
    implementation(libs.safefile) // To Prevent the URI File Fu*kery
    implementation(libs.conscrypt.android) // To Fix SSL Fu*kery on Android 9
    implementation(libs.tmdb.java) // TMDB API v3 Wrapper Made with RetroFit
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.jackson.module.kotlin) /* JSON Parser
    ^ Don't Bump Jackson above 2.13.1 , Crashes on Android TV's and FireSticks that have Min API
    Level 25 or Less. */

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
    archivesName = "classes"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
    }
}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set("Cloudstream")
    dokkaSourceSets {
        named("main") {
            sourceLink {
                // Unix based directory relative path to the root of the project (where you execute gradle respectively).
                localDirectory.set(file("src/main/java"))

                // URL showing where the source code can be accessed through the web browser
                remoteUrl.set(URL("https://github.com/recloudstream/cloudstream/tree/master/app/src/main/java"))

                // Suffix which is used to append the line number to the URL. Use #L for GitHub
                remoteLineSuffix.set("#L")
            }
        }
    }
}

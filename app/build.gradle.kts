import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.dokka.gradle.DokkaTask
import java.io.ByteArrayOutputStream
import java.net.URL

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
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

    // disable this for now
    //externalNativeBuild {
    //    cmake {
    //        path("CMakeLists.txt")
    //    }
    //}

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

    compileSdk = 33
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.lagradost.cloudstream3"
        minSdk = 21
        targetSdk = 33

        versionCode = 62
        versionName = "4.2.0"

        resValue("string", "app_version", "${defaultConfig.versionName}${versionNameSuffix ?: ""}")
        resValue("string", "commit_hash", "git rev-parse --short HEAD".execute() ?: "")
        resValue("bool", "is_prerelease", "false")

        // Reads local.properties
        val localProperties = gradleLocalProperties(rootDir)

        buildConfigField(
            "String",
            "BUILDDATE",
            "new java.text.SimpleDateFormat(\"yyyy-MM-dd HH:mm\").format(new java.util.Date(" + System.currentTimeMillis() + "L));"
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

        kapt {
            includeCompileClasspath = true
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
    //toolchain {
    //     languageVersion.set(JavaLanguageVersion.of(17))
    // }
    // jvmToolchain(17)

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=compatibility")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    namespace = "com.lagradost.cloudstream3"
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.google.android.mediahome:video:1.0.0")
    implementation("androidx.test.ext:junit-ktx:1.1.5")
    testImplementation("org.json:json:20180813")

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1") // need target 32 for 1.5.0

    // dont change this to 1.6.0 it looks ugly af
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:core")

    //implementation("io.karn:khttp-android:0.1.2") //okhttp instead
    // implementation("org.jsoup:jsoup:1.13.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

    implementation("androidx.preference:preference-ktx:1.2.0")

    implementation("com.github.bumptech.glide:glide:4.13.1")
    kapt("com.github.bumptech.glide:compiler:4.13.1")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.13.0")

    implementation("jp.wasabeef:glide-transformations:4.3.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // implementation("androidx.leanback:leanback-paging:1.1.0-alpha09")

    // Media 3
    implementation("androidx.media3:media3-common:1.1.1")
    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.1.1")
    implementation("androidx.media3:media3-ui:1.1.1")
    implementation("androidx.media3:media3-session:1.1.1")
    implementation("androidx.media3:media3-cast:1.1.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.1.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.1.1")
    // Custom ffmpeg extension for audio codecs
    implementation("com.github.recloudstream:media-ffmpeg:1.1.0")

    //implementation("com.google.android.exoplayer:extension-leanback:2.14.0")

    // Bug reports
    implementation("ch.acra:acra-core:5.11.0")
    implementation("ch.acra:acra-toast:5.11.0")

    compileOnly("com.google.auto.service:auto-service-annotations:1.0")
    //either for java sources:
    annotationProcessor("com.google.auto.service:auto-service:1.0")
    //or for kotlin sources (requires kapt gradle plugin):
    kapt("com.google.auto.service:auto-service:1.0")

    // subtitle color picker
    implementation("com.jaredrummler:colorpicker:1.1.0")

    //run JS
    // do not upgrade to 1.7.14, since in 1.7.14 Rhino uses the `SourceVersion` class, which is not
    // available on Android (even when using desugaring), and `NoClassDefFoundError` is thrown
    implementation("org.mozilla:rhino:1.7.13")

    // TorrentStream
    //implementation("com.github.TorrentStream:TorrentStream-Android:2.7.0")

    // Downloading
    implementation("androidx.work:work-runtime:2.8.1")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Networking
    // implementation("com.squareup.okhttp3:okhttp:4.9.2")
    // implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.9.1")
    implementation("com.github.Blatzar:NiceHttp:0.4.3")
    // To fix SSL fuckery on android 9
    implementation("org.conscrypt:conscrypt-android:2.2.1")
    // Util to skip the URI file fuckery 🙏
    implementation("com.github.LagradOst:SafeFile:0.0.5")

    // API because cba maintaining it myself
    implementation("com.uwetrottmann.tmdb2:tmdb-java:2.6.0")

    implementation("com.github.discord:OverlappingPanels:0.1.5")
    // debugImplementation because LeakCanary should only run in debug builds.
    //debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    // for shimmer when loading
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    implementation("androidx.tvprovider:tvprovider:1.0.0")

    // used for subtitle decoding https://github.com/albfernandez/juniversalchardet
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")

    // newpipe yt taken from https://github.com/TeamNewPipe/NewPipeExtractor/commits/dev
    // this should be updated frequently to avoid trailer fu*kery
    implementation("com.github.teamnewpipe:NewPipeExtractor:1f08d28")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.6")

    // Library/extensions searching with Levenshtein distance
    implementation("me.xdrop:fuzzywuzzy:1.4.0")

    // color palette for images -> colors
    implementation("androidx.palette:palette-ktx:1.0.0")
}

tasks.register("androidSourcesJar", Jar::class) {
    archiveClassifier.set("sources")
    from(android.sourceSets.getByName("main").java.srcDirs) //full sources
}

// this is used by the gradlew plugin
tasks.register("makeJar", Copy::class) {
    from("build/intermediates/compile_app_classes_jar/prereleaseDebug")
    into("build")
    include("classes.jar")
    dependsOn("build")
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

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

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        moduleName = "Cloudstream"
        register("cloudstream") {
            listOf("androidMain", "commonMain", "jvmMain").forEach { srcName ->
                sourceRoot("../library/src/$srcName/kotlin")
            }
            classpath.from(android.bootClasspath)
            sourceRoot(file("../app/src/main/java"))
            sourceLink {
                localDirectory = file("..")
                remoteUrl = URL("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix = "#L"
            }
        }
    }
}
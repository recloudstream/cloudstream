import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    id("org.jetbrains.dokka")
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        moduleName = "Cloudstream"
        register("cloudstream") {
            listOf("androidMain", "commonMain", "jvmMain").forEach { srcName ->
                sourceRoot("../library/src/$srcName/kotlin")
            }
            sourceRoot(file("../app/src/main/java"))
            sourceLink {
                localDirectory = file("..")
                remoteUrl = URL("https://github.com/recloudstream/cloudstream/tree/master")
                remoteLineSuffix = "#L"
            }
        }
    }
}
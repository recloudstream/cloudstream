plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka.gradle.plugin)
}

dependencies {
    dokka(project(":app:"))
    dokka(project(":library:"))
}

dokka {
    moduleName = "Cloudstream"
}
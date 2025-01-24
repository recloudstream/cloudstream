plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

dependencies {
    dokka(project(":app:"))
    dokka(project(":library:"))
}

dokka {
    moduleName = "Cloudstream"
}
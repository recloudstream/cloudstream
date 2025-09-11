plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply true
    alias(libs.plugins.buildKonfig) apply false
    kotlin("android") version libs.versions.kotlinGradlePluginVersion apply false
    kotlin("jvm") version libs.versions.kotlinGradlePluginVersion apply false
    kotlin("multiplatform") version libs.versions.kotlinGradlePluginVersion apply false
}

dependencies {
    dokka(project(":app"))
    dokka(project(":library:"))
}
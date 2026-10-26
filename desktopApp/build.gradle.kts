import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {}
    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.collections.immutable)
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
            implementation(project(":library"))

            // The library only exposes these as implementation details, but we use them directly
            implementation(libs.nicehttp)
            implementation(libs.jackson.module.kotlin)
            implementation(libs.kotlin.reflect)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.java)

            // Video playback through the system LibVLC
            implementation(libs.vlcj)
            // Converts Android-only (.cs3 / dex) extensions to JVM bytecode
            implementation(libs.dex2jar.translator)
        }
    }
}

/**
 * Extensions are built for Android and reference Android classes in code paths desktop never runs
 * (settings screens). The bytecode verifier would reject those classes up front, so it is turned off
 * for classes outside the JDK, missing classes then only fail if that code actually runs.
 */
val extensionJvmArgs = arrayOf("-XX:+UnlockDiagnosticVMOptions", "-XX:-BytecodeVerificationRemote")

tasks.withType<JavaExec> {
    // java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
    jvmArgs("--enable-native-access=ALL-UNNAMED", *extensionJvmArgs)
}

compose.resources {
    packageOfResClass = "com.lagradost.cloudstream4.desktop.generated.resources"
    generateResClass = always
}

// CI passes the version from the release tag (desktop-v1.2.3 -> 1.2.3) so updates compare correctly
val appVersion = (findProperty("desktopVersion") as String?)?.takeIf { it.isNotBlank() } ?: libs.versions.versionName.get()
// GitHub repository whose releases the app updates itself from
val updateRepository = (findProperty("desktopUpdateRepo") as String?) ?: "atomic-crafter/cloudstream"

val generateBuildInfo by tasks.registering {
    val output = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    val version = appVersion
    val repository = updateRepository
    inputs.property("version", version)
    inputs.property("repository", repository)
    outputs.dir(output)
    doLast {
        val file = output.get().file("com/lagradost/cloudstream4/desktop/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.lagradost.cloudstream4.desktop
            |
            |object BuildInfo {
            |    const val VERSION = "$version"
            |    const val UPDATE_REPOSITORY = "$repository"
            |}
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.getByName("jvmMain").kotlin.srcDir(generateBuildInfo)

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream4.MainKt"
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Xmx2G",
            *extensionJvmArgs,
        )

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm,
            )
            packageName = "CloudStream"
            // Native installers require MAJOR.MINOR.PATCH with MAJOR > 0
            packageVersion = appVersion
            description = "Stream and browse media using CloudStream extensions"
            vendor = "recloudstream"
            copyright = "GPL-3.0"
            licenseFile.set(rootProject.file("LICENSE"))

            // From suggestRuntimeModules, plus what extensions commonly need at runtime
            // (DNS, XML, every charset and locale for parsing sites, extra crypto)
            modules(
                "java.instrument",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.scripting",
                "java.security.jgss",
                "java.sql",
                "java.xml",
                "jdk.charsets",
                "jdk.crypto.ec",
                "jdk.dynalink",
                "jdk.httpserver",
                "jdk.localedata",
                "jdk.management",
                "jdk.net",
                "jdk.unsupported",
                "jdk.zipfs",
            )

            val iconsRoot = project.file("src/desktop-icons")
            macOS {
                bundleID = "com.lagradost.cloudstream4"
                iconFile.set(iconsRoot.resolve("icon-mac.icns"))
                appCategory = "public.app-category.entertainment"
            }
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                menuGroup = "CloudStream"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Must stay the same between versions for upgrades to replace the old install
                upgradeUuid = "8d2b7a3e-4d6c-4f1e-9a57-3c0f3e9b6a21"
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
                packageName = "cloudstream"
                debMaintainer = "cloudstream@users.noreply.github.com"
                menuGroup = "AudioVideo"
                appCategory = "Video"
                shortcut = true
            }
        }
    }
}

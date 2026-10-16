import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {}
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.coil.network.okhttp)
            implementation(libs.jna)
            implementation(libs.haze)
            implementation(libs.okhttp)
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
            implementation(project(":library"))
        }
    }
}

// java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

/**
 * Downloads the pinned libmpv build that plays video and unpacks libmpv-2.dll from it.
 * Both downloads are checked against their SHA-256 and kept in the build folder between builds.
 */
abstract class PrepareLibmpv : DefaultTask() {
    @get:Input abstract val archiveUrl: Property<String>
    @get:Input abstract val archiveSha256: Property<String>
    @get:Input abstract val sevenZipUrl: Property<String>
    @get:Input abstract val sevenZipSha256: Property<String>
    @get:Internal abstract val downloadDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun prepare() {
        val downloads = downloadDir.get().asFile.apply { mkdirs() }
        val archive = fetch(archiveUrl.get(), archiveSha256.get(), downloads.resolve("mpv-dev.7z"))
        val sevenZip = fetch(sevenZipUrl.get(), sevenZipSha256.get(), downloads.resolve("7zr.exe"))
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        exec.exec { commandLine(sevenZip.absolutePath, "x", "-y", "-o${out.absolutePath}", archive.absolutePath, "libmpv-2.dll") }
    }

    private fun fetch(url: String, sha256: String, file: File): File {
        if (!file.exists() || file.sha256() != sha256) {
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val actual = file.sha256()
        if (actual != sha256) {
            file.delete()
            throw GradleException("$url has SHA-256 $actual, expected $sha256")
        }
        return file
    }

    private fun File.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }
}

val prepareLibmpv by tasks.registering(PrepareLibmpv::class) {
    // Built by the libmpv workflow, which publishes the complete source next to it. The release workflow
    // copies that source into each app release from the release named here, so keep this URL first
    archiveUrl = "https://github.com/harsh-pockt/cloudstream/releases/download/libmpv-20261004-git-413ff0b1cd/mpv-dev-x86_64-20261004-git-413ff0b1cd.7z"
    archiveSha256 = "356bec543ce3acd8811f10b11cddaaca9b6946226b9c8dc99e9542c61d37b672"
    // The archive uses the BCJ2 filter, which only 7-Zip itself unpacks. 7zr.exe is its small standalone version,
    // mirrored too: 7-zip.org replaces it with each new version
    sevenZipUrl = "https://github.com/harsh-pockt/cloudstream/releases/download/libmpv-20261002/7zr.exe"
    sevenZipSha256 = "ad4c82fadcbdf93c03b4fc440f300509c7d60c5c2f4d183e35d9d70d6957037d"
    downloadDir = layout.buildDirectory.dir("libmpv-download")
    // In the windows folder of the app resources, so it is only bundled on Windows
    outputDir = layout.buildDirectory.dir("libmpv/windows")
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
}

/** Files installed next to the app: the notices and licence from src/app-resources, and libmpv on Windows */
val assembleAppResources by tasks.registering(Sync::class) {
    from("src/app-resources")
    from(rootProject.file("LICENSE")) { into("common"); rename { "LICENSE.txt" } }
    from(prepareLibmpv.map { it.outputDir.get().asFile.parentFile })
    into(layout.buildDirectory.dir("app-resources"))
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(assembleAppResources) }

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream4.MainKt"
        // Read by AppVersion, for the update check. The release workflow passes the tag's version, which
        // can be a pre-release such as 4.8.0-beta.2
        jvmArgs("-Dcloudstream.version=${providers.gradleProperty("appVersion").getOrElse(libs.versions.versionName.get())}")
        // Memory. Without a limit the heap may grow to a quarter of the computer's RAM before it is
        // collected, while the app uses well under 100 MB of it. When idle, the heap shrinks and the
        // memory goes back to Windows. Strings repeated across extensions' results are kept once
        jvmArgs(
            "-Xmx1g",
            "-XX:+UseG1GC",
            "-XX:G1PeriodicGCInterval=60000",
            "-XX:MinHeapFreeRatio=10",
            "-XX:MaxHeapFreeRatio=30",
            "-XX:+UseStringDeduplication",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CloudStream"
            // Holds libmpv-2.dll and the notices, found at runtime through the compose.application.resources.dir property
            appResourcesRootDir = layout.buildDirectory.dir("app-resources")
            // MSI requires MAJOR.MINOR.BUILD with numbers only, and only replaces an installed copy with a
            // higher one: two betas of 4.8.0 would both be 4.8.0. So the release workflow passes
            // MAJOR.MINOR.<its run number>, which grows with every release
            packageVersion = providers.gradleProperty("msiVersion").getOrElse(libs.versions.versionName.get())

            // The packaged runtime only contains these JDK modules, regenerate with ./gradlew :desktopApp:suggestRuntimeModules
            // jdk.zipfs is used when converting Android extensions, which suggestRuntimeModules does not see
            modules("java.instrument", "java.management", "java.net.http", "java.sql", "jdk.dynalink", "jdk.unsupported", "jdk.zipfs")

            val iconsRoot = project.file("src/desktop-icons")
            macOS {
                // iconFile.set(iconsRoot.resolve("icon-mac.icns"))
            }
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                menu = true
                menuGroup = "CloudStream"
                shortcut = true
                dirChooser = true
                // Must never change, otherwise new versions install side by side instead of upgrading
                // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                upgradeUuid = "33b76f91-08d2-41b1-9ed1-ddde419f24be"
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }
        }

        //buildTypes.release.proguard {
        //    configurationFiles.from(project.file("rules.pro"))
        //}
    }
}
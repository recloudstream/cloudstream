package com.lagradost.cloudstream4.desktop.data

import java.io.File

enum class OperatingSystem { MacOS, Windows, Linux }

object AppDirs {
    val os: OperatingSystem = System.getProperty("os.name").lowercase().let {
        when {
            it.contains("mac") || it.contains("darwin") -> OperatingSystem.MacOS
            it.contains("win") -> OperatingSystem.Windows
            else -> OperatingSystem.Linux
        }
    }

    private val home = File(System.getProperty("user.home"))

    /** Where settings, repositories and extensions live, following each platform's conventions. */
    val dataDir: File by lazy {
        when (os) {
            OperatingSystem.MacOS -> File(home, "Library/Application Support/CloudStream")
            OperatingSystem.Windows -> File(System.getenv("APPDATA") ?: home.path, "CloudStream")
            OperatingSystem.Linux -> File(
                System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "${home.path}/.local/share",
                "cloudstream"
            )
        }.also { it.mkdirs() }
    }

    val cacheDir: File by lazy {
        when (os) {
            OperatingSystem.MacOS -> File(home, "Library/Caches/CloudStream")
            OperatingSystem.Windows -> File(System.getenv("LOCALAPPDATA") ?: dataDir.path, "CloudStream/Cache")
            OperatingSystem.Linux -> File(
                System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "${home.path}/.cache",
                "cloudstream"
            )
        }.also { it.mkdirs() }
    }

    val extensionsDir: File get() = File(dataDir, "Extensions").also { it.mkdirs() }
}

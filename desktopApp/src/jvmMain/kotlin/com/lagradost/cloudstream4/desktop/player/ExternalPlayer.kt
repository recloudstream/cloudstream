package com.lagradost.cloudstream4.desktop.player

import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.OperatingSystem
import java.io.File

/** Launches VLC, mpv or IINA (or the user's configured player) as a separate process. */
object ExternalPlayer {
    private fun onPath(name: String): String? {
        val exe = if (AppDirs.os == OperatingSystem.Windows) "$name.exe" else name
        return System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, exe) }
            .firstOrNull { it.canExecute() }?.absolutePath
    }

    private fun candidates(): List<String> = when (AppDirs.os) {
        OperatingSystem.MacOS -> listOf(
            "/Applications/IINA.app/Contents/MacOS/iina-cli",
            "/opt/homebrew/bin/mpv",
            "/usr/local/bin/mpv",
            "/Applications/VLC.app/Contents/MacOS/VLC",
        )

        OperatingSystem.Windows -> listOf(
            "${System.getenv("ProgramFiles")}\\VideoLAN\\VLC\\vlc.exe",
            "${System.getenv("ProgramFiles(x86)")}\\VideoLAN\\VLC\\vlc.exe",
        )

        OperatingSystem.Linux -> emptyList()
    }.filter { File(it).exists() } + listOfNotNull(onPath("mpv"), onPath("vlc"))

    fun detect(): String? = AppData.settings.value.externalPlayer.takeIf { it.isNotBlank() } ?: candidates().firstOrNull()

    /** @return false if no player could be found */
    fun launch(url: String, title: String, subtitles: List<File>): Boolean {
        val player = detect() ?: return false
        val name = File(player).name.lowercase()
        val command = mutableListOf(player)
        when {
            name.startsWith("mpv") -> {
                command += "--force-media-title=$title"
                subtitles.forEach { command += "--sub-file=${it.absolutePath}" }
            }

            name.startsWith("iina") -> {
                command += "--mpv-force-media-title=$title"
                subtitles.forEach { command += "--mpv-sub-file=${it.absolutePath}" }
            }

            name.startsWith("vlc") -> {
                command += "--meta-title=$title"
                subtitles.firstOrNull()?.let { command += "--sub-file=${it.absolutePath}" }
            }
        }
        command += url
        ProcessBuilder(command).inheritIO().start()
        return true
    }
}

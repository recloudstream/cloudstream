package com.lagradost.cloudstream4.desktop.update

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.desktop.BuildInfo
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.data.OperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubAsset(
    val name: String,
    @JsonProperty("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRelease(
    @JsonProperty("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

data class Update(val version: String, val notes: String?, val asset: GithubAsset)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: Update) : UpdateState
    data class Downloading(val update: Update, val progress: Float) : UpdateState
    /** Downloaded, installed on restart or when the app quits */
    data class Ready(val update: Update, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Updates the installed app from the GitHub releases of [BuildInfo.UPDATE_REPOSITORY].
 * Releases are the `desktop-v*` tags published by the Desktop Installers workflow.
 */
object Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val TAG_PREFIX = "desktop-v"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildInfo.VERSION

    /** Path of the running launcher, set by jpackage. Null when running from Gradle, where updating makes no sense */
    private val appPath: String? get() = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    val canInstall: Boolean get() = appPath != null

    private fun parseVersion(version: String): List<Int> =
        version.removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }

    /** Positive if [a] is newer than [b] */
    fun compareVersions(a: String, b: String): Int {
        val left = parseVersion(a)
        val right = parseVersion(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    /** Release assets are named like CloudStream-macos-arm64-CloudStream-1.2.3.dmg by the workflow */
    private fun pickAsset(assets: List<GithubAsset>): GithubAsset? {
        val arm = System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm64") }
        val (platform, extension) = when (AppDirs.os) {
            OperatingSystem.MacOS -> (if (arm) "macos-arm64" else "macos-x64") to ".dmg"
            OperatingSystem.Windows -> "windows-x64" to ".msi"
            OperatingSystem.Linux -> "linux-x64" to ".deb"
        }
        return assets.firstOrNull { it.name.contains(platform) && it.name.endsWith(extension) }
    }

    suspend fun fetchLatest(): Update? {
        val text = app.get(
            "https://api.github.com/repos/${BuildInfo.UPDATE_REPOSITORY}/releases?per_page=20",
            headers = mapOf("Accept" to "application/vnd.github+json"),
        ).text
        return mapper.readValue<List<GithubRelease>>(text)
            .filter { !it.draft && !it.prerelease && it.tagName.startsWith(TAG_PREFIX) }
            .mapNotNull { release ->
                pickAsset(release.assets)?.let { Update(release.tagName.removePrefix(TAG_PREFIX), release.body, it) }
            }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            ?.takeIf { compareVersions(it.version, currentVersion) > 0 }
    }

    /** Checks for an update and, if automatic updates are on, downloads it in the background */
    fun check(userInitiated: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading || current is UpdateState.Ready) return
        _state.value = UpdateState.Checking
        scope.launch {
            val update = try {
                fetchLatest()
            } catch (t: Throwable) {
                logError(t)
                _state.value = if (userInitiated) UpdateState.Failed("Could not check for updates: ${t.message}") else UpdateState.Idle
                return@launch
            }
            when {
                update == null -> _state.value = UpdateState.UpToDate
                AppData.settings.value.autoUpdate && canInstall -> download(update)
                else -> _state.value = UpdateState.Available(update)
            }
        }
    }

    fun download(update: Update) {
        _state.value = UpdateState.Downloading(update, 0f)
        scope.launch {
            try {
                val dir = File(AppDirs.cacheDir, "updates").also { it.deleteRecursively(); it.mkdirs() }
                val target = File(dir, update.asset.name)
                val part = File(dir, update.asset.name + ".part")
                app.get(update.asset.downloadUrl).okhttpResponse.use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    val total = response.body.contentLength().takeIf { it > 0 } ?: update.asset.size
                    var written = 0L
                    part.outputStream().use { out ->
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                written += read
                                if (total > 0) _state.value = UpdateState.Downloading(update, written.toFloat() / total)
                            }
                        }
                    }
                    if (total > 0 && written != total) throw IllegalStateException("Download incomplete")
                }
                part.renameTo(target)
                _state.value = UpdateState.Ready(update, target)
            } catch (t: Throwable) {
                logError(t)
                _state.value = UpdateState.Failed("Update download failed: ${t.message}")
            }
        }
    }

    @Volatile
    private var installing = false

    /** Installs a downloaded update when the app quits, so automatic updates need no clicks at all */
    fun installOnExitIfReady() {
        if (installing) return
        val ready = _state.value as? UpdateState.Ready ?: return
        if (AppData.settings.value.autoUpdate) runCatching { launchInstaller(ready.file, relaunch = false) }.onFailure { logError(it) }
    }

    /** Quits, installs the downloaded update and starts the new version */
    fun installAndRestart() {
        val ready = _state.value as? UpdateState.Ready ?: return
        try {
            launchInstaller(ready.file, relaunch = true)
            installing = true
            exitProcess(0)
        } catch (t: Throwable) {
            logError(t)
            _state.value = UpdateState.Failed("Could not install the update: ${t.message}")
        }
    }

    /**
     * The running app cannot replace itself, so a small script waits for this process to exit,
     * installs the update and optionally starts the new version.
     */
    private fun launchInstaller(file: File, relaunch: Boolean) {
        val launcher = appPath ?: throw IllegalStateException("Updates only work in the installed app")
        val pid = ProcessHandle.current().pid().toString()
        val flag = if (relaunch) "1" else "0"
        val dir = file.parentFile

        val command = when (AppDirs.os) {
            OperatingSystem.MacOS -> {
                // .../CloudStream.app/Contents/MacOS/CloudStream -> .../CloudStream.app
                val bundle = File(launcher).parentFile.parentFile.parentFile.absolutePath
                val script = File(dir, "install.sh").apply {
                    writeText(
                        """
                        |#!/bin/sh
                        |while kill -0 "$1" 2>/dev/null; do sleep 0.5; done
                        |MNT=$(mktemp -d)
                        |# The DMG shows the license first, answer it so this runs unattended
                        |yes | hdiutil attach -nobrowse -readonly -noverify -mountpoint "${'$'}MNT" "$2" >/dev/null || exit 1
                        |NEW=$(find "${'$'}MNT" -maxdepth 1 -name "*.app" | head -n 1)
                        |rm -rf "$3.old"
                        |if mv "$3" "$3.old" && ditto "${'$'}NEW" "$3"; then
                        |  rm -rf "$3.old"
                        |else
                        |  rm -rf "$3"; mv "$3.old" "$3"
                        |fi
                        |hdiutil detach "${'$'}MNT" -quiet
                        |xattr -dr com.apple.quarantine "$3" 2>/dev/null
                        |if [ "$4" = "1" ]; then open "$3"; fi
                        |""".trimMargin()
                    )
                    setExecutable(true)
                }
                listOf("/bin/sh", script.absolutePath, pid, file.absolutePath, bundle, flag)
            }

            OperatingSystem.Windows -> {
                val script = File(dir, "install.cmd").apply {
                    writeText(
                        """
                        |@echo off
                        |:wait
                        |tasklist /FI "PID eq %1" 2>nul | find "%1" >nul && (timeout /t 1 /nobreak >nul & goto wait)
                        |msiexec /i "%~2" /passive /norestart
                        |if "%~4"=="1" start "" "%~3"
                        |""".trimMargin().replace("\n", "\r\n")
                    )
                }
                listOf("cmd", "/c", script.absolutePath, pid, file.absolutePath, launcher, flag)
            }

            OperatingSystem.Linux -> {
                val script = File(dir, "install.sh").apply {
                    writeText(
                        """
                        |#!/bin/sh
                        |while kill -0 "$1" 2>/dev/null; do sleep 0.5; done
                        |# Installing a .deb needs root, pkexec shows the system password prompt
                        |pkexec dpkg -i "$2" || xdg-open "$2"
                        |if [ "$4" = "1" ]; then "$3" & fi
                        |""".trimMargin()
                    )
                    setExecutable(true)
                }
                listOf("/bin/sh", script.absolutePath, pid, file.absolutePath, launcher, flag)
            }
        }
        ProcessBuilder(command)
            .redirectOutput(File(dir, "install.log"))
            .redirectErrorStream(true)
            .start()
    }
}

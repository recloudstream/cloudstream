package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.desktop.BuildInfo
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.data.PlayerKind
import com.lagradost.cloudstream4.desktop.downloads.DownloadManager
import com.lagradost.cloudstream4.desktop.player.ExternalPlayer
import com.lagradost.cloudstream4.desktop.player.Vlc
import com.lagradost.cloudstream4.desktop.update.UpdateState
import com.lagradost.cloudstream4.desktop.update.Updater
import com.lagradost.cloudstream4.theme.CloudStreamPrimaryColor
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import java.awt.Desktop

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val settings by AppData.settings.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 800.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Appearance") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudStreamThemeMode.entries.filter { it != CloudStreamThemeMode.Dynamic }.forEach { mode ->
                    FilterChip(
                        settings.theme == mode,
                        onClick = { AppData.updateSettings { it.copy(theme = mode) } },
                        label = { Text(if (mode == CloudStreamThemeMode.FollowSystem) "System" else mode.name) },
                    )
                }
            }
            Text("Accent color", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudStreamPrimaryColor.entries.forEach { color ->
                    Box(
                        Modifier.size(32.dp).background(color.color, CircleShape)
                            .border(
                                if (settings.primaryColor == color) 3.dp else 0.dp,
                                if (settings.primaryColor == color) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { AppData.updateSettings { it.copy(primaryColor = color) } }
                    )
                }
            }
        }

        Section("Player") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.player == PlayerKind.Internal, { AppData.updateSettings { it.copy(player = PlayerKind.Internal) } },
                    label = { Text("Built-in (VLC)") })
                FilterChip(settings.player == PlayerKind.External, { AppData.updateSettings { it.copy(player = PlayerKind.External) } },
                    label = { Text("External player") })
            }
            Text(
                if (Vlc.isAvailable) "VLC found, the built-in player is available."
                else "VLC was not found. Install VLC to use the built-in player.",
                style = MaterialTheme.typography.bodySmall,
                color = if (Vlc.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            if (!Vlc.isAvailable) TextButton(onClick = { openInBrowser("https://www.videolan.org/vlc/") }) { Text("Download VLC") }
            OutlinedTextField(
                value = settings.externalPlayer,
                onValueChange = { value -> AppData.updateSettings { it.copy(externalPlayer = value) } },
                label = { Text("External player path") },
                placeholder = { Text(ExternalPlayer.detect() ?: "Auto detect VLC, mpv or IINA") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Seek ${settings.seekSeconds} seconds with the arrow keys", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { seconds ->
                    FilterChip(settings.seekSeconds == seconds, { AppData.updateSettings { it.copy(seekSeconds = seconds) } },
                        label = { Text("${seconds}s") })
                }
            }
        }

        Section("Downloads") {
            OutlinedTextField(
                value = settings.downloadDirectory,
                onValueChange = { value -> AppData.updateSettings { it.copy(downloadDirectory = value) } },
                label = { Text("Download folder") },
                placeholder = { Text(DownloadManager.defaultDirectory.absolutePath) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Extensions") {
            SwitchRow(
                "Use jsDelivr for GitHub",
                "Downloads repositories and extensions through cdn.jsdelivr.net, useful if GitHub is blocked",
                settings.jsdelivrProxy,
            ) { value -> AppData.updateSettings { it.copy(jsdelivrProxy = value) } }
        }

        Section("Storage") {
            Text("Data folder: ${AppDirs.dataDir.absolutePath}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { runCatching { Desktop.getDesktop().open(AppDirs.dataDir) } }) { Text("Open data folder") }
        }

        Section("Updates") {
            val update by Updater.state.collectAsState()
            Text("Version ${Updater.currentVersion}, updates from github.com/${BuildInfo.UPDATE_REPOSITORY}", style = MaterialTheme.typography.bodyMedium)
            SwitchRow("Check for updates on startup", null, settings.checkForUpdates) { value ->
                AppData.updateSettings { it.copy(checkForUpdates = value) }
            }
            SwitchRow(
                "Update automatically",
                "Downloads new versions in the background and installs them when you restart or quit",
                settings.autoUpdate,
            ) { value -> AppData.updateSettings { it.copy(autoUpdate = value) } }
            if (!Updater.canInstall) {
                Text(
                    "Running outside the installed app, updates can be checked but not installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                when (val current = update) {
                    is UpdateState.Ready -> TextButton(onClick = Updater::installAndRestart) { Text("Restart and update to ${current.update.version}") }
                    is UpdateState.Available -> TextButton(onClick = {
                        if (Updater.canInstall) Updater.download(current.update) else openInBrowser(current.update.asset.downloadUrl)
                    }) { Text("Download ${current.update.version}") }

                    is UpdateState.Downloading, UpdateState.Checking -> Unit
                    else -> TextButton(onClick = { Updater.check(userInitiated = true) }) { Text("Check for updates") }
                }
                Text(
                    when (val current = update) {
                        UpdateState.Idle -> ""
                        UpdateState.Checking -> "Checking..."
                        UpdateState.UpToDate -> "You have the latest version"
                        is UpdateState.Available -> "Version ${current.update.version} is available"
                        is UpdateState.Downloading -> "Downloading ${current.update.version}: ${(current.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "Version ${current.update.version} will be installed when you quit"
                        is UpdateState.Failed -> current.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (update is UpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("About") {
            Text("CloudStream for desktop, running on ${AppDirs.os.name}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { openInBrowser("https://github.com/recloudstream/cloudstream") }) { Text("Source code") }
        }
        Spacer(Modifier.height(32.dp))
    }
}

package com.lagradost.cloudstream3.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.isAppRestricted
import com.lagradost.cloudstream3.utils.BatteryOptimizationChecker.showRequestIgnoreBatteryOptDialog
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.cloudstream4.compose.ActionDialog
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.lagradost.safefile.SafeFile
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import com.mihon.presentation.settings.collectAsState
import com.mihon.presentation.settings.widget.TextPreferenceWidget
import kotlinx.collections.immutable.persistentListOf

class SettingsGeneralScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.category_general)

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()

        // TODO Refactor entirely to use a different file path selector ect like QuickNovel
        val selectFileSelector =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                // It lies, it can be null if file manager quits.
                if (uri == null) return@rememberLauncherForActivityResult
                val context = CloudStreamApp.context ?: return@rememberLauncherForActivityResult

                try {
                    val settings = AppSettings(context)
                    // RW perms for the path
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    context.contentResolver.takePersistableUriPermission(uri, flags)

                    val filePath = SafeFile.fromUri(context, uri)?.filePath()
                    println("Selected URI path: $uri - Full path: $filePath")

                    // store the actual URI instead of the path due to permissions.
                    // filePath should only be used for cosmetic purposes.
                    val visual = filePath ?: uri.toString()
                    settings.general.downloadPath.set(uri.toString())
                    settings.general.downloadPathVisual.set(visual)
                } catch (t: Throwable) {
                    logError(t)
                }
            }

        val bananas by settings.general.bananas.collectAsState()
        val parallelDownloads by settings.general.parallelDownloads.collectAsState()
        val concurrentConnections by settings.general.concurrentConnections.collectAsState()
        val locale by settings.general.locale.collectAsState()
        val downloadPathVisual by settings.general.downloadPathVisual.collectAsState()
        //val downloadPath by settings.general.downloadPath.collectAsState()

        return persistentListOf(
            Preference.PreferenceItem.BasicListPreference(
                value = locale,
                entries = appLanguages.associate { (name, code) -> (code to (name to code).nameNextToFlagEmoji()) },
                title = stringResource(R.string.app_language),
                icon = painterResource(R.drawable.ic_baseline_language_24),
                onValueChanged = { value ->
                    settings.general.locale.set(value)
                    activity?.recreate()
                },
                subtitleProvider = { v, e -> e[v] ?: getCurrentLocale(LocalContext.current) }
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.title_downloads),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        icon = painterResource(R.drawable.netflix_download),
                        subtitle = downloadPathVisual,
                        title = stringResource(R.string.download_path_pref),
                        onClick = {
                            // This is not a ListPreference because the old selection system is
                            // broken af. This needs to be refactored to QuickNovels download path
                            // system.
                            selectFileSelector.launch(Uri.EMPTY)
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        icon = painterResource(R.drawable.arrow_or_edge_24px),
                        value = parallelDownloads,
                        valueRange = 1..10,
                        title = stringResource(R.string.parallel_downloads),
                        subtitle = stringResource(R.string.download_parallel_settings_des),
                        onValueChanged = settings.general.parallelDownloads::set,
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        icon = painterResource(R.drawable.arrow_and_edge_24px),
                        value = concurrentConnections,
                        valueRange = 1..10,
                        title = stringResource(R.string.concurrent_connections),
                        subtitle = stringResource(R.string.concurrent_connections_settings_des),
                        onValueChanged = settings.general.concurrentConnections::set,
                    ),
                    Preference.PreferenceItem.CustomPreference(
                        title = stringResource(R.string.battery_dialog_title),
                        content = {
                            var isBatteryShown by remember { mutableStateOf(false) }
                            val context = LocalContext.current

                            TextPreferenceWidget(
                                title = stringResource(R.string.battery_dialog_title),
                                icon = painterResource(R.drawable.ic_battery),
                                onPreferenceClick = {
                                    if (isAppRestricted(context)) {
                                        isBatteryShown = true
                                    } else {
                                        showToast(R.string.app_unrestricted_toast)
                                    }
                                })

                            if (isBatteryShown) {
                                ActionDialog(
                                    icon = painterResource(R.drawable.ic_battery),
                                    title = stringResource(R.string.battery_dialog_title),
                                    text = stringResource(R.string.battery_dialog_message),
                                    confirmText = stringResource(R.string.ok),
                                    dismissText = stringResource(R.string.cancel),
                                    dismiss = {
                                        isBatteryShown = false
                                        settings.general.batterOptimization.set(false)
                                    },
                                    confirm = {
                                        isBatteryShown = false
                                        // The og impl never modified it to true?
                                        // settings.general.batterOptimization.set(true)
                                        context.showRequestIgnoreBatteryOptDialog()
                                    }
                                )
                            }
                        }
                    ),
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_bypass),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.add_site_pref),
                        subtitle = stringResource(R.string.add_site_summary),
                        icon = painterResource(R.drawable.ic_baseline_add_24),
                        onClick = {
                            // TODO refactor into compose
                            if (SettingsGeneral.getCurrent().isEmpty()) {
                                SettingsGeneral.showAdd()
                            } else {
                                SettingsGeneral.showAddOrDelete()
                            }
                        }
                    ),
                    Preference.PreferenceItem.ListPreference(
                        title = stringResource(R.string.dns_pref),
                        subtitle = stringResource(R.string.dns_pref_summary),
                        icon = painterResource(R.drawable.ic_baseline_dns_24),
                        preference = settings.general.dns,
                        entries = integerArrayResource(R.array.dns_pref_values).zip(
                            stringArrayResource(R.array.dns_pref)
                        ).toMap(),
                        onValueChanged = {
                            (CloudStreamApp.context)?.let { ctx ->
                                app.initClient(ctx, ignoreSSL = false)
                                @OptIn(UnsafeSSL::class)
                                insecureApp.initClient(ctx, ignoreSSL = true)
                            }
                            return@ListPreference true
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        title = stringResource(R.string.jsdelivr_proxy),
                        subtitle = stringResource(R.string.jsdelivr_proxy_summary),
                        icon = painterResource(R.drawable.ic_github_logo),
                        preference = settings.general.jsdelivrProxy
                    )
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_links),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.github),
                        subtitle = "https://github.com/recloudstream/cloudstream",
                        icon = painterResource(R.drawable.ic_github_logo),
                        onClick = {
                            CloudStreamApp.openBrowser("https://github.com/recloudstream/cloudstream")
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.lightnovel),
                        subtitle = "https://github.com/LagradOst/QuickNovel",
                        icon = painterResource(R.drawable.quick_novel_icon),
                        onClick = {
                            CloudStreamApp.openBrowser("https://github.com/LagradOst/QuickNovel")
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.discord),
                        subtitle = "https://discord.gg/5Hus6fM",
                        icon = painterResource(R.drawable.ic_baseline_discord_24),
                        onClick = {
                            CloudStreamApp.openBrowser("https://discord.gg/5Hus6fM")
                        }
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.cs3wiki),
                        subtitle = "https://cloudstream.miraheze.org/",
                        icon = painterResource(R.drawable.baseline_description_24),
                        onClick = {
                            CloudStreamApp.openBrowser("https://cloudstream.miraheze.org/")
                        }
                    ),
                )
            ),

            Preference.PreferenceItem.TextPreference(
                title = stringResource(R.string.benene),
                subtitle = if (bananas == 0) {
                    stringResource(R.string.benene_count_text_none)
                } else {
                    stringResource(R.string.benene_count_text, bananas)
                },
                onClick = {
                    settings.general.bananas.set(bananas + 1)
                },
                icon = painterResource(R.drawable.benene),
            ),
            Preference.PreferenceItem.InfoPreference(title = stringResource(R.string.legal_notice_text)),
        )
    }
}


@PreviewLightDark
@Composable
private fun SettingGeneralPreview() {
    val screen = SettingsGeneralScreen()
    CloudStreamPreviewTheme {
        screen.Content()
    }
}
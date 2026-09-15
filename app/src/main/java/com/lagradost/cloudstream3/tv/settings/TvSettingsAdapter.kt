package com.lagradost.cloudstream3.tv.settings

import android.content.Context
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.tv.model.TvSettingControlKind
import com.lagradost.cloudstream3.tv.model.TvSettingItem
import com.lagradost.cloudstream3.tv.model.TvSettingOption
import com.lagradost.cloudstream3.tv.model.TvSettingsCatalog
import com.lagradost.cloudstream3.tv.model.TvSettingsCategory
import com.lagradost.cloudstream3.tv.model.TvSettingsSection
import com.lagradost.cloudstream3.ui.player.CustomDecoder
import com.lagradost.cloudstream3.ui.settings.appLanguages
import com.lagradost.cloudstream3.ui.settings.getCurrentLocale
import com.lagradost.cloudstream3.ui.settings.nameNextToFlagEmoji
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageTagIETF
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.SubtitleHelper.languages
import com.lagradost.cloudstream4.AppSettings
import com.mihon.common.preference.AndroidPreferenceStore
import com.mihon.common.preference.PreferenceData

/**
 * Adapts EXISTING [AppSettings] / PreferenceManager / setKey prefs into TV presentation rows.
 * Architectural Q: YES — same underlying CloudStream preference values/behavior
 * (no TV-specific subtitle config path, no parallel store, no new DataStore keys).
 */
class TvSettingsAdapter(
    private val context: Context,
    private val settings: AppSettings,
) {
    private val androidStore = AndroidPreferenceStore(context)
    private val filterSubLangPref = androidStore.getBoolean(
        context.getString(R.string.filter_sub_lang_key),
        false,
    )
    private val encodingPref = androidStore.getString(
        context.getString(R.string.subtitles_encoding_key),
        "",
    )

    fun buildCatalog(): TvSettingsCatalog {
        val sections = listOfNotNull(
            section(TvSettingsCategory.Playback, playbackItems()),
            section(TvSettingsCategory.Subtitles, subtitleItems()),
            section(TvSettingsCategory.Appearance, appearanceItems()),
            section(TvSettingsCategory.Language, languageItems()),
            section(TvSettingsCategory.Downloads, downloadItems()),
            section(TvSettingsCategory.App, appItems()),
        )
        val account = DataStoreHelper.getCurrentAccount()
            ?: runCatching { DataStoreHelper.getDefaultAccount(context) }.getOrNull()
        return TvSettingsCatalog(
            sections = sections,
            accountDisplayName = account?.name,
        )
    }

    fun toggleBoolean(id: String): ApplyResult {
        val pref = booleanPref(id) ?: return ApplyResult.Unknown
        pref.set(!pref.get())
        return ApplyResult.Applied
    }

    fun selectEnum(id: String, optionKey: String): ApplyResult {
        return when (id) {
            ID_SOFTWARE_DECODING -> {
                settings.player.softwareDecoding.set(optionKey.toInt())
                ApplyResult.Applied
            }
            ID_TV_SEEK_ON -> {
                settings.player.tvSeekOnTime.set(optionKey.toInt())
                ApplyResult.Applied
            }
            ID_TV_SEEK_OFF -> {
                settings.player.tvSeekOffTime.set(optionKey.toInt())
                ApplyResult.Applied
            }
            ID_CONFIRM_EXIT -> {
                settings.ui.confirmExit.set(optionKey.toInt())
                ApplyResult.Applied
            }
            ID_LOCALE -> {
                settings.general.locale.set(optionKey)
                ApplyResult.NeedsRecreate
            }
            ID_DNS -> {
                settings.general.dns.set(optionKey.toInt())
                // Same downstream as phone SettingsGeneralScreen ListPreference onValueChanged.
                CloudStreamApp.context?.let { ctx ->
                    app.initClient(ctx, ignoreSSL = false)
                    @OptIn(UnsafeSSL::class)
                    insecureApp.initClient(ctx, ignoreSSL = true)
                }
                ApplyResult.Applied
            }
            ID_PARALLEL_DOWNLOADS -> {
                settings.general.parallelDownloads.set(optionKey.toInt())
                ApplyResult.Applied
            }
            ID_CONCURRENT_CONNECTIONS -> {
                settings.general.concurrentConnections.set(optionKey.toInt())
                ApplyResult.Applied
            }
            ID_SUB_AUTO_SELECT -> {
                // Highest-level existing write: CloudStreamApp.setKey (JSON literal), same as
                // SubtitlesFragment / GeneratorPlayer — NOT DataPreferenceStore.getString (raw).
                CloudStreamApp.setKey(SUBTITLE_AUTO_SELECT_KEY, optionKey)
                ApplyResult.Applied
            }
            ID_SUB_ENCODING -> {
                encodingPref.set(optionKey)
                // Same companion downstream as the player source/subs HUD after a write.
                CustomDecoder.updateForcedEncoding(context)
                ApplyResult.Applied
            }
            else -> ApplyResult.Unknown
        }
    }

    sealed interface ApplyResult {
        data object Applied : ApplyResult
        data object NeedsRecreate : ApplyResult
        data object Unknown : ApplyResult
    }

    private fun section(
        category: TvSettingsCategory,
        items: List<TvSettingItem>,
    ): TvSettingsSection? {
        if (items.isEmpty()) return null
        return TvSettingsSection(category, items)
    }

    private fun playbackItems(): List<TvSettingItem> = listOf(
        boolItem(
            id = ID_AUTOPLAY,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.autoplay_next_settings),
            summary = context.getString(R.string.autoplay_next_settings_des),
            pref = settings.player.autoPlayEnabled,
        ),
        boolItem(
            id = ID_SKIP_OP,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.video_skip_op),
            summary = context.getString(R.string.enable_skip_op_from_database_des),
            pref = settings.player.skipOpEnabled,
        ),
        boolItem(
            id = ID_EPISODE_SYNC,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.episode_sync_settings),
            summary = context.getString(R.string.episode_sync_settings_des),
            pref = settings.player.episodeSync,
        ),
        boolItem(
            id = ID_START_PAUSED,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.start_paused_settings),
            summary = context.getString(R.string.start_paused_settings_des),
            pref = settings.player.startPaused,
        ),
        boolItem(
            id = ID_SPEED,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.eigengraumode_settings),
            summary = context.getString(R.string.speed_setting_summary),
            pref = settings.player.speedEnabled,
        ),
        enumItem(
            id = ID_SOFTWARE_DECODING,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.software_decoding),
            summary = context.getString(R.string.software_decoding_desc),
            options = zipIntString(
                R.array.software_decoding_switch_values,
                R.array.software_decoding_switch,
            ),
            selectedKey = settings.player.softwareDecoding.get().toString(),
        ),
        enumItem(
            id = ID_TV_SEEK_ON,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.android_tv_interface_on_seek_settings),
            summary = context.getString(R.string.android_tv_interface_on_seek_settings_summary),
            options = seekSecondOptions(),
            selectedKey = nearestSeekKey(settings.player.tvSeekOnTime.get()),
        ),
        enumItem(
            id = ID_TV_SEEK_OFF,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.android_tv_interface_off_seek_settings),
            summary = context.getString(R.string.android_tv_interface_off_seek_settings_summary),
            options = seekSecondOptions(),
            selectedKey = nearestSeekKey(settings.player.tvSeekOffTime.get()),
        ),
        boolItem(
            id = ID_SHOW_NAME,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.source_name),
            pref = settings.player.showName,
        ),
        boolItem(
            id = ID_SHOW_RESOLUTION,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.resolution),
            pref = settings.player.showResolution,
        ),
        boolItem(
            id = ID_SHOW_MEDIA_INFO,
            category = TvSettingsCategory.Playback,
            title = context.getString(R.string.video_info),
            pref = settings.player.showMediaInfo,
        ),
    )

    /**
     * Phase 12 — only prefs with established key + API + behavior + TV value.
     * SaveCaptionStyle blob / Chromecast / download multi-select kept out (see companion notes).
     */
    private fun subtitleItems(): List<TvSettingItem> {
        val nextPlayback = EFFECT_NEXT_PLAYBACK
        return listOf(
            enumItem(
                id = ID_SUB_AUTO_SELECT,
                category = TvSettingsCategory.Subtitles,
                title = context.getString(R.string.subs_auto_select_language),
                summary = context.getString(R.string.player_subtitles_settings_des),
                options = autoSelectLanguageOptions(),
                selectedKey = autoSelectSelectedKey(),
                effectHint = nextPlayback,
            ),
            enumItem(
                id = ID_SUB_ENCODING,
                category = TvSettingsCategory.Subtitles,
                title = context.getString(R.string.subtitles_encoding),
                options = zipStringString(
                    R.array.subtitles_encoding_values,
                    R.array.subtitles_encoding_list,
                ),
                selectedKey = encodingPref.get(),
                effectHint = nextPlayback,
            ),
            boolItem(
                id = ID_SUB_FILTER_LANG,
                category = TvSettingsCategory.Subtitles,
                title = context.getString(R.string.subtitles_filter_lang),
                pref = filterSubLangPref,
                effectHint = nextPlayback,
            ),
        )
    }

    private fun appearanceItems(): List<TvSettingItem> = listOf(
        boolItem(
            id = ID_TRAILERS,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.show_trailers_settings),
            pref = settings.ui.trailersEnabled,
        ),
        boolItem(
            id = ID_KITSU,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.kitsu_settings),
            pref = settings.ui.kitsuPostersEnabled,
        ),
        boolItem(
            id = ID_CAST,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.show_cast_in_details),
            pref = settings.ui.castEnabled,
        ),
        boolItem(
            id = ID_FILLERS,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.show_fillers_settings),
            pref = settings.ui.fillersEnabled,
        ),
        boolItem(
            id = ID_CLOCK,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.tv_layout_clock_settings),
            summary = context.getString(R.string.tv_layout_clock_settings_des),
            pref = settings.ui.showClock,
        ),
        boolItem(
            id = ID_METADATA_OVERLAY,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.show_player_metadata_overlay),
            pref = settings.ui.showMetadataOverlay,
        ),
        enumItem(
            id = ID_CONFIRM_EXIT,
            category = TvSettingsCategory.Appearance,
            title = context.getString(R.string.confirm_before_exiting_title),
            summary = context.getString(R.string.confirm_before_exiting_desc),
            options = zipIntString(R.array.confirm_exit_values, R.array.confirm_exit),
            selectedKey = settings.ui.confirmExit.get().toString(),
        ),
    )

    private fun languageItems(): List<TvSettingItem> {
        val options = appLanguages.map { pair ->
            TvSettingOption(
                key = pair.second,
                label = pair.nameNextToFlagEmoji(),
            )
        }
        val current = settings.general.locale.get().ifBlank { getCurrentLocale(context) }
        val selected = options.firstOrNull { it.key.equals(current, ignoreCase = true) }?.key
            ?: options.firstOrNull { current.startsWith(it.key, ignoreCase = true) }?.key
            ?: current
        return listOf(
            enumItem(
                id = ID_LOCALE,
                category = TvSettingsCategory.Language,
                title = context.getString(R.string.app_language),
                options = options,
                selectedKey = selected,
                requiresRestart = true,
                restartMessage = context.getString(R.string.apply_on_restart),
            ),
        )
    }

    private fun downloadItems(): List<TvSettingItem> {
        val countOptions = (1..10).map { n ->
            TvSettingOption(key = n.toString(), label = n.toString())
        }
        return listOf(
            enumItem(
                id = ID_PARALLEL_DOWNLOADS,
                category = TvSettingsCategory.Downloads,
                title = context.getString(R.string.parallel_downloads),
                summary = context.getString(R.string.download_parallel_settings_des),
                options = countOptions,
                selectedKey = settings.general.parallelDownloads.get().coerceIn(1, 10).toString(),
            ),
            enumItem(
                id = ID_CONCURRENT_CONNECTIONS,
                category = TvSettingsCategory.Downloads,
                title = context.getString(R.string.concurrent_connections),
                summary = context.getString(R.string.concurrent_connections_settings_des),
                options = countOptions,
                selectedKey = settings.general.concurrentConnections.get().coerceIn(1, 10).toString(),
            ),
        )
    }

    private fun appItems(): List<TvSettingItem> {
        val accountName = DataStoreHelper.getCurrentAccount()?.name
            ?: runCatching { DataStoreHelper.getDefaultAccount(context).name }.getOrNull()
            ?: context.getString(R.string.default_account)
        val items = mutableListOf(
            TvSettingItem(
                id = ID_ACCOUNT_DISPLAY,
                category = TvSettingsCategory.App,
                title = context.getString(R.string.account),
                summary = "Local profile (read-only)",
                valueLabel = accountName,
                control = TvSettingControlKind.ReadOnly,
            ),
            boolItem(
                id = ID_SKIP_ACCOUNT,
                category = TvSettingsCategory.App,
                title = context.getString(R.string.skip_startup_account_select_pref),
                pref = settings.security.skipAccountSelection,
            ),
            boolItem(
                id = ID_AUTO_UPDATE,
                category = TvSettingsCategory.App,
                title = context.getString(R.string.updates_settings),
                summary = context.getString(R.string.updates_settings_des),
                pref = settings.updates.showAppUpdates,
            ),
            enumItem(
                id = ID_DNS,
                category = TvSettingsCategory.App,
                title = context.getString(R.string.dns_pref),
                summary = context.getString(R.string.dns_pref_summary),
                options = zipIntString(R.array.dns_pref_values, R.array.dns_pref),
                selectedKey = settings.general.dns.get().toString(),
            ),
            boolItem(
                id = ID_JSDELIVR,
                category = TvSettingsCategory.App,
                title = context.getString(R.string.jsdelivr_proxy),
                summary = context.getString(R.string.jsdelivr_proxy_summary),
                pref = settings.general.jsdelivrProxy,
            ),
        )
        if (BuildConfig.DEBUG) {
            items.add(
                TvSettingItem(
                    id = ID_FOCUS_PROBE,
                    category = TvSettingsCategory.App,
                    title = context.getString(R.string.compose_tv_debug),
                    summary = "Phase 2 focus probe (debug builds only)",
                    control = TvSettingControlKind.Action,
                    valueLabel = "Open",
                ),
            )
        }
        return items
    }

    private fun booleanPref(id: String): PreferenceData<Boolean>? = when (id) {
        ID_AUTOPLAY -> settings.player.autoPlayEnabled
        ID_SKIP_OP -> settings.player.skipOpEnabled
        ID_EPISODE_SYNC -> settings.player.episodeSync
        ID_START_PAUSED -> settings.player.startPaused
        ID_SPEED -> settings.player.speedEnabled
        ID_SHOW_NAME -> settings.player.showName
        ID_SHOW_RESOLUTION -> settings.player.showResolution
        ID_SHOW_MEDIA_INFO -> settings.player.showMediaInfo
        ID_TRAILERS -> settings.ui.trailersEnabled
        ID_KITSU -> settings.ui.kitsuPostersEnabled
        ID_CAST -> settings.ui.castEnabled
        ID_FILLERS -> settings.ui.fillersEnabled
        ID_CLOCK -> settings.ui.showClock
        ID_METADATA_OVERLAY -> settings.ui.showMetadataOverlay
        ID_SKIP_ACCOUNT -> settings.security.skipAccountSelection
        ID_AUTO_UPDATE -> settings.updates.showAppUpdates
        ID_JSDELIVR -> settings.general.jsdelivrProxy
        ID_SUB_FILTER_LANG -> filterSubLangPref
        else -> null
    }

    private fun boolItem(
        id: String,
        category: TvSettingsCategory,
        title: String,
        summary: String? = null,
        pref: PreferenceData<Boolean>,
        effectHint: String? = null,
    ): TvSettingItem {
        val value = pref.get()
        return TvSettingItem(
            id = id,
            category = category,
            title = title,
            summary = summary,
            valueLabel = if (value) "On" else "Off",
            control = TvSettingControlKind.Boolean,
            booleanValue = value,
            effectHint = effectHint,
        )
    }

    private fun enumItem(
        id: String,
        category: TvSettingsCategory,
        title: String,
        summary: String? = null,
        options: List<TvSettingOption>,
        selectedKey: String,
        requiresRestart: Boolean = false,
        restartMessage: String? = null,
        effectHint: String? = null,
    ): TvSettingItem {
        val optionsWithUnknown = optionsWithUnknownCurrent(options, selectedKey)
        val label = optionsWithUnknown.firstOrNull { it.key == selectedKey }?.label ?: "Unknown"
        return TvSettingItem(
            id = id,
            category = category,
            title = title,
            summary = summary,
            valueLabel = label,
            control = TvSettingControlKind.Enum,
            options = optionsWithUnknown,
            selectedOptionKey = selectedKey,
            requiresRestart = requiresRestart,
            restartMessage = restartMessage,
            effectHint = effectHint,
        )
    }

    /**
     * Unknown persisted values stay visible as Current — never coerced / written on open.
     */
    private fun optionsWithUnknownCurrent(
        options: List<TvSettingOption>,
        selectedKey: String,
    ): List<TvSettingOption> {
        if (options.any { it.key == selectedKey }) return options
        val current = TvSettingOption(
            key = selectedKey,
            label = if (selectedKey.isBlank()) "Unknown" else "Current",
        )
        return listOf(current) + options
    }

    private fun zipIntString(valuesRes: Int, namesRes: Int): List<TvSettingOption> {
        val values = context.resources.getIntArray(valuesRes)
        val names = context.resources.getStringArray(namesRes)
        val n = minOf(values.size, names.size)
        return (0 until n).map { i ->
            TvSettingOption(key = values[i].toString(), label = names[i])
        }
    }

    private fun zipStringString(valuesRes: Int, namesRes: Int): List<TvSettingOption> {
        val values = context.resources.getStringArray(valuesRes)
        val names = context.resources.getStringArray(namesRes)
        val n = minOf(values.size, names.size)
        return (0 until n).map { i ->
            TvSettingOption(key = values[i], label = names[i])
        }
    }

    /**
     * Same choice source as SubtitlesFragment auto-select dialog:
     * [languages] IETF tags + None. Not a hard-coded tiny list.
     *
     * None is stored as empty string — the value GeneratorPlayer writes when the user
     * turns subtitles off, and the value `!langCode.isNullOrEmpty()` treats as no auto-select.
     */
    private fun autoSelectLanguageOptions(): List<TvSettingOption> {
        val none = TvSettingOption(
            key = AUTO_SELECT_NONE_KEY,
            label = context.getString(R.string.none),
        )
        val langs = languages
            .map { lang ->
                TvSettingOption(
                    key = lang.IETF_tag,
                    label = lang.nameNextToFlagEmoji(),
                )
            }
            .sortedBy { it.label.substringAfter("\u00a0").lowercase() }
        return listOf(none) + langs
    }

    private fun autoSelectSelectedKey(): String {
        val persisted = getAutoSelectLanguageTagIETF()
        return if (isNoneAutoSelect(persisted)) AUTO_SELECT_NONE_KEY else persisted
    }

    private fun isNoneAutoSelect(value: String): Boolean {
        if (value.isEmpty()) return true
        if (value.equals("None", ignoreCase = true)) return true
        return value.equals(context.getString(R.string.none), ignoreCase = true)
    }

    private fun seekSecondOptions(): List<TvSettingOption> {
        // Discrete TV choices instead of a phone SeekBar — same int key written to AppSettings.
        val seconds = listOf(5, 10, 15, 20, 25, 30, 45, 60)
        return seconds.map { s -> TvSettingOption(key = s.toString(), label = "${s}s") }
    }

    private fun nearestSeekKey(current: Int): String {
        val options = listOf(5, 10, 15, 20, 25, 30, 45, 60)
        val nearest = options.minByOrNull { kotlin.math.abs(it - current) } ?: current
        return nearest.toString()
    }

    companion object {
        const val ID_AUTOPLAY = "playback.autoplay"
        const val ID_SKIP_OP = "playback.skip_op"
        const val ID_EPISODE_SYNC = "playback.episode_sync"
        const val ID_START_PAUSED = "playback.start_paused"
        const val ID_SPEED = "playback.speed"
        const val ID_SOFTWARE_DECODING = "playback.software_decoding"
        const val ID_TV_SEEK_ON = "playback.tv_seek_on"
        const val ID_TV_SEEK_OFF = "playback.tv_seek_off"
        const val ID_SHOW_NAME = "playback.show_name"
        const val ID_SHOW_RESOLUTION = "playback.show_resolution"
        const val ID_SHOW_MEDIA_INFO = "playback.show_media_info"

        const val ID_SUB_AUTO_SELECT = "subtitles.auto_select"
        const val ID_SUB_ENCODING = "subtitles.encoding"
        const val ID_SUB_FILTER_LANG = "subtitles.filter_lang"

        /** GeneratorPlayer none-value: empty IETF tag. */
        const val AUTO_SELECT_NONE_KEY = ""

        const val EFFECT_NEXT_PLAYBACK = "Applies on next playback"

        const val ID_TRAILERS = "appearance.trailers"
        const val ID_KITSU = "appearance.kitsu"
        const val ID_CAST = "appearance.cast"
        const val ID_FILLERS = "appearance.fillers"
        const val ID_CLOCK = "appearance.clock"
        const val ID_METADATA_OVERLAY = "appearance.metadata_overlay"
        const val ID_CONFIRM_EXIT = "appearance.confirm_exit"

        const val ID_LOCALE = "language.locale"

        const val ID_PARALLEL_DOWNLOADS = "downloads.parallel"
        const val ID_CONCURRENT_CONNECTIONS = "downloads.concurrent"

        const val ID_ACCOUNT_DISPLAY = "app.account_display"
        const val ID_SKIP_ACCOUNT = "app.skip_account"
        const val ID_AUTO_UPDATE = "app.auto_update"
        const val ID_DNS = "app.dns"
        const val ID_JSDELIVR = "app.jsdelivr"
        const val ID_FOCUS_PROBE = "app.focus_probe"
    }
}

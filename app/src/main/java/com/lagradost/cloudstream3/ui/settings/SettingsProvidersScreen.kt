package com.lagradost.cloudstream3.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.SubtitleHelper.getNameNextToFlagEmoji
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream4.rememberAppSettings
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import kotlinx.collections.immutable.persistentListOf

class SettingsProvidersScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.category_providers)

    fun TvType.toStringRes() = when (this) {
        TvType.TvSeries -> R.string.tv_series_singular
        TvType.Anime -> R.string.anime_singular
        TvType.OVA -> R.string.ova_singular
        TvType.AnimeMovie -> R.string.movies_singular
        TvType.Cartoon -> R.string.cartoons_singular
        TvType.Documentary -> R.string.documentaries_singular
        TvType.Movie -> R.string.movies_singular
        TvType.Torrent -> R.string.torrent_singular
        TvType.AsianDrama -> R.string.asian_drama_singular
        TvType.Live -> R.string.live_singular
        TvType.Others -> R.string.other_singular
        TvType.NSFW -> R.string.nsfw_singular
        TvType.Music -> R.string.music_singular
        TvType.AudioBook -> R.string.audio_book_singular
        TvType.CustomMedia -> R.string.custom_media_singular
        TvType.Audio -> R.string.audio_singular
        TvType.Podcast -> R.string.podcast_singular
        TvType.Video -> R.string.video_singular
    }

    // TODO Move this into general and layout?
    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()

        val default = AllLanguagesName to stringResource(R.string.all_languages_preference)
        val languages = APIHolder.apis.withLock {
            APIHolder.apis.map { api -> api.lang }.distinct()
        }

        return persistentListOf(
            Preference.PreferenceItem.MultiSelectListPreference(
            title = stringResource(R.string.provider_lang_settings),
            icon = painterResource(R.drawable.plugin_lang),
            entries = mapOf(default) + languages.associateWith { lang ->
                (getNameNextToFlagEmoji(
                    lang
                ) ?: lang)
            },
            preference = settings.provider.extensionLanguages
        ), Preference.PreferenceItem.MultiSelectListPreference(
            title = stringResource(R.string.preferred_media_settings),
            icon = painterResource(R.drawable.movie_edit_24px),
            preference = settings.provider.preferredMedia,
            entries = TvType.entries.associate {
                    it.ordinal.toString() to stringResource(it.toStringRes())
                }), Preference.PreferenceItem.MultiSelectListPreference(
            title = stringResource(R.string.display_subbed_dubbed_settings),
            icon = painterResource(R.drawable.ic_outline_voice_over_off_24),
            preference = settings.provider.displayDubSub,
            entries = mapOf(
                DubStatus.None.name to stringResource(R.string.none),
                DubStatus.Dubbed.name to stringResource(R.string.app_dubbed_text),
                DubStatus.Subbed.name to stringResource(R.string.app_subbed_text),
            )
        ), Preference.PreferenceItem.TextPreference(
            title = stringResource(R.string.test_extensions),
            subtitle = stringResource(R.string.test_extensions_summary),
            icon = painterResource(R.drawable.baseline_network_ping_24),
            onClick = {
                activity?.navigate(R.id.navigation_test_providers)
            }))
    }
}
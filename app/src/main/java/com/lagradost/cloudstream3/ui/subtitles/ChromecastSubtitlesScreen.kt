package com.lagradost.cloudstream3.ui.subtitles

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DEPRESSED
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DROP_SHADOW
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_NONE
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_OUTLINE
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_RAISED
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment.Companion.applyStyleEvent
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment.Companion.chromeCastSubtitleState
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment.Companion.defaultChromeCastSubtitleState
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment.Companion.saveStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesScreen.SetUpUndoBar
import com.mihon.common.preference.StatePreferenceStore
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.math.roundToInt

object ChromecastSubtitlesScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.chromecast_subtitles_settings)

    // For ux purposes we also want to show a diff of what will be reset
    private val diff = arrayOf<Pair<Int, (SaveChromeCaptionStyle) -> Any?>>(
        R.string.subs_font to { style -> style.fontFamily },
        R.string.subs_text_color to { state -> state.foregroundColor },
        R.string.subs_font_size to { state -> state.fontScale },
        R.string.subs_edge_type to { state -> state.edgeType },
        R.string.subs_outline_color to { state -> state.edgeColor },
        R.string.subs_window_color to { state -> state.windowColor },
        R.string.subs_background_color to { state -> state.backgroundColor },
    )

    @Composable
    override fun RowScope.AppBarAction() {
        val initialState = remember { chromeCastSubtitleState.value }
        var state by chromeCastSubtitleState
        val context = LocalContext.current

        SideEffect(state) {
            // When we modify the state, we modify the stored data
            context.saveStyle(state)
        }

        DisposableEffect(Unit) {
            onDispose {
                if (initialState != state) {
                    applyStyleEvent.invoke(state)
                }
            }
        }
        SetUpUndoBar(
            diff = diff,
            initialState = initialState,
            currentState = chromeCastSubtitleState,
            default = defaultChromeCastSubtitleState
        )
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val store = StatePreferenceStore(chromeCastSubtitleState)

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.subs_font),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = store.field(SaveChromeCaptionStyle::fontFamily) { newValue ->
                            copy(fontFamily = newValue)
                        },
                        icon = painterResource(R.drawable.font_download_24px),
                        title = stringResource(R.string.subs_font),
                        entries = persistentMapOf(
                            null to stringResource(R.string.normal),
                            "Droid Sans" to "Droid Sans",
                            "Droid Sans Mono" to "Droid Sans Mono",
                            "Droid Serif Regular" to "Droid Serif Regular",
                            "Cutive Mono" to "Cutive Mono",
                            "Short Stack" to "Short Stack",
                            "Quintessential" to "Quintessential",
                            "Alegreya Sans SC" to "Alegreya Sans SC",
                        )
                    ),
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveChromeCaptionStyle::foregroundColor) { newValue ->
                            copy(foregroundColor = newValue)
                        },
                        title = stringResource(R.string.subs_text_color),
                        icon = painterResource(R.drawable.format_color_text_24px),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = store.field(
                            get = { fontScale.times(100.0f).roundToInt() },
                            set = { newValue -> copy(fontScale = newValue.toFloat() * 0.01f) }),
                        icon = painterResource(R.drawable.text_fields_24px),
                        title = stringResource(R.string.subs_font_size),
                        valueRange = 75..150,
                        steps = 75 / 5 - 1,
                    )
                )
            ), Preference.PreferenceGroup(
                title = stringResource(R.string.subs_edge), preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = store.field(SaveChromeCaptionStyle::edgeType) { newValue ->
                            copy(edgeType = newValue)
                        },
                        icon = painterResource(R.drawable.shadow_24px),
                        title = stringResource(R.string.subs_edge_type),
                        entries = persistentMapOf(
                            EDGE_TYPE_NONE to stringResource(R.string.subtitles_none),
                            EDGE_TYPE_OUTLINE to stringResource(R.string.subtitles_outline),
                            EDGE_TYPE_DEPRESSED to stringResource(R.string.subtitles_depressed),
                            EDGE_TYPE_DROP_SHADOW to stringResource(R.string.subtitles_shadow),
                            EDGE_TYPE_RAISED to stringResource(R.string.subtitles_raised),
                        )
                    ),
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveChromeCaptionStyle::edgeColor) { newValue ->
                            copy(edgeColor = newValue)
                        },
                        //enabled = state.edgeType != EDGE_TYPE_NONE,
                        title = stringResource(R.string.subs_outline_color),
                        icon = painterResource(R.drawable.border_color_24px),
                    ),
                )
            ), Preference.PreferenceGroup(
                title = stringResource(R.string.background), preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveChromeCaptionStyle::windowColor) { newValue ->
                            copy(windowColor = newValue)
                        },
                        title = stringResource(R.string.subs_window_color),
                        icon = painterResource(R.drawable.imagesearch_roller_24px),
                    ),
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveChromeCaptionStyle::backgroundColor) { newValue ->
                            copy(backgroundColor = newValue)
                        },
                        title = stringResource(R.string.subs_background_color),
                        icon = painterResource(R.drawable.format_color_fill_24px),
                    ),
                )
            )
        )
    }
}
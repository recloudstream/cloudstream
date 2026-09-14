package com.lagradost.cloudstream3.ui.subtitles

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        SetUpUndoBar(
            diff = diff,
            initialState = initialState,
            currentState = chromeCastSubtitleState,
            default = defaultChromeCastSubtitleState
        )
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val initialState = remember { chromeCastSubtitleState.value }
        var state by remember { chromeCastSubtitleState }

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
        return getPreferences(state) { updater ->
            state = updater(state)
        }
    }

    /** Unfortunately this causes a lot of re-compositions, but what can you do? */
    @Composable
    fun getPreferences(
        state : SaveChromeCaptionStyle,
        update : (SaveChromeCaptionStyle.() -> SaveChromeCaptionStyle) -> Unit
    ): List<Preference> {
        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(R.string.subs_font),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.BasicListPreference(
                        icon = painterResource(R.drawable.font_download_24px),
                        title = stringResource(R.string.subs_font),
                        value = state.fontFamily,
                        entries = persistentMapOf(
                            null to stringResource(R.string.normal),
                            "Droid Sans" to "Droid Sans",
                            "Droid Sans Mono" to "Droid Sans Mono",
                            "Droid Serif Regular" to "Droid Serif Regular",
                            "Cutive Mono" to "Cutive Mono",
                            "Short Stack" to "Short Stack",
                            "Quintessential" to "Quintessential",
                            "Alegreya Sans SC" to "Alegreya Sans SC",
                        ),
                        onValueChanged = { newValue ->
                            update { copy(fontFamily = newValue) }
                        }),
                    Preference.PreferenceItem.BasicColorPreference(
                        title = stringResource(R.string.subs_text_color),
                        value = Color(state.foregroundColor),
                        icon = painterResource(R.drawable.format_color_text_24px),
                        onValueChanged = { newValue ->
                            update { copy(foregroundColor = newValue.toArgb()) }
                        }
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = state.fontScale.times(100.0f).roundToInt(),
                        icon = painterResource(R.drawable.text_fields_24px),
                        title = stringResource(R.string.subs_font_size),
                        valueRange = 75..150,
                        steps = 75/5 - 1,
                        onValueChanged = { newValue ->
                            update { copy(fontScale = newValue.toFloat() * 0.01f) }
                        })
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.subs_edge),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.BasicListPreference(
                        icon = painterResource(R.drawable.shadow_24px),
                        title = stringResource(R.string.subs_edge_type),
                        value = state.edgeType,
                        entries = persistentMapOf(
                            EDGE_TYPE_NONE to stringResource(R.string.subtitles_none),
                            EDGE_TYPE_OUTLINE to stringResource(R.string.subtitles_outline),
                            EDGE_TYPE_DEPRESSED to stringResource(R.string.subtitles_depressed),
                            EDGE_TYPE_DROP_SHADOW to stringResource(R.string.subtitles_shadow),
                            EDGE_TYPE_RAISED to stringResource(R.string.subtitles_raised),
                        ),
                        onValueChanged = { newValue ->
                            update { copy(edgeType = newValue) }
                        }),
                    Preference.PreferenceItem.BasicColorPreference(
                        enabled = state.edgeType != EDGE_TYPE_NONE,
                        title = stringResource(R.string.subs_outline_color),
                        value = Color(state.edgeColor),
                        icon = painterResource(R.drawable.border_color_24px),
                        onValueChanged = { newValue ->
                            update { copy(edgeColor = newValue.toArgb()) }
                        }
                    ),
                )),
            Preference.PreferenceGroup(
                title = stringResource(R.string.background),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.BasicColorPreference(
                        title = stringResource(R.string.subs_window_color),
                        value = Color(state.windowColor),
                        icon = painterResource(R.drawable.imagesearch_roller_24px),
                        onValueChanged = { newValue ->
                            update { copy(windowColor = newValue.toArgb()) }
                        }
                    ),
                    Preference.PreferenceItem.BasicColorPreference(
                        title = stringResource(R.string.subs_background_color),
                        value = Color(state.backgroundColor),
                        icon = painterResource(R.drawable.format_color_fill_24px),
                        onValueChanged = { newValue ->
                            update { copy(backgroundColor = newValue.toArgb()) }
                        }
                    ),
                ))
        )
    }
}
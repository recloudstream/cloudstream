package com.lagradost.cloudstream3.ui.subtitles

import com.mihon.common.preference.StatePreferenceStore
import android.text.SpannableString
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.CustomDecoder
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyleEvent
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.defaultSubtitleStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.saveStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.setSubtitleViewStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.subtitleStyleState
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral
import com.lagradost.cloudstream3.utils.SubtitleHelper.languages
import com.lagradost.cloudstream4.compose.ActionDialog
import com.mihon.common.preference.AndroidPreferenceStore
import com.mihon.common.preference.DataPreferenceStore
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

object SubtitlesScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.subtitles_settings)

    // For ux purposes we also want to show a diff of what will be reset
    private val diff = arrayOf<Pair<Int, (SaveCaptionStyle) -> Any?>>(
        R.string.subs_font to { style -> style.font },
        R.string.subs_text_color to { state -> state.foregroundColor },
        R.string.subs_font_size to { state -> state.fixedTextSize },
        R.string.uppercase_all_subtitles to { state -> state.upperCase },
        R.string.all_subtitles_bold to { state -> state.bold },
        R.string.all_subtitles_italic to { state -> state.italic },
        R.string.subs_edge_type to { state -> state.edgeType },
        R.string.subs_outline_color to { state -> state.edgeColor },
        R.string.subs_edge_size to { state -> state.edgeSize },
        R.string.subs_window_color to { state -> state.windowColor },
        R.string.subs_background_color to { state -> state.backgroundColor },
        R.string.background_radius to { state -> state.backgroundRadius },
        R.string.subs_subtitle_alignment to { state -> state.alignment },
        R.string.subs_subtitle_elevation to { state -> state.elevation },
        R.string.subtitles_remove_captions to { state -> state.removeCaptions },
        R.string.subtitles_remove_bloat to { state -> state.removeBloat },
    )

    @Composable
    fun <T> SetUpUndoBar(
        diff: Array<Pair<Int, (T) -> Any?>>,
        initialState: T,
        currentState: MutableState<T>,
        default: T
    ) {
        var currentState by currentState
        var dialogShown by remember { mutableStateOf(false) }
        if (dialogShown) {
            ActionDialog(
                title = stringResource(R.string.subs_default_reset_toast),
                // This can not be converted to joinToString due to stringResource being composable
                text =
                    @Suppress("SimplifiableCallChain")
                    diff.filter { (_, f) -> f(default) != f(currentState) }
                    .map { (name, _) -> stringResource(name) }.joinToString(separator = "\n"),
                confirmText = stringResource(R.string.reset_btn),
                dismissText = stringResource(R.string.dismiss),
                confirm = {
                    currentState = default
                    dialogShown = false
                },
                dismiss = {
                    dialogShown = false
                })
        }

        // Undo, current -> initial last saved value
        AnimatedVisibility(currentState != initialState) {
            IconButton(onClick = {
                currentState = initialState
            }) {
                Icon(
                    painter = painterResource(R.drawable.undo_24px),
                    contentDescription = stringResource(R.string.reset_btn),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Reset, current -> default
        AnimatedVisibility(currentState != default) {
            IconButton(onClick = {
                dialogShown = true
            }) {
                Icon(
                    painter = painterResource(R.drawable.reset_colors_24px),
                    contentDescription = stringResource(R.string.subs_default_reset_toast),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }

    @Composable
    override fun RowScope.AppBarAction() {
        val initialState = remember { subtitleStyleState.value }
        var state by subtitleStyleState
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
            currentState = subtitleStyleState,
            default = defaultSubtitleStyle
        )
    }

    @OptIn(UnstableApi::class)
    @Composable
    override fun getPreferences(): List<Preference> {
        val store = StatePreferenceStore(subtitleStyleState)
        val dataStore = DataPreferenceStore(LocalContext.current)
        val androidStore = AndroidPreferenceStore(LocalContext.current)

        val subtitlesFilterSubLang = androidStore.getBoolean("filter_sub_lang_key", false)
        val autoSelectSubtitles = dataStore.getString(SUBTITLE_AUTO_SELECT_KEY, "en")
        val downloadSubsLanguage =
            dataStore.getObjectFromString(SUBTITLE_DOWNLOAD_KEY, setOf("en"), serializer = { obj ->
                obj.toList().toJsonLiteral()
            }, deserializer = { json ->
                parseJson<List<String>>(json).toSet()
            })

        return persistentListOf(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(R.string.skip_type_preview), content = {
                    val state by store.state
                    val text = stringResource(R.string.subtitles_example_text)
                    val fixedText =
                        SpannableString.valueOf(if (state.upperCase) text.uppercase() else text)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(75.dp)
                    ) {
                        AsyncImage(
                            contentDescription = stringResource(R.string.preview_background_img_des),
                            model = R.drawable.subtitles_preview_background,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                            SubtitleView(context).apply {
                                setSubtitleViewStyle(this, state, false)
                                this.setCues(
                                    listOf(
                                        Cue.Builder().setText(fixedText).applyStyle(state).build()
                                    )
                                )
                            }
                        }, update = { view ->
                            setSubtitleViewStyle(view, state, false)
                            view.setCues(
                                listOf(
                                    Cue.Builder().setText(fixedText).applyStyle(state).build()
                                )
                            )
                        })
                    }
                }),
            Preference.PreferenceGroup(
                title = stringResource(R.string.subs_font),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = store.field(SaveCaptionStyle::font) { newValue -> copy(font = newValue) },
                        icon = painterResource(R.drawable.font_download_24px),
                        title = stringResource(R.string.subs_font),
                        entries = mapOf(null to stringResource(R.string.normal)) + SubtitleFont.entries.associateWith { it.label },
                    ),
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveCaptionStyle::foregroundColor) { newValue ->
                            copy(foregroundColor = newValue)
                        },
                        title = stringResource(R.string.subs_text_color),
                        icon = painterResource(R.drawable.format_color_text_24px),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = store.field(
                            get = { fixedTextSize?.toInt() ?: 25 },
                            set = { newValue -> copy(fixedTextSize = newValue.toFloat()) }
                        ),
                        icon = painterResource(R.drawable.text_fields_24px),
                        title = stringResource(R.string.subs_font_size),
                        valueRange = 5..60
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = store.field(SaveCaptionStyle::upperCase) { newValue ->
                            copy(upperCase = newValue)
                        },
                        icon = painterResource(R.drawable.uppercase_24px),
                        title = stringResource(R.string.uppercase_all_subtitles),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = store.field(SaveCaptionStyle::bold) { newValue -> copy(bold = newValue) },
                        icon = painterResource(R.drawable.format_bold_24px),
                        title = stringResource(R.string.all_subtitles_bold),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = store.field(SaveCaptionStyle::italic) { newValue -> copy(italic = newValue) },
                        icon = painterResource(R.drawable.format_italic_24px),
                        title = stringResource(R.string.all_subtitles_italic),
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.subs_edge),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        icon = painterResource(R.drawable.shadow_24px),
                        title = stringResource(R.string.subs_edge_type),
                        preference = store.field(SaveCaptionStyle::edgeType) { newValue ->
                            copy(edgeType = newValue)
                        },
                        entries = persistentMapOf(
                            CaptionStyleCompat.EDGE_TYPE_NONE to stringResource(R.string.subtitles_none),
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE to stringResource(R.string.subtitles_outline),
                            CaptionStyleCompat.EDGE_TYPE_DEPRESSED to stringResource(R.string.subtitles_depressed),
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to stringResource(R.string.subtitles_shadow),
                            CaptionStyleCompat.EDGE_TYPE_RAISED to stringResource(R.string.subtitles_raised),
                        )
                    ),
                    Preference.PreferenceItem.ColorPreference(
                        //enabled = state.edgeType != CaptionStyleCompat.EDGE_TYPE_NONE,
                        preference = store.field(SaveCaptionStyle::edgeColor) { newValue ->
                            copy(edgeColor = newValue)
                        },
                        title = stringResource(R.string.subs_outline_color),
                        icon = painterResource(R.drawable.border_color_24px),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = store.field(
                            get = { edgeSize?.toInt() ?: 0 },
                            set = { newValue -> copy(edgeSize = newValue.toFloat()) }
                        ),
                        //enabled = state.edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        icon = painterResource(R.drawable.shadow_add_24px),
                        title = stringResource(R.string.subs_edge_size),
                        valueRange = 0..60,
                    )
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.background),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveCaptionStyle::windowColor) { newValue ->
                            copy(windowColor = newValue)
                        },
                        title = stringResource(R.string.subs_window_color),
                        icon = painterResource(R.drawable.imagesearch_roller_24px),
                    ),
                    Preference.PreferenceItem.ColorPreference(
                        preference = store.field(SaveCaptionStyle::backgroundColor) { newValue ->
                            copy(backgroundColor = newValue)
                        },
                        title = stringResource(R.string.subs_background_color),
                        icon = painterResource(R.drawable.format_color_fill_24px),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = store.field(
                            get = { backgroundRadius?.toInt() ?: 0 },
                            set = { newValue -> copy(backgroundRadius = newValue.toFloat()) }
                        ),
                        icon = painterResource(R.drawable.rounded_corner_24px),
                        title = stringResource(R.string.background_radius),
                        valueRange = 0..50,
                        steps = 9,
                    )
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_player_layout),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = store.field(SaveCaptionStyle::alignment) { newValue ->
                            copy(alignment = newValue)
                        },

                        icon = painterResource(R.drawable.format_align_center_24px),
                        title = stringResource(R.string.subs_subtitle_alignment),
                        entries = persistentMapOf(
                            null to stringResource(R.string.automatic),
                            CustomDecoder.SSA_ALIGNMENT_BOTTOM_LEFT to stringResource(R.string.bottom_left),
                            CustomDecoder.SSA_ALIGNMENT_BOTTOM_CENTER to stringResource(R.string.bottom_center),
                            CustomDecoder.SSA_ALIGNMENT_BOTTOM_RIGHT to stringResource(R.string.bottom_right),
                            CustomDecoder.SSA_ALIGNMENT_MIDDLE_LEFT to stringResource(R.string.middle_left),
                            CustomDecoder.SSA_ALIGNMENT_MIDDLE_CENTER to stringResource(R.string.middle_center),
                            CustomDecoder.SSA_ALIGNMENT_MIDDLE_RIGHT to stringResource(R.string.middle_right),
                            CustomDecoder.SSA_ALIGNMENT_TOP_LEFT to stringResource(R.string.top_left),
                            CustomDecoder.SSA_ALIGNMENT_TOP_CENTER to stringResource(R.string.top_center),
                            CustomDecoder.SSA_ALIGNMENT_TOP_RIGHT to stringResource(R.string.top_right),
                        )
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = store.field(SaveCaptionStyle::elevation) { newValue ->
                            copy(elevation = newValue)
                        },
                        title = stringResource(R.string.subs_subtitle_elevation),
                        icon = painterResource(R.drawable.text_select_move_up_24px),
                        valueRange = 0..400,
                        steps = 39
                    ),
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.extension_language),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        icon = painterResource(R.drawable.language_korean_latin_24px),
                        title = stringResource(R.string.subs_auto_select_language),
                        preference = autoSelectSubtitles,
                        entries = mapOf(
                            "None" to stringResource(R.string.none)
                        ) + languages.map { it.IETF_tag to it.nameNextToFlagEmoji() }.sortedBy {
                            it.second.substringAfter("\u00a0").lowercase()
                        }.toMap(),
                    ),
                    Preference.PreferenceItem.MultiSelectListPreference(
                        icon = painterResource(R.drawable.language_download2),
                        title = stringResource(R.string.subs_download_languages),
                        preference = downloadSubsLanguage,
                        entries = mapOf(
                            "None" to stringResource(R.string.none)
                        ) + languages.map { it.IETF_tag to it.nameNextToFlagEmoji() }.sortedBy {
                            it.second.substringAfter("\u00a0").lowercase()
                        }.toMap(),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = subtitlesFilterSubLang,
                        icon = painterResource(R.drawable.filter_alt_24px),
                        title = stringResource(R.string.subtitles_filter_lang),
                    ),
                )
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_ui_features),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = store.field(SaveCaptionStyle::removeCaptions) { newValue ->
                            copy(removeCaptions = newValue)
                        },
                        icon = painterResource(R.drawable.closed_caption_24px),
                        title = stringResource(R.string.subtitles_remove_captions),
                        subtitle = "[Knocking on door] Hello → Hello",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = store.field(SaveCaptionStyle::removeBloat) { newValue ->
                            copy(removeBloat = newValue)
                        },
                        icon = painterResource(R.drawable.text_ad_off_24px),
                        title = stringResource(R.string.subtitles_remove_bloat),
                    ),
                )
            ),
        )
    }
}
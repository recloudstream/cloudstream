package com.lagradost.cloudstream3.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.FontRes
import androidx.annotation.OptIn
import androidx.annotation.Px
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.SubtitleSettingsBinding
import com.lagradost.cloudstream3.ui.BaseDialogFragment
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.player.CustomDecoder
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.setSubtitleAlignment
import com.lagradost.cloudstream3.ui.player.OutlineSpan
import com.lagradost.cloudstream3.ui.player.RoundedBackgroundColorSpan
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.languages
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

const val SUBTITLE_KEY = "subtitle_settings"
const val SUBTITLE_AUTO_SELECT_KEY = "subs_auto_select"
const val SUBTITLE_DOWNLOAD_KEY = "subs_auto_download"

@Serializable
enum class SubtitleFont(
    @FontRes val resource: Int,
    val label: String
) {
    @JsonProperty("Trebuchet")
    @SerialName("Trebuchet")
    Trebuchet(R.font.trebuchet_ms, "Trebuchet MS"),
    @JsonProperty("Netflix")
    @SerialName("Netflix")
    Netflix(R.font.netflix_sans, "Netflix Sans"),
    @JsonProperty("Google")
    @SerialName("Google")
    Google(R.font.google_sans, "Google Sans"),
    @JsonProperty("Open")
    @SerialName("Open")
    Open(R.font.open_sans, "Open Sans"),
    @JsonProperty("Futura")
    @SerialName("Futura")
    Futura(R.font.futura, "Futura"),
    @JsonProperty("Consola")
    @SerialName("Consola")
    Consola(R.font.consola, "Consola"),
    @JsonProperty("Gotham")
    @SerialName("Gotham")
    Gotham(R.font.gotham, "Gotham"),
    @JsonProperty("Lucida")
    @SerialName("Lucida")
    Lucida(R.font.lucida_grande, "Lucida Grande"),
    @JsonProperty("STIX")
    @SerialName("STIX")
    STIX(R.font.stix_general, "STIX General"),
    @JsonProperty("TimesNewRoman")
    @SerialName("TimesNewRoman")
    TimesNewRoman(R.font.times_new_roman, "Times New Roman"),
    @JsonProperty("Verdana")
    @SerialName("Verdana")
    Verdana(R.font.verdana, "Verdana"),
    @JsonProperty("Ubuntu")
    @SerialName("Ubuntu")
    Ubuntu(R.font.ubuntu_regular, "Ubuntu"),
    @JsonProperty("Comic")
    @SerialName("Comic")
    Comic(R.font.comic_sans, "Comic Sans"),
    @JsonProperty("Poppins")
    @SerialName("Poppins")
    Poppins(R.font.poppins_regular, "Poppins"),
}

@Serializable
@Immutable
data class SaveCaptionStyle(
    @JsonProperty("foregroundColor") @SerialName("foregroundColor") val foregroundColor: Int,
    @JsonProperty("backgroundColor") @SerialName("backgroundColor") val backgroundColor: Int,
    @JsonProperty("windowColor") @SerialName("windowColor") val windowColor: Int,
    @OptIn(UnstableApi::class)
    @JsonProperty("edgeType") @SerialName("edgeType") val edgeType: @CaptionStyleCompat.EdgeType Int,
    @JsonProperty("edgeColor") @SerialName("edgeColor") val edgeColor: Int,
    @JsonProperty("font") @SerialName("font") val font: SubtitleFont? = null,
    @JsonProperty("typefaceFilePath") @SerialName("typefaceFilePath") val typefaceFilePath: String?,
    @JsonProperty("elevation") @SerialName("elevation") val elevation: Int, // in dp
    @JsonProperty("fixedTextSize") @SerialName("fixedTextSize") val fixedTextSize: Float?, // in sp
    @Px @JsonProperty("edgeSize") @SerialName("edgeSize") val edgeSize: Float? = null,
    @JsonProperty("removeCaptions") @SerialName("removeCaptions") val removeCaptions: Boolean = false,
    @JsonProperty("removeBloat") @SerialName("removeBloat") val removeBloat: Boolean = true,
    /** Apply caps lock to the text */
    @JsonProperty("upperCase") @SerialName("upperCase") val upperCase: Boolean = false,
    /** Apply bold to the text */
    @JsonProperty("bold") @SerialName("bold") val bold: Boolean = false,
    /** Apply italic to the text */
    @JsonProperty("italic") @SerialName("italic") val italic: Boolean = false,
    /** in px, background radius, aka how round the background (backgroundColor) on each row is */
    @JsonProperty("backgroundRadius") @SerialName("backgroundRadius") val backgroundRadius: Float? = null,
    /** The SSA_ALIGNMENT */
    @JsonProperty("alignment") @SerialName("alignment") val alignment: Int? = null,
)

const val DEF_SUBS_ELEVATION = 20

@OptIn(UnstableApi::class)
class SubtitlesFragment : BaseDialogFragment<SubtitleSettingsBinding>(
    BaseFragment.BindingCreator.Inflate(SubtitleSettingsBinding::inflate)
) {
    companion object {
        val applyStyleEvent = Event<SaveCaptionStyle>()
        private val captionRegex = Regex("""(-\s?|)[\[({][\S\s]*?[])}]\s*""")

        fun setSubtitleViewStyle(
            view: SubtitleView?,
            data: SaveCaptionStyle,
            applyElevation: Boolean
        ) {
            if (view == null) return
            val ctx = view.context ?: return
            val style = ctx.fromSaveToStyle(data)
            view.setStyle(style)

            if (applyElevation) {
                view.setPadding(
                    view.paddingLeft, data.elevation.toPx, view.paddingRight, view.paddingBottom
                )
            }

            view.clipToPadding = false
            view.clipChildren = false

            // we default to 25sp, this is needed as RoundedBackgroundColorSpan breaks on override sizes
            val size = data.fixedTextSize ?: 25.0f
            view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            view.setBottomPaddingFraction(0.0f)
            /*if (size != null) {
                view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            } else {
                view.setUserDefaultTextSize()
            }*/
        }

        fun Cue.Builder.applyStyle(style: SaveCaptionStyle): Cue.Builder {
            val edgeSize = style.edgeSize ?: 0.0f

            /*
            This is old code for only applying on non null

            val fixedFontSize = style.fixedTextSize
            val absoluteFontSize =
                fixedFontSize?.let { getPixels(TypedValue.COMPLEX_UNIT_SP, it).toFloat() }

            // 1. apply override size
            if (absoluteFontSize != null) {
                setTextSize(absoluteFontSize, Cue.TEXT_SIZE_TYPE_ABSOLUTE)
            }*/

            // 1. remove any subtitle size set by the subtitle file (like ass)
            // instead we use the inherit size of the subtitle view
            setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)

            text?.let { text ->
                val textSpanBuilder = SpannableStringBuilder(text)

                // 2. apply edge
                if (edgeSize > 0.0f) {
                    textSpanBuilder.setSpan(
                        OutlineSpan(edgeSize), 0, textSpanBuilder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // 3. apply bold + italic
                val typeface = when (style.bold to style.italic) {
                    (true to true) -> Typeface.BOLD_ITALIC
                    (true to false) -> Typeface.BOLD
                    (false to true) -> Typeface.ITALIC
                    (false to false) -> Typeface.NORMAL
                    else -> {
                        Typeface.NORMAL
                    }
                }
                if (typeface != Typeface.NORMAL) {
                    val styleSpan = StyleSpan(typeface)
                    textSpanBuilder.setSpan(
                        styleSpan, 0, textSpanBuilder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // 4. apply radius
                val radius = style.backgroundRadius ?: 0.0f
                if (radius > 0.0f && style.backgroundColor != Color.TRANSPARENT) {
                    val styleSpan = RoundedBackgroundColorSpan(
                        style.backgroundColor,
                        this.textAlignment ?: Layout.Alignment.ALIGN_CENTER,
                        2.0F + radius * 0.5f,
                        radius
                    )
                    textSpanBuilder.setSpan(
                        styleSpan, 0, textSpanBuilder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // 5. remove captions but ensure that styling is kept
                // Just doing .replace removes all styling!
                if (style.removeCaptions) {
                    for (match in captionRegex.findAll(text).toList().asReversed()) {
                        textSpanBuilder.delete(match.range.first, match.range.last + 1)
                    }
                }

                this.setText(textSpanBuilder)
            }

            // 6. set alignment
            return this.setSubtitleAlignment(style.alignment)
        }

        private fun Context.fromSaveToStyle(data: SaveCaptionStyle): CaptionStyleCompat {
            return CaptionStyleCompat(
                data.foregroundColor,
                data.backgroundColor,
                data.windowColor,
                data.edgeType,
                data.edgeColor,
                data.typefaceFilePath?.let {
                    try {
                        // RuntimeException: Font asset not found
                        // Fuck android, they have no good way to load a font from a uri without a copy
                        Typeface.createFromFile(File(it))
                    } catch (_: Exception) {
                        null
                    }
                } ?: data.font?.let { font ->
                    ResourcesCompat.getFont(
                        this,
                        font.resource
                    )
                }
                ?: Typeface.SANS_SERIF
            )
        }

        fun push(activity: Activity?, hide: Boolean = true) {
            activity.navigate(R.id.global_to_navigation_subtitles, Bundle().apply {
                putBoolean("hide", hide)
                putBoolean("popFragment", true)
            })
        }

        private fun getDefColor(id: Int): Int {
            return when (id) {
                0 -> Color.WHITE
                1 -> Color.BLACK
                2 -> Color.TRANSPARENT
                3 -> Color.TRANSPARENT
                else -> Color.TRANSPARENT
            }
        }

        fun Context.saveStyle(style: SaveCaptionStyle) {
            if (subtitleStyleState.value !== style) {
                subtitleStyleState.value = style
            }
            this.setKey(SUBTITLE_KEY, style)
        }

        val defaultSubtitleStyle = SaveCaptionStyle(
            foregroundColor = getDefColor(0),
            backgroundColor = getDefColor(2),
            windowColor = getDefColor(3),
            edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            edgeColor = getDefColor(1),
            font = null,
            typefaceFilePath = null,
            elevation = DEF_SUBS_ELEVATION,
            fixedTextSize = null,
        )
        val subtitleStyleState =
            mutableStateOf((getKey<SaveCaptionStyle>(SUBTITLE_KEY) ?: defaultSubtitleStyle))

        fun getCurrentSavedStyle(): SaveCaptionStyle {
            return subtitleStyleState.value
        }

        private fun Context.getSavedFonts(): List<File> {
            val externalFiles = getExternalFilesDir(null) ?: return emptyList()
            val fontDir = File(externalFiles.absolutePath + "/Fonts").also {
                it.mkdir()
            }
            return fontDir.list()?.mapNotNull {
                // No idea which formats are supported, but these should be.
                if (it.endsWith(".ttf") || it.endsWith(".otf")) {
                    File(fontDir.absolutePath + "/" + it)
                } else null
            } ?: listOf()
        }

        private fun getPixels(unit: Int, size: Float): Int {
            val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
            return TypedValue.applyDimension(unit, size, metrics).toInt()
        }

        fun getDownloadSubsLanguageTagIETF(): List<String> {
            return getKey<List<String>>(SUBTITLE_DOWNLOAD_KEY) ?: listOf("en")
        }

        fun getAutoSelectLanguageTagIETF(): String {
            return getKey<String>(SUBTITLE_AUTO_SELECT_KEY) ?: "en"
        }
    }

    private fun onColorSelected(stuff: Pair<Int, Int>) {
        context?.setColor(stuff.first, stuff.second)
        if (hide)
            activity?.hideSystemUI()
    }

    private fun onDialogDismissed(@Suppress("UNUSED_PARAMETER") id: Int) {
        if (hide)
            activity?.hideSystemUI()
    }

    private fun Context.setColor(id: Int, color: Int?) {
        val realColor = color ?: getDefColor(id)
        when (id) {
            0 -> state = state.copy(foregroundColor = realColor)
            1 -> state = state.copy(edgeColor = realColor)
            2 -> state = state.copy(backgroundColor = realColor)
            3 -> state = state.copy(windowColor = realColor)

            else -> Unit
        }
        updateState()
    }

    private fun Context.updateState() {
        val text = getString(R.string.subtitles_example_text)
        val fixedText = SpannableString.valueOf(if (state.upperCase) text.uppercase() else text)
        setSubtitleViewStyle(binding?.subtitleText, state, false)

        binding?.subtitleText?.setCues(
            listOf(
                Cue.Builder()
                    .setText(fixedText)
                    .applyStyle(state)
                    .build()
            )
        )
    }

    private fun getColor(id: Int): Int {
        val color = when (id) {
            0 -> state.foregroundColor
            1 -> state.edgeColor
            2 -> state.backgroundColor
            3 -> state.windowColor

            else -> Color.TRANSPARENT
        }

        return if (color == Color.TRANSPARENT) Color.BLACK else color
    }

    private lateinit var state: SaveCaptionStyle
    private var hide: Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        onColorSelectedEvent -= ::onColorSelected
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setWindowAnimations(R.style.DialogFullscreenPlayer)
    }

    override fun getTheme(): Int {
        return R.style.DialogFullscreenPlayer
    }

    var systemBarsAddPadding = isLayout(TV or EMULATOR)
    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = systemBarsAddPadding || isLandscape(),
            padLeft = systemBarsAddPadding
        )
    }

    override fun onBindingCreated(binding: SubtitleSettingsBinding) {
        hide = arguments?.getBoolean("hide") ?: true
        val popFragment = arguments?.getBoolean("popFragment") ?: false
        onColorSelectedEvent += ::onColorSelected
        onDialogDismissedEvent += ::onDialogDismissed
        binding.subsImportText.text = getString(R.string.subs_import_text).format(
            context?.getExternalFilesDir(null)?.absolutePath.toString() + "/Fonts"
        )

        state = getCurrentSavedStyle()
        context?.updateState()

        val isTvTrueSettings = isLayout(TV)
        fun View.setFocusableInTv() {
            this.isFocusableInTouchMode = isTvTrueSettings
        }

        fun View.setup(id: Int) {
            setFocusableInTv()

            this.setOnClickListener {
                activity?.let {
                    ColorPickerDialog.newBuilder()
                        .setDialogId(id)
                        .setShowAlphaSlider(true)
                        .setColor(getColor(id))
                        .show(it)
                }
            }

            this.setOnLongClickListener {
                it.context.setColor(id, null)
                showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }
        }
        binding.apply {
            subsTextColor.setup(0)
            subsOutlineColor.setup(1)
            subsBackgroundColor.setup(2)
            subsWindowColor.setup(3)

            val dismissCallback = {
                if (hide)
                    activity?.hideSystemUI()
            }

            subsSubtitleElevation.setFocusableInTv()
            subsSubtitleElevation.setOnClickListener { textView ->
                // tbh this should not be a dialog if it has so many values
                val elevationTypes = listOf(
                    0 to textView.context.getString(R.string.none)
                ) + (1..40).map { x ->
                    val i = x * 10
                    i to "${i}dp"
                }

                //showBottomDialog
                activity?.showDialog(
                    elevationTypes.map { it.second },
                    elevationTypes.map { it.first }.indexOf(state.elevation),
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state = state.copy(elevation = elevationTypes.map { it.first }[index])
                    textView.context.updateState()
                    if (hide)
                        activity?.hideSystemUI()
                }
            }

            subsSubtitleElevation.setOnLongClickListener {
                state = state.copy(elevation = DEF_SUBS_ELEVATION)
                it.context.updateState()
                showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsBackgroundRadius.setFocusableInTv()
            subsBackgroundRadius.setOnClickListener { textView ->
                // tbh this should not be a dialog if it has so many values
                val radiusTypes = listOf(
                    null to textView.context.getString(R.string.none)
                ) + (1..10).map { x ->
                    val i = x * 5
                    i to "${i}px"
                }

                activity?.showDialog(
                    radiusTypes.map { it.second },
                    radiusTypes.map { it.first }.indexOf(state.backgroundRadius?.toInt()),
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state =
                        state.copy(backgroundRadius = radiusTypes.map { it.first }[index]?.toFloat())
                    textView.context.updateState()
                }
            }

            subsBackgroundRadius.setOnLongClickListener {
                state = state.copy(backgroundRadius = null)
                it.context.updateState()
                showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsSubtitleAlignment.setFocusableInTv()
            subsSubtitleAlignment.setOnClickListener { textView ->
                val alignmentTypes = listOf(
                    null to R.string.automatic,
                    CustomDecoder.SSA_ALIGNMENT_BOTTOM_LEFT to R.string.bottom_left,
                    CustomDecoder.SSA_ALIGNMENT_BOTTOM_CENTER to R.string.bottom_center,
                    CustomDecoder.SSA_ALIGNMENT_BOTTOM_RIGHT to R.string.bottom_right,
                    CustomDecoder.SSA_ALIGNMENT_MIDDLE_LEFT to R.string.middle_left,
                    CustomDecoder.SSA_ALIGNMENT_MIDDLE_CENTER to R.string.middle_center,
                    CustomDecoder.SSA_ALIGNMENT_MIDDLE_RIGHT to R.string.middle_right,
                    CustomDecoder.SSA_ALIGNMENT_TOP_LEFT to R.string.top_left,
                    CustomDecoder.SSA_ALIGNMENT_TOP_CENTER to R.string.top_center,
                    CustomDecoder.SSA_ALIGNMENT_TOP_RIGHT to R.string.top_right,
                )

                activity?.showDialog(
                    alignmentTypes.map { textView.context.getString(it.second) },
                    alignmentTypes.map { it.first }.indexOf(state.alignment),
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state = state.copy(alignment = alignmentTypes.map { it.first }[index])
                    textView.context.updateState()
                }
            }

            subsEdgeType.setFocusableInTv()
            subsEdgeType.setOnClickListener { textView ->
                val edgeTypes = listOf(
                    CaptionStyleCompat.EDGE_TYPE_NONE to
                            textView.context.getString(R.string.subtitles_none),
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE to
                            textView.context.getString(R.string.subtitles_outline),
                    CaptionStyleCompat.EDGE_TYPE_DEPRESSED to
                            textView.context.getString(R.string.subtitles_depressed),
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to
                            textView.context.getString(R.string.subtitles_shadow),
                    CaptionStyleCompat.EDGE_TYPE_RAISED to
                            textView.context.getString(R.string.subtitles_raised),
                )

                //showBottomDialog
                activity?.showDialog(
                    edgeTypes.map { it.second },
                    edgeTypes.map { it.first }.indexOf(state.edgeType),
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state = state.copy(edgeType = edgeTypes.map { it.first }[index])
                    textView.context.updateState()
                }
            }

            subsEdgeType.setOnLongClickListener {
                state = state.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE)
                it.context.updateState()
                showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsFontSize.setFocusableInTv()
            subsFontSize.setOnClickListener { textView ->
                val fontSizes = listOf(
                    null to textView.context.getString(R.string.normal),
                ) + (6..60).map { i -> i.toFloat() to "${i}sp" }

                //showBottomDialog
                activity?.showDialog(
                    fontSizes.map { it.second },
                    fontSizes.map { it.first }.indexOf(state.fixedTextSize),
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state = state.copy(fixedTextSize = fontSizes.map { it.first }[index])
                    textView.context.updateState()
                }
            }

            subsEdgeSize.setFocusableInTv()
            subsEdgeSize.setOnClickListener { textView ->
                val fontSizes = listOf(
                    null to textView.context.getString(R.string.normal),
                ) + (1..60).map { i -> i.toFloat() to "${i}px" }

                //showBottomDialog
                activity?.showDialog(
                    fontSizes.map { it.second },
                    fontSizes.map { it.first }.indexOf(state.edgeSize),
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state = state.copy(edgeSize = fontSizes.map { it.first }[index])
                    textView.context.updateState()
                }
            }

            subtitlesRemoveBloat.isChecked = state.removeBloat
            subtitlesRemoveBloat.setOnCheckedChangeListener { _, b ->
                state = state.copy(removeBloat = b)
            }
            subtitlesUppercase.isChecked = state.upperCase
            subtitlesUppercase.setOnCheckedChangeListener { _, b ->
                state = state.copy(upperCase = b)
                context?.updateState()
            }

            subtitlesRemoveCaptions.isChecked = state.removeCaptions
            subtitlesRemoveCaptions.setOnCheckedChangeListener { _, b ->
                state = state.copy(removeCaptions = b)
            }

            subtitlesBold.isChecked = state.bold
            subtitlesBold.setOnCheckedChangeListener { _, b ->
                state = state.copy(bold = b)
                context?.updateState()
            }

            subtitlesItalic.isChecked = state.italic
            subtitlesItalic.setOnCheckedChangeListener { _, b ->
                state = state.copy(italic = b)
                context?.updateState()
            }

            subsFontSize.setOnLongClickListener { _ ->
                state = state.copy(fixedTextSize = null)
                context?.updateState()
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsEdgeSize.setOnLongClickListener { _ ->
                state = state.copy(edgeSize = null)
                context?.updateState()
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            //Fetch current value from preference
            context?.let { ctx ->
                subtitlesFilterSubLang.isChecked =
                    PreferenceManager.getDefaultSharedPreferences(ctx)
                        .getBoolean(getString(R.string.filter_sub_lang_key), false)
            }

            subtitlesFilterSubLang.setOnCheckedChangeListener { _, b ->
                context?.let { ctx ->
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                        putBoolean(getString(R.string.filter_sub_lang_key), b)
                    }
                }
            }

            subsFont.setFocusableInTv()
            subsFont.setOnClickListener { textView ->
                val fontTypes = listOf(
                    null to textView.context.getString(R.string.normal),
                    R.font.trebuchet_ms to "Trebuchet MS",
                    R.font.netflix_sans to "Netflix Sans",
                    R.font.google_sans to "Google Sans",
                    R.font.open_sans to "Open Sans",
                    R.font.futura to "Futura",
                    R.font.consola to "Consola",
                    R.font.gotham to "Gotham",
                    R.font.lucida_grande to "Lucida Grande",
                    R.font.stix_general to "STIX General",
                    R.font.times_new_roman to "Times New Roman",
                    R.font.verdana to "Verdana",
                    R.font.ubuntu_regular to "Ubuntu",
                    R.font.comic_sans to "Comic Sans",
                    R.font.poppins_regular to "Poppins",
                )
                val savedFontTypes = textView.context.getSavedFonts()

                val currentIndex =
                    savedFontTypes.indexOfFirst { it.absolutePath == state.typefaceFilePath }
                        .let { index ->
                            if (index == -1)
                                fontTypes.indexOfFirst { it.first == state.font?.resource }
                            else index + fontTypes.size
                        }

                //showBottomDialog
                activity?.showDialog(
                    fontTypes.map { it.second } + savedFontTypes.map { it.name },
                    currentIndex,
                    (textView as TextView).text.toString(),
                    false,
                    dismissCallback
                ) { index ->
                    state = if (index < fontTypes.size) {
                        state.copy(
                            font = SubtitleFont.entries.firstOrNull { it.resource == fontTypes[index].first },
                            typefaceFilePath = null
                        )
                    } else {
                        state.copy(
                            typefaceFilePath = savedFontTypes[index - fontTypes.size].absolutePath,
                            font = null
                        )
                    }
                    textView.context.updateState()
                }
            }

            subsFont.setOnLongClickListener { textView ->
                state = state.copy(font = null, typefaceFilePath = null)
                textView.context.updateState()
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsAutoSelectLanguage.setFocusableInTv()
            subsAutoSelectLanguage.setOnClickListener { textView ->
                val languagesTagName =
                    listOf(
                        Pair(
                            textView.context.getString(R.string.none),
                            textView.context.getString(R.string.none)
                        )
                    ) +
                            languages
                                .map { Pair(it.IETF_tag, it.nameNextToFlagEmoji()) }
                                .sortedBy {
                                    it.second.substringAfter("\u00a0").lowercase()
                                } // name ignoring flag emoji

                val (langTagsIETF, langNames) = languagesTagName.unzip()

                activity?.showDialog(
                    langNames,
                    langTagsIETF.indexOf(getAutoSelectLanguageTagIETF()),
                    (textView as TextView).text.toString(),
                    true,
                    dismissCallback
                ) { index ->
                    setKey(SUBTITLE_AUTO_SELECT_KEY, langTagsIETF[index])
                }
            }

            subsAutoSelectLanguage.setOnLongClickListener {
                setKey(SUBTITLE_AUTO_SELECT_KEY, "en")
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsDownloadLanguages.setFocusableInTv()
            subsDownloadLanguages.setOnClickListener { textView ->
                val languagesTagName =
                    languages
                        .map { Pair(it.IETF_tag, it.nameNextToFlagEmoji()) }
                        .sortedBy {
                            it.second.substringAfter("\u00a0").lowercase()
                        } // name ignoring flag emoji

                val (langTagsIETF, langNames) = languagesTagName.unzip()

                val selectedLanguages = getDownloadSubsLanguageTagIETF()
                    .map { langTagsIETF.indexOf(it) }
                    .filter { it >= 0 }

                activity?.showMultiDialog(
                    langNames,
                    selectedLanguages,
                    (textView as TextView).text.toString(),
                    dismissCallback
                ) { indexList ->
                    setKey(SUBTITLE_DOWNLOAD_KEY, indexList.map { langTagsIETF[it] }.toList())
                }
            }

            subsDownloadLanguages.setOnLongClickListener {
                setKey(SUBTITLE_DOWNLOAD_KEY, listOf("en"))

                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            cancelBtt.setOnClickListener {
                if (popFragment) {
                    activity?.popCurrentPage()
                } else {
                    dismiss()
                }
            }

            applyBtt.setOnClickListener {
                it.context.saveStyle(state)
                applyStyleEvent.invoke(state)
                if (popFragment) {
                    activity?.popCurrentPage()
                } else {
                    dismiss()
                }
            }
        }
    }
}

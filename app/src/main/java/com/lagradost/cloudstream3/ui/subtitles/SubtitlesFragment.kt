package com.lagradost.cloudstream3.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.FontRes
import androidx.annotation.OptIn
import androidx.annotation.Px
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
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
import com.lagradost.cloudstream3.ui.player.OutlineSpan
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import java.io.File

const val SUBTITLE_KEY = "subtitle_settings"
const val SUBTITLE_AUTO_SELECT_KEY = "subs_auto_select"
const val SUBTITLE_DOWNLOAD_KEY = "subs_auto_download"

data class SaveCaptionStyle @OptIn(UnstableApi::class) constructor(
    @JsonProperty("foregroundColor") var foregroundColor: Int,
    @JsonProperty("backgroundColor") var backgroundColor: Int,
    @JsonProperty("windowColor") var windowColor: Int,
    @JsonProperty("edgeType") var edgeType: @CaptionStyleCompat.EdgeType Int,
    @JsonProperty("edgeColor") var edgeColor: Int,
    @FontRes
    @JsonProperty("typeface") var typeface: Int?,
    @JsonProperty("typefaceFilePath") var typefaceFilePath: String?,
    /**in dp**/
    @JsonProperty("elevation") var elevation: Int,
    /**in sp**/
    @JsonProperty("fixedTextSize") var fixedTextSize: Float?,
    @Px
    @JsonProperty("edgeSize") var edgeSize: Float? = null,
    @JsonProperty("removeCaptions") var removeCaptions: Boolean = false,
    @JsonProperty("removeBloat") var removeBloat: Boolean = true,
    /** Apply caps lock to the text **/
    @JsonProperty("upperCase") var upperCase: Boolean = false,
)

const val DEF_SUBS_ELEVATION = 20

@OptIn(androidx.media3.common.util.UnstableApi::class)
class SubtitlesFragment : DialogFragment() {
    companion object {
        val applyStyleEvent = Event<SaveCaptionStyle>()

        fun setSubtitleViewStyle(view: SubtitleView?, data: SaveCaptionStyle) {
            if (view == null) return
            val ctx = view.context ?: return
            val style = ctx.fromSaveToStyle(data)
            view.setStyle(style)
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
                        Typeface.createFromFile(File(it))
                    } catch (e: Exception) {
                        null
                    }
                } ?: data.typeface?.let {
                    ResourcesCompat.getFont(
                        this,
                        it
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
            this.setKey(SUBTITLE_KEY, style)
        }

        fun getCurrentSavedStyle(): SaveCaptionStyle {
            return getKey(SUBTITLE_KEY) ?: SaveCaptionStyle(
                foregroundColor = getDefColor(0),
                backgroundColor = getDefColor(2),
                windowColor = getDefColor(3),
                edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                edgeColor = getDefColor(1),
                typeface = null,
                typefaceFilePath = null,
                elevation = DEF_SUBS_ELEVATION,
                fixedTextSize = null,
            )
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

        private fun Context.getCurrentStyle(): CaptionStyleCompat {
            return fromSaveToStyle(getCurrentSavedStyle())
        }

        private fun getPixels(unit: Int, size: Float): Int {
            val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
            return TypedValue.applyDimension(unit, size, metrics).toInt()
        }

        fun getDownloadSubsLanguageISO639_1(): List<String> {
            return getKey(SUBTITLE_DOWNLOAD_KEY) ?: listOf("en")
        }

        fun getAutoSelectLanguageISO639_1(): String {
            return getKey(SUBTITLE_AUTO_SELECT_KEY) ?: "en"
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
            0 -> state.foregroundColor = realColor
            1 -> state.edgeColor = realColor
            2 -> state.backgroundColor = realColor
            3 -> state.windowColor = realColor

            else -> Unit
        }
        updateState()
    }

    private fun Context.updateState() {
        val text = getString(R.string.subtitles_example_text)
        val fixedText = SpannableString.valueOf(if (state.upperCase) text.uppercase() else text)

        state.edgeSize?.let { size ->
            fixedText.setSpan(
                OutlineSpan(size),
                0,
                fixedText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        setSubtitleViewStyle(binding?.subtitleText, state)
        binding?.subtitleText?.setCues(
            listOf(
                Cue.Builder()
                    .setTextSize(
                        getPixels(TypedValue.COMPLEX_UNIT_SP, 25.0f).toFloat(),
                        Cue.TEXT_SIZE_TYPE_ABSOLUTE
                    )
                    .setText(fixedText)
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

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    var binding: SubtitleSettingsBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localBinding = SubtitleSettingsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root
        //return inflater.inflate(R.layout.subtitle_settings, container, false)
    }

    private lateinit var state: SaveCaptionStyle
    private var hide: Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        onColorSelectedEvent -= ::onColorSelected
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setWindowAnimations(R.style.DialogFullscreen)
    }

    override fun getTheme(): Int {
        return R.style.DialogFullscreen
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hide = arguments?.getBoolean("hide") ?: true
        val popFragment = arguments?.getBoolean("popFragment") ?: false
        onColorSelectedEvent += ::onColorSelected
        onDialogDismissedEvent += ::onDialogDismissed
        binding?.subsImportText?.text = getString(R.string.subs_import_text).format(
            context?.getExternalFilesDir(null)?.absolutePath.toString() + "/Fonts"
        )

        fixPaddingStatusbar(binding?.subsRoot)

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
        binding?.apply {
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
                ) + (1..30).map { x ->
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
                    state.elevation = elevationTypes.map { it.first }[index]
                    textView.context.updateState()
                    if (hide)
                        activity?.hideSystemUI()
                }
            }

            subsSubtitleElevation.setOnLongClickListener {
                state.elevation = DEF_SUBS_ELEVATION
                it.context.updateState()
                showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
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
                    state.edgeType = edgeTypes.map { it.first }[index]
                    textView.context.updateState()
                }
            }

            subsEdgeType.setOnLongClickListener {
                state.edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE
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
                    state.fixedTextSize = fontSizes.map { it.first }[index]
                    //textView.context.updateState() // font size not changed
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
                    state.edgeSize = fontSizes.map { it.first }[index]
                    textView.context.updateState()
                }
            }

            subtitlesRemoveBloat.isChecked = state.removeBloat
            subtitlesRemoveBloat.setOnCheckedChangeListener { _, b ->
                state.removeBloat = b
            }
            subtitlesUppercase.isChecked = state.upperCase
            subtitlesUppercase.setOnCheckedChangeListener { _, b ->
                state.upperCase = b
                context?.updateState()
            }

            subtitlesRemoveCaptions.isChecked = state.removeCaptions
            subtitlesRemoveCaptions.setOnCheckedChangeListener { _, b ->
                state.removeCaptions = b
            }

            subsFontSize.setOnLongClickListener { _ ->
                state.fixedTextSize = null
                //textView.context.updateState() // font size not changed
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsEdgeSize.setOnLongClickListener { _ ->
                state.edgeSize = null
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
                    PreferenceManager.getDefaultSharedPreferences(ctx)
                        .edit()
                        .putBoolean(getString(R.string.filter_sub_lang_key), b)
                        .apply()
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
                                fontTypes.indexOfFirst { it.first == state.typeface }
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
                    if (index < fontTypes.size) {
                        state.typeface = fontTypes[index].first
                        state.typefaceFilePath = null
                    } else {
                        state.typefaceFilePath = savedFontTypes[index - fontTypes.size].absolutePath
                        state.typeface = null
                    }
                    textView.context.updateState()
                }
            }

            subsFont.setOnLongClickListener { textView ->
                state.typeface = null
                state.typefaceFilePath = null
                textView.context.updateState()
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsAutoSelectLanguage.setFocusableInTv()
            subsAutoSelectLanguage.setOnClickListener { textView ->
                val langMap = arrayListOf(
                    SubtitleHelper.Language639(
                        textView.context.getString(R.string.none),
                        textView.context.getString(R.string.none),
                        "",
                        "",
                        "",
                        "",
                        ""
                    ),
                )
                langMap.addAll(SubtitleHelper.languages)

                val lang639_1 = langMap.map { it.ISO_639_1 }
                activity?.showDialog(
                    langMap.map { it.languageName },
                    lang639_1.indexOf(getAutoSelectLanguageISO639_1()),
                    (textView as TextView).text.toString(),
                    true,
                    dismissCallback
                ) { index ->
                    setKey(SUBTITLE_AUTO_SELECT_KEY, lang639_1[index])
                }
            }

            subsAutoSelectLanguage.setOnLongClickListener {
                setKey(SUBTITLE_AUTO_SELECT_KEY, "en")
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }

            subsDownloadLanguages.setFocusableInTv()
            subsDownloadLanguages.setOnClickListener { textView ->
                val langMap = SubtitleHelper.languages
                val lang639_1 = langMap.map { it.ISO_639_1 }
                val keys = getDownloadSubsLanguageISO639_1()
                val keyMap = keys.map { lang639_1.indexOf(it) }.filter { it >= 0 }

                activity?.showMultiDialog(
                    langMap.map { it.languageName },
                    keyMap,
                    (textView as TextView).text.toString(),
                    dismissCallback
                ) { indexList ->
                    setKey(SUBTITLE_DOWNLOAD_KEY, indexList.map { lang639_1[it] }.toList())
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
                it.context.fromSaveToStyle(state)
                if (popFragment) {
                    activity?.popCurrentPage()
                } else {
                    dismiss()
                }
            }
        }
    }
}

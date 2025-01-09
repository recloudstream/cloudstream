package com.lagradost.cloudstream3.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.text.Cue
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DEPRESSED
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DROP_SHADOW
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_NONE
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_OUTLINE
import com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_RAISED
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CommonActivity.onColorSelectedEvent
import com.lagradost.cloudstream3.CommonActivity.onDialogDismissedEvent
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ChromecastSubtitleSettingsBinding
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage

const val CHROME_SUBTITLE_KEY = "chome_subtitle_settings"

data class SaveChromeCaptionStyle(
    @JsonProperty("fontFamily") var fontFamily: String? = null,
    @JsonProperty("fontGenericFamily") var fontGenericFamily: Int? = null,
    @JsonProperty("backgroundColor") var backgroundColor: Int = 0x00FFFFFF, // transparent
    @JsonProperty("edgeColor") var edgeColor: Int = Color.BLACK, // BLACK
    @JsonProperty("edgeType") var edgeType: Int = EDGE_TYPE_OUTLINE,
    @JsonProperty("foregroundColor") var foregroundColor: Int = Color.WHITE,
    @JsonProperty("fontScale") var fontScale: Float = 1.05f,
    @JsonProperty("windowColor") var windowColor: Int = Color.TRANSPARENT,
)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ChromecastSubtitlesFragment : Fragment() {
    companion object {
        val applyStyleEvent = Event<SaveChromeCaptionStyle>()

        //fun Context.fromSaveToStyle(data: SaveChromeCaptionStyle): CaptionStyleCompat {
        //    return CaptionStyleCompat(
        //        data.foregroundColor,
        //        data.backgroundColor,
        //        data.windowColor,
        //        data.edgeType,
        //        data.edgeColor,
        //        if (typeface == null) Typeface.SANS_SERIF else ResourcesCompat.getFont(
        //            this,
        //            typeface
        //        )
        //    )
        //}

        fun push(activity: Activity?, hide: Boolean = true) {
            activity.navigate(R.id.global_to_navigation_chrome_subtitles, Bundle().apply {
                putBoolean("hide", hide)
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

        fun Context.saveStyle(style: SaveChromeCaptionStyle) {
            this.setKey(CHROME_SUBTITLE_KEY, style)
        }

        private fun getPixels(unit: Int, size: Float): Int {
            val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
            return TypedValue.applyDimension(unit, size, metrics).toInt()
        }

        fun getCurrentSavedStyle(): SaveChromeCaptionStyle {
            return getKey(CHROME_SUBTITLE_KEY) ?: defaultState
        }

        private val defaultState = SaveChromeCaptionStyle()
    }

    private fun onColorSelected(stuff: Pair<Int, Int>) {
        setColor(stuff.first, stuff.second)
        if (hide)
            activity?.hideSystemUI()
    }

    private fun onDialogDismissed(id: Int) {
        if (hide)
            activity?.hideSystemUI()
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

    private fun setColor(id: Int, color: Int?) {
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

    private fun updateState() {
        //subtitle_text?.setStyle(fromSaveToStyle(state))
    }

    var binding : ChromecastSubtitleSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val localBinding = ChromecastSubtitleSettingsBinding.inflate(inflater, container, false)
        binding = localBinding
        return localBinding.root//inflater.inflate(R.layout.chromecast_subtitle_settings, container, false)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private lateinit var state: SaveChromeCaptionStyle
    private var hide: Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        onColorSelectedEvent -= ::onColorSelected
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hide = arguments?.getBoolean("hide") ?: true
        onColorSelectedEvent += ::onColorSelected
        onDialogDismissedEvent += ::onDialogDismissed

        fixPaddingStatusbar(binding?.subsRoot)

        state = getCurrentSavedStyle()
        updateState()

        val isTvSettings = isLayout(TV or EMULATOR)

        fun View.setFocusableInTv() {
            this.isFocusableInTouchMode = isTvSettings
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
                setColor(id, null)
                showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }
        }

        binding?.apply {
            subsTextColor.setup(0)
            subsOutlineColor.setup(1)
            subsBackgroundColor.setup(2)
        }


        val dismissCallback = {
            if (hide)
                activity?.hideSystemUI()
        }

        binding?.subsEdgeType?.setFocusableInTv()
        binding?.subsEdgeType?.setOnClickListener { textView ->
            val edgeTypes = listOf(
                Pair(
                    EDGE_TYPE_NONE,
                    textView.context.getString(R.string.subtitles_none)
                ),
                Pair(
                    EDGE_TYPE_OUTLINE,
                    textView.context.getString(R.string.subtitles_outline)
                ),
                Pair(
                    EDGE_TYPE_DEPRESSED,
                    textView.context.getString(R.string.subtitles_depressed)
                ),
                Pair(
                    EDGE_TYPE_DROP_SHADOW,
                    textView.context.getString(R.string.subtitles_shadow)
                ),
                Pair(
                    EDGE_TYPE_RAISED,
                    textView.context.getString(R.string.subtitles_raised)
                ),
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
                updateState()
            }
        }

        binding?.subsEdgeType?.setOnLongClickListener {
            state.edgeType = defaultState.edgeType
            updateState()
            showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        binding?.subsFontSize?.setFocusableInTv()
        binding?.subsFontSize?.setOnClickListener { textView ->
            val fontSizes = listOf(
                Pair(0.75f, "75%"),
                Pair(0.80f, "80%"),
                Pair(0.85f, "85%"),
                Pair(0.90f, "90%"),
                Pair(0.95f, "95%"),
                Pair(1.00f, "100%"),
                Pair(1.05f, textView.context.getString(R.string.normal)),
                Pair(1.10f, "110%"),
                Pair(1.15f, "115%"),
                Pair(1.20f, "120%"),
                Pair(1.25f, "125%"),
                Pair(1.30f, "130%"),
                Pair(1.35f, "135%"),
                Pair(1.40f, "140%"),
                Pair(1.45f, "145%"),
                Pair(1.50f, "150%"),
            )

            //showBottomDialog
            activity?.showDialog(
                fontSizes.map { it.second },
                fontSizes.map { it.first }.indexOf(state.fontScale),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.fontScale = fontSizes.map { it.first }[index]
                //textView.context.updateState() // font size not changed
            }
        }

        binding?.subsFontSize?.setOnLongClickListener { _ ->
            state.fontScale = defaultState.fontScale
            //textView.context.updateState() // font size not changed
            showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }



        binding?.subsFont?.setFocusableInTv()
        binding?.subsFont?.setOnClickListener { textView ->
            val fontTypes = listOf(
                null to textView.context.getString(R.string.normal),
                "Droid Sans" to "Droid Sans",
                "Droid Sans Mono" to "Droid Sans Mono",
                "Droid Serif Regular" to "Droid Serif Regular",
                "Cutive Mono" to "Cutive Mono",
                "Short Stack" to "Short Stack",
                "Quintessential" to "Quintessential",
                "Alegreya Sans SC" to "Alegreya Sans SC",
            )

            //showBottomDialog
            activity?.showDialog(
                fontTypes.map { it.second },
                fontTypes.map { it.first }.indexOf(state.fontFamily),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.fontFamily = fontTypes.map { it.first }[index]
                updateState()
            }
        }
        binding?.subsFont?.setOnLongClickListener { _ ->
            state.fontFamily = defaultState.fontFamily
            updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        binding?.cancelBtt?.setOnClickListener {
            activity?.popCurrentPage()
        }

        binding?.applyBtt?.setOnClickListener {
            it.context.saveStyle(state)
            applyStyleEvent.invoke(state)
            //it.context.fromSaveToStyle(state)
            activity?.popCurrentPage()
        }
        binding?.subtitleText?.apply {
            setCues(
                listOf(
                    Cue.Builder()
                        .setTextSize(
                            getPixels(TypedValue.COMPLEX_UNIT_SP, 25.0f).toFloat(),
                            Cue.TEXT_SIZE_TYPE_ABSOLUTE
                        )
                        .setText(context.getString(R.string.subtitles_example_text))
                        .build()
                )
            )
        }
    }
}

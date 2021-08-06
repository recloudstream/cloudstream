package com.lagradost.cloudstream3.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.subtitle_settings.*

const val SUBTITLE_KEY = "subtitle_settings"

data class SaveCaptionStyle(
    var foregroundColor: Int,
    var backgroundColor: Int,
    var windowColor: Int,
    @CaptionStyleCompat.EdgeType
    var edgeType: Int,
    var edgeColor: Int,
    var typeface: String?,
    /**in dp**/
    var elevation: Int,
)

class SubtitlesFragment : Fragment() {
    companion object {
        val applyStyleEvent = Event<SaveCaptionStyle>()

        fun fromSaveToStyle(data: SaveCaptionStyle): CaptionStyleCompat {
            return CaptionStyleCompat(
                data.foregroundColor, data.backgroundColor, data.windowColor, data.edgeType, data.edgeColor,
                Typeface.SANS_SERIF
            )
        }

        fun push(activity: Activity?) {
            (activity as FragmentActivity?)?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(
                    R.anim.enter_anim,
                    R.anim.exit_anim,
                    R.anim.pop_enter,
                    R.anim.pop_exit
                )
                ?.add(
                    R.id.homeRoot,
                    SubtitlesFragment()
                )
                ?.commit()
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

        fun Context.getCurrentSavedStyle(): SaveCaptionStyle {
            return this.getKey(SUBTITLE_KEY) ?: SaveCaptionStyle(
                getDefColor(0),
                getDefColor(2),
                getDefColor(3),
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                getDefColor(1),
                null,
                0,
            )
        }

        private fun Context.getCurrentStyle(): CaptionStyleCompat {
            return fromSaveToStyle(getCurrentSavedStyle())
        }

        private fun getPixels(unit: Int, size: Float): Int {
            val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
            return TypedValue.applyDimension(unit, size, metrics).toInt()
        }
    }

    private fun onColorSelected(stuff: Pair<Int, Int>) {
        setColor(stuff.first, stuff.second)
    }

    private fun setColor(id: Int, color: Int?) {
        val realColor = color ?: getDefColor(id)
        when (id) {
            0 -> state.foregroundColor = realColor
            1 -> state.edgeColor = realColor
            2 -> state.backgroundColor = realColor
            3 -> state.windowColor = realColor

            else -> {
            }
        }
        updateState()
    }

    fun updateState() {
        subtitle_text?.setStyle(fromSaveToStyle(state))
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.subtitle_settings, container, false)
    }

    lateinit var state: SaveCaptionStyle

    override fun onDestroy() {
        super.onDestroy()
        MainActivity.onColorSelectedEvent -= ::onColorSelected
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MainActivity.onColorSelectedEvent += ::onColorSelected

        context?.fixPaddingStatusbar(subs_root)

        state = requireContext().getCurrentSavedStyle()
        updateState()

        fun View.setup(id: Int) {
            this.setOnClickListener {
                activity?.let {
                    ColorPickerDialog.newBuilder()
                        .setDialogId(id)
                        .setColor(getColor(id))
                        .show(it)
                }
            }

            this.setOnLongClickListener {
                setColor(id, null)
                Toast.makeText(it.context, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }
        }

        subs_text_color.setup(0)
        subs_outline_color.setup(1)
        subs_background_color.setup(2)
        subs_window_color.setup(3)

        subs_subtitle_elevation.setOnClickListener { textView ->
            val elevationTypes = listOf(
                Pair(0, "None"),
                Pair(10, "10"),
                Pair(20, "20"),
                Pair(30, "30"),
                Pair(40, "40"),
            )

            textView.context.showBottomDialog(
                elevationTypes.map { it.second },
                elevationTypes.map { it.first }.indexOf(state.elevation),
                (textView as TextView).text.toString(),
                false
            ) { index ->
                state.elevation = elevationTypes.map { it.first }[index]
                updateState()
            }
        }

        subs_subtitle_elevation.setOnLongClickListener {
            state.elevation = 0
            updateState()
            Toast.makeText(it.context, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT).show()
            return@setOnLongClickListener true
        }

        subs_edge_type.setOnClickListener { textView ->
            val edgeTypes = listOf(
                Pair(CaptionStyleCompat.EDGE_TYPE_NONE, "None"),
                Pair(CaptionStyleCompat.EDGE_TYPE_OUTLINE, "Outline"),
                Pair(CaptionStyleCompat.EDGE_TYPE_DEPRESSED, "Depressed"),
                Pair(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, "Shadow"),
                Pair(CaptionStyleCompat.EDGE_TYPE_RAISED, "Raised"),
            )

            textView.context.showBottomDialog(
                edgeTypes.map { it.second },
                edgeTypes.map { it.first }.indexOf(state.edgeType),
                (textView as TextView).text.toString(),
                false
            ) { index ->
                state.edgeType = edgeTypes.map { it.first }[index]
                updateState()
            }
        }

        subs_edge_type.setOnLongClickListener {
            state.edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE
            updateState()
            Toast.makeText(it.context, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT).show()
            return@setOnLongClickListener true
        }

        cancel_btt.setOnClickListener {
            activity?.popCurrentPage()
        }

        apply_btt.setOnClickListener {
            applyStyleEvent.invoke(state)
            fromSaveToStyle(state)
            activity?.popCurrentPage()
        }

        subtitle_text.setCues(
            listOf(
                Cue.Builder()
                    .setTextSize(
                        getPixels(TypedValue.COMPLEX_UNIT_SP, 25.0f).toFloat(),
                        Cue.TEXT_SIZE_TYPE_ABSOLUTE
                    )
                    .setText("The quick brown fox jumps over the lazy dog").build()
            )
        )
    }
}
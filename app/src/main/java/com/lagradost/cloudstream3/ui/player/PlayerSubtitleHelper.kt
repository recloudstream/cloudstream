package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.util.MimeTypes
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.fromSaveToStyle
import com.lagradost.cloudstream3.utils.UIHelper.toPx

enum class SubtitleStatus {
    IS_ACTIVE,
    REQUIRES_RELOAD,
    NOT_FOUND,
}

enum class SubtitleOrigin {
    URL,
    DOWNLOADED_FILE,
    OPEN_SUBTITLES,
}

data class SubtitleData(
    val name: String,
    val url: String,
    val origin: SubtitleOrigin,
    val mimeType: String,
)

class PlayerSubtitleHelper {
    private var activeSubtitles: Set<SubtitleData> = emptySet()
    private var allSubtitles: Set<SubtitleData> = emptySet()

    fun getAllSubtitles(): Set<SubtitleData> {
        return allSubtitles
    }

    fun setActiveSubtitles(list: Set<SubtitleData>) {
        activeSubtitles = list
    }

    fun setAllSubtitles(list: Set<SubtitleData>) {
        allSubtitles = list
    }

    private var subStyle: SaveCaptionStyle? = null
    private var subtitleView: SubtitleView? = null

    companion object {
        fun String.toSubtitleMimeType(): String {
            return when {
                endsWith("vtt", true) -> MimeTypes.TEXT_VTT
                endsWith("srt", true) -> MimeTypes.APPLICATION_SUBRIP
                endsWith("xml", true) || endsWith("ttml", true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP // TODO get request to see
            }
        }

        private fun getSubtitleMimeType(context: Context, url: String, origin: SubtitleOrigin): String {
            return when (origin) {
                // The url can look like .../document/4294 when the name is EnglishSDH.srt
                SubtitleOrigin.DOWNLOADED_FILE -> {
                    UniFile.fromUri(
                        context,
                        Uri.parse(url)
                    ).name?.toSubtitleMimeType() ?: MimeTypes.APPLICATION_SUBRIP
                }
                SubtitleOrigin.URL -> {
                    return url.toSubtitleMimeType()
                }
                SubtitleOrigin.OPEN_SUBTITLES -> {
                    // TODO
                    throw NotImplementedError()
                }
            }
        }

        fun getSubtitleData(subtitleFile: SubtitleFile): SubtitleData {
            return SubtitleData(
                name = subtitleFile.lang,
                url = subtitleFile.url,
                origin = SubtitleOrigin.URL,
                mimeType = subtitleFile.url.toSubtitleMimeType()
            )
        }
    }

    fun subtitleStatus(sub : SubtitleData?): SubtitleStatus {
        if(activeSubtitles.contains(sub)) {
            return SubtitleStatus.IS_ACTIVE
        }
        if(allSubtitles.contains(sub)) {
            return SubtitleStatus.REQUIRES_RELOAD
        }
        return SubtitleStatus.NOT_FOUND
    }

    fun setSubStyle(style: SaveCaptionStyle) {
        subtitleView?.context?.let { ctx ->
            subStyle = style
            subtitleView?.setStyle(ctx.fromSaveToStyle(style))
            subtitleView?.translationY = -style.elevation.toPx.toFloat()
            val size = style.fixedTextSize
            if (size != null) {
                subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            } else {
                subtitleView?.setUserDefaultTextSize()
            }
        }
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleView = subView
        subView?.let { sView ->
            (sView.parent as ViewGroup?)?.removeView(sView)
            subHolder?.addView(sView)
        }
        style?.let {
            setSubStyle(it)
        }
    }
}
package com.lagradost.cloudstream3.ui.player

import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.setSubtitleViewStyle
import com.lagradost.cloudstream3.utils.UIHelper.toPx

enum class SubtitleStatus {
    IS_ACTIVE,
    REQUIRES_RELOAD,
    NOT_FOUND,
}

enum class SubtitleOrigin {
    URL,
    DOWNLOADED_FILE,
    EMBEDDED_IN_VIDEO
}

/**
 * @param originalName the start of the name to be displayed in the player
 * @param nameSuffix An extra suffix added to the subtitle to make sure it is unique
 * @param url Url for the subtitle, when EMBEDDED_IN_VIDEO this variable is used as the real backend id
 * @param headers if empty it will use the base onlineDataSource headers else only the specified headers
 * @param languageCode usually, tags such as "en", "es-mx", or "zh-hant-TW". But it could be something like "English 4"
 * */
data class SubtitleData(
    val originalName: String,
    val nameSuffix: String,
    val url: String,
    val origin: SubtitleOrigin,
    val mimeType: String,
    val headers: Map<String, String>,
    val languageCode: String?,
) {
    /** Internal ID for exoplayer, unique for each link*/
    fun getId(): String {
        return if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url
        else "$url|$name"
    }

    val name = "$originalName $nameSuffix"

    /**
     * Gets the URL, but tries to fix it if it is malformed.
     */
    fun getFixedUrl(): String {
        // Some extensions fail to include the protocol, this helps with that.
        val fixedSubUrl = if (this.url.startsWith("//")) {
            "https:${this.url}"
        } else {
            this.url
        }
        return fixedSubUrl
    }
}

@OptIn(UnstableApi::class)
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

    var subtitleView: SubtitleView? = null

    companion object {
        fun String.toSubtitleMimeType(): String {
            return when {
                endsWith("vtt", true) -> MimeTypes.TEXT_VTT
                endsWith("srt", true) -> MimeTypes.APPLICATION_SUBRIP
                endsWith("xml", true) || endsWith("ttml", true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
        }

        fun getSubtitleData(subtitleFile: SubtitleFile): SubtitleData {
            return SubtitleData(
                originalName = subtitleFile.lang,
                nameSuffix = "",
                url = subtitleFile.url,
                origin = SubtitleOrigin.URL,
                mimeType = subtitleFile.url.toSubtitleMimeType(),
                headers = subtitleFile.headers ?: emptyMap(),
                languageCode = subtitleFile.langTag ?: subtitleFile.lang
            )
        }
    }

    fun subtitleStatus(sub: SubtitleData?): SubtitleStatus {
        if (activeSubtitles.contains(sub)) {
            return SubtitleStatus.IS_ACTIVE
        }
        if (allSubtitles.contains(sub)) {
            return SubtitleStatus.REQUIRES_RELOAD
        }
        return SubtitleStatus.NOT_FOUND
    }

    fun setSubStyle(style: SaveCaptionStyle) {
        Log.i(TAG, "SET STYLE = $style")
        subtitleView?.translationY = -style.elevation.toPx.toFloat()
        setSubtitleViewStyle(subtitleView, style)
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
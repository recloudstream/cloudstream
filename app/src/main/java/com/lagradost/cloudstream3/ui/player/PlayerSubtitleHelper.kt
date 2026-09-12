package com.lagradost.cloudstream3.ui.player

import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.fasterxml.jackson.annotation.JsonIgnore
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.setSubtitleViewStyle
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
 */
@Serializable
data class SubtitleData(
    @SerialName("originalName") val originalName: String,
    @SerialName("nameSuffix") val nameSuffix: String,
    @SerialName("url") val url: String,
    @SerialName("origin") val origin: SubtitleOrigin,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("headers") val headers: Map<String, String>,
    @SerialName("languageCode") val languageCode: String?,
) {
    /** Internal ID for media3, unique for each link. */
    @JsonIgnore
    fun getId(): String {
        return if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url
        else "$url|$name"
    }

    /** Returns true if langCode is the same as the IETF tag */
    fun matchesLanguageCode(langCode: String): Boolean {
        return getIETF_tag() == langCode
    }

    /** Tries hard to figure out a valid IETF tag based on language code and name. Will return null if not found. */
    @JsonIgnore
    fun getIETF_tag(): String? {
        return fromLanguageToTagIETF(this.languageCode) ?: fromLanguageToTagIETF(this.originalName, halfMatch = true)
    }

    @SerialName("name") val name = "$originalName $nameSuffix"

    /**
     * Gets the URL, but tries to fix it if it is malformed.
     */
    @JsonIgnore
    fun getFixedUrl(): String {
        // Some extensions fail to include the protocol, this helps with that.
        val fixedSubUrl = if (this.url.startsWith("//")) {
            "https:${this.url}"
        } else this.url
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
    var secondarySubtitleView: SubtitleView? = null

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
        setSubtitleViewStyle(subtitleView, style, true)
        setSubtitleViewStyle(secondarySubtitleView, style.copy(backgroundColor = android.graphics.Color.TRANSPARENT, windowColor = android.graphics.Color.TRANSPARENT), false)
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleView = subView
        secondarySubtitleView = subHolder?.findViewById(com.lagradost.cloudstream3.R.id.secondary_subtitle_view)
        secondarySubtitleView?.isClickable = false
        secondarySubtitleView?.isLongClickable = false
        subView?.let { sView ->
            (sView.parent as ViewGroup?)?.removeView(sView)
            subHolder?.addView(sView)
        }
        style?.let {
            setSubStyle(it)
        }
    }
}

fun android.content.Context.vibrateDevice(durationMillis: Long = 55L) {
    try {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(durationMillis, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator?.vibrate(durationMillis)
        }
    } catch (_: Throwable) {}
}

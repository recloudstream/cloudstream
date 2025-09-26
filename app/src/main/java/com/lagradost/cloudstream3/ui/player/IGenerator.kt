package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlin.math.max
import kotlin.math.min

val LOADTYPE_INAPP = setOf(
    ExtractorLinkType.VIDEO,
    ExtractorLinkType.DASH,
    ExtractorLinkType.M3U8,
    ExtractorLinkType.TORRENT,
    ExtractorLinkType.MAGNET
)

val LOADTYPE_INAPP_DOWNLOAD = setOf(
    ExtractorLinkType.VIDEO,
    ExtractorLinkType.M3U8
)

val LOADTYPE_CHROMECAST = setOf(
    ExtractorLinkType.VIDEO,
    ExtractorLinkType.DASH,
    ExtractorLinkType.M3U8
)

val LOADTYPE_ALL = ExtractorLinkType.entries.toSet()


abstract class NoVideoGenerator : VideoGenerator<Nothing>(emptyList(), 0) {
    override val hasCache = false
    override val canSkipLoading = false
}

abstract class VideoGenerator<T : Any>(val videos: List<T>, var videoIndex: Int = 0) :
    IGenerator {

    override fun hasNext(): Boolean = videoIndex < videos.lastIndex
    override fun hasPrev(): Boolean = videoIndex > 0
    override fun getAll(): List<T>? = videos
    override fun getCurrent(offset: Int): T? = videos.getOrNull(videoIndex + offset)
    override fun next() {
        if (hasNext()) {
            videoIndex += 1
        }
    }

    override fun prev() {
        if (hasPrev()) {
            videoIndex -= 1
        }
    }

    override fun goto(index: Int) {
        videoIndex = min(videos.lastIndex, max(0, index))
    }

    override fun getCurrentId(): Int? {
        return when (val current = getCurrent()) {
            is ResultEpisode -> {
                current.id
            }

            is ExtractorUri -> {
                current.id
            }

            else -> null
        }
    }
}

// TODO deprecate/remove IGenerator in favor of a more ergonomic and correct implementation
interface IGenerator {
    val hasCache: Boolean
    val canSkipLoading: Boolean

    fun hasNext(): Boolean
    fun hasPrev(): Boolean
    fun next()
    fun prev()
    fun goto(index: Int)

    fun getCurrentId(): Int?                    // this is used to save data or read data about this id
    fun getCurrent(offset: Int = 0): Any?      // this is used to get metadata about the current playing, can return null
    fun getAll(): List<Any>?                   // this us used to get the metadata about all entries, not needed

    /* not safe, must use try catch */
    suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int = 0,
        isCasting: Boolean = false
    ): Boolean
}

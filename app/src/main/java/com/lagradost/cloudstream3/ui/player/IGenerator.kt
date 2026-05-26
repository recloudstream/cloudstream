package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

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


abstract class NoVideoGenerator(val id : Int?) : VideoGenerator<Nothing>(emptyList()) {
    override val hasCache = false
    override val canSkipLoading = false
    override fun getId(index: Int): Int? = id
}

abstract class VideoGenerator<T : Any>(val videos: List<T>) {
    abstract val hasCache: Boolean
    abstract val canSkipLoading: Boolean
    abstract fun getId(index : Int) : Int?

    fun hasNext(videoIndex : Int): Boolean = videoIndex < videos.lastIndex
    fun hasPrev(videoIndex : Int): Boolean = videoIndex > 0

    @Throws
    abstract suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean
}

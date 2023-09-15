package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.ExtractorUri

enum class LoadType {
    Unknown,
    InApp,
    InAppDownload,
    ExternalApp,
    Browser,
    Chromecast
}

fun LoadType.toSet() : Set<ExtractorLinkType> {
    return when(this) {
        LoadType.InApp -> setOf(
            ExtractorLinkType.VIDEO,
            ExtractorLinkType.DASH,
            ExtractorLinkType.M3U8
        )
        LoadType.Browser -> setOf(
            ExtractorLinkType.VIDEO,
            ExtractorLinkType.DASH,
            ExtractorLinkType.M3U8
        )
        LoadType.InAppDownload -> setOf(
            ExtractorLinkType.VIDEO,
            ExtractorLinkType.M3U8
        )
        LoadType.ExternalApp, LoadType.Unknown -> ExtractorLinkType.values().toSet()
        LoadType.Chromecast -> setOf(
            ExtractorLinkType.VIDEO,
            ExtractorLinkType.DASH,
            ExtractorLinkType.M3U8
        )
    }
}

interface IGenerator {
    val hasCache: Boolean

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
        type: LoadType,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int = 0,
    ): Boolean
}
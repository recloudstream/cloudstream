package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.*

class LinkGenerator(
    private val links: List<String>,
    private val extract: Boolean = true,
    private val referer: String? = null,
) : IGenerator {
    override val hasCache = false

    override fun getCurrentId(): Int? {
        return null
    }

    override fun hasNext(): Boolean {
        return false
    }

    override fun getAll(): List<Any>? {
        return null
    }

    override fun hasPrev(): Boolean {
        return false
    }

    override fun getCurrent(offset: Int): Any? {
        return null
    }

    override fun goto(index: Int) {}

    override fun next() {}

    override fun prev() {}

    override suspend fun generateLinks(
        clearCache: Boolean,
        isCasting: Boolean,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int
    ): Boolean {
        links.apmap { link ->
            if (!extract || !loadExtractor(link, referer, {
                    subtitleCallback(PlayerSubtitleHelper.getSubtitleData(it))
                }) {
                    callback(it to null)
                }) {

                // if don't extract or if no extractor found simply return the link
                callback(
                    ExtractorLink(
                        "",
                        link,
                        unshortenLinkSafe(link), // unshorten because it might be a raw link
                        referer ?: "",
                        Qualities.Unknown.value, link.contains(".m3u") // TODO USE REAL PARSER
                    ) to null
                )
            }
        }

        return true
    }
}
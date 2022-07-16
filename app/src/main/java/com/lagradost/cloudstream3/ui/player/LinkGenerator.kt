package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

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
            if (!extract || !loadExtractor(link, referer) {
                    callback(it to null)
                }) {
                // if don't extract or if no extractor found simply return the link
                callback(
                    ExtractorLink(
                        "",
                        link,
                        link,
                        referer ?: "",
                        Qualities.Unknown.value, link.contains(".m3u8") // TODO USE REAL PARSER
                    ) to null
                )
            }
        }

        return true
    }
}
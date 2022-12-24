package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri

class ExtractorLinkGenerator(
    private val links: List<ExtractorLink>,
    private val subtitles: List<SubtitleData>,
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
        subtitles.forEach(subtitleCallback)
        links.forEach {
            callback.invoke(it to null)
        }

        return true
    }
}
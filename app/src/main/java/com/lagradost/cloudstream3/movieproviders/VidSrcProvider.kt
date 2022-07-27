package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.extractors.VidSrcExtractor
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink

class VidSrcProvider : TmdbProvider() {
    override val apiName = "VidSrc"
    override var name = "VidSrc"
    override var mainUrl = "https://v2.vidsrc.me"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    companion object {
        val extractor = VidSrcExtractor()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = parseJson<TmdbLink>(data)
        val (id, site) = if (mappedData.imdbID != null) listOf(
            mappedData.imdbID,
            "imdb"
        ) else listOf(mappedData.tmdbID.toString(), "tmdb")
        val isMovie = mappedData.episode == null && mappedData.season == null
        val embedUrl = if (isMovie) {
            if (site == "imdb") "$mainUrl/embed/$id" else
                "$mainUrl/embed/$id"
        } else {
            val suffix = "$id/${mappedData.season ?: 1}-${mappedData.episode ?: 1}"
            if (site == "imdb") "$mainUrl/embed/$suffix" else
                "$mainUrl/embed/$suffix"
        }

        extractor.getSafeUrl(embedUrl, null, subtitleCallback, callback)

        return true
    }
}
package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class ApiMDBProvider : TmdbProvider() {
    override val apiName = "ApiMDB"
    override var name = "ApiMDB"
    override var mainUrl = "https://v2.apimdb.net"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

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
            if(site == "imdb") "$mainUrl/e/movie/$id" else
                "$mainUrl/e/tmdb/movie/$id"
        } else {
            val suffix = "$id/${mappedData.season ?: 1}/${mappedData.episode ?: 1}"
            if (site == "imdb") "$mainUrl/e/tv/$suffix" else
                "$mainUrl/e/tmdb/tv/$suffix"
        }
        val document = app.get(embedUrl, referer = mainUrl).document
        val servers = document.select(".servers-list div.server").map { it.attr("data-src") }
        servers.apmap { serverID ->
            val serverdoc = app.get("$mainUrl$serverID", referer = embedUrl).document
            serverdoc.select("html body iframe").map {
                val link = it.attr("src")
                    .replace("https://vidembed.cc/load.php","https://vidembed.io/streaming.php")
                    .replace(Regex("(https://cloudemb\\.com/play/.*\\?auto=1&referer=)"),"")
                if (link.contains("vidembed", ignoreCase = true)) {
                    val vidstreamid = link.substringAfter("https://vidembed.io/streaming.php?id=")
                    val vidstreamObject = Vidstream("https://vidembed.io")
                    vidstreamObject.getUrl(vidstreamid, isCasting, callback)
                } else
                    loadExtractor(link, embedUrl, callback)
            }
        }
        return true
    }
}
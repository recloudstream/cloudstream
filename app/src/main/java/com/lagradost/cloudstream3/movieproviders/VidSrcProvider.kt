package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.loadExtractor

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
            if(site == "imdb") "$mainUrl/embed/$id" else
                "$mainUrl/embed/$id"
        } else {
            val suffix = "$id/${mappedData.season ?: 1}-${mappedData.episode ?: 1}"
            if (site == "imdb") "$mainUrl/embed/$suffix" else
                "$mainUrl/embed/$suffix"
        }
        val iframedoc = app.get(embedUrl).document

        val serverslist = iframedoc.select("div#sources.button_content div#content div#list div").map {
            val datahash = it.attr("data-hash")
            if (datahash.isNotBlank()) {
                val links = try {
                    app.get("$mainUrl/src/$datahash", referer = "https://source.vidsrc.me/").url
                } catch (e: Exception) {
                    ""
                }
                links
            } else ""
        }

        serverslist.apmap { server ->
            val linkfixed = server.replace("https://vidsrc.xyz/","https://embedsito.com/")
            if (linkfixed.contains("/pro")) {
                val srcresponse = app.get(server, referer = mainUrl).text
                val m3u8Regex = Regex("((https:|http:)\\/\\/.*\\.m3u8)")
                val srcm3u8 = m3u8Regex.find(srcresponse)?.value ?: return@apmap false
                generateM3u8(
                    name,
                    srcm3u8,
                    mainUrl
                ).forEach(callback)
            } else
                loadExtractor(linkfixed, embedUrl, callback)
        }

        return true
    }
}
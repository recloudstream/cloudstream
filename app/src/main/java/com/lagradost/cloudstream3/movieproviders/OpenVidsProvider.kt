package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.extractVidstream
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class OpenVidsProvider:TmdbProvider() {
    override val apiName = "OpenVids"
    override var name = "OpenVids"
    override var mainUrl = "https://openvids.io"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    data class  OpenvidsMain(
        @JsonProperty("ok"      ) val ok      : Boolean? = null,
        @JsonProperty("servers" ) val servers : OpenvidServers? = OpenvidServers()
    )

    data class OpenvidServers (
        @JsonProperty("streamsb" ) val streamsb : OpenvidServersData? = OpenvidServersData(),
        @JsonProperty("voxzer"   ) val voxzer   : OpenvidServersData?   = OpenvidServersData(),
        @JsonProperty("mixdrop"   ) val mixdrop   : OpenvidServersData?   = OpenvidServersData(),
        @JsonProperty("doodstream"   ) val doodstream   : OpenvidServersData?   = OpenvidServersData(),
        @JsonProperty("voe"   ) val voe   : OpenvidServersData?   = OpenvidServersData(),
        @JsonProperty("vidcloud" ) val vidcloud : OpenvidServersData? = OpenvidServersData()
    )
    data class OpenvidServersData (
        @JsonProperty("code"      ) val code      : String?  = null,
        @JsonProperty("updatedAt" ) val updatedAt : String?  = null,
        @JsonProperty("encoded"   ) val encoded   : Boolean? = null
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
            if(site == "imdb") "$mainUrl/movie/$id" else
                "$mainUrl/tmdb/movie/$id"
        } else {
            val suffix = "$id-${mappedData.season ?: 1}-${mappedData.episode ?: 1}"
            if (site == "imdb") "$mainUrl/episode/$suffix" else
                "$mainUrl/tmdb/episode/$suffix"
        }
        val zonedatetime = ZonedDateTime.now()
        val timeformated = DateTimeFormatter.ISO_INSTANT.format(zonedatetime)
        val headers = if (isMovie) {
            mapOf(
                "Host" to "openvids.io",
                "User-Agent" to USER_AGENT,
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Referer" to embedUrl,
                "updatedAt" to timeformated,
                "title" to "${mappedData.movieName}",
                "year" to "2016",
                "DNT" to "1",
                "Alt-Used" to "openvids.io",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
            )
        } else {
            mapOf(
                "Host" to "openvids.io",
                "User-Agent" to USER_AGENT,
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.5",
                "Referer" to embedUrl,
                "updatedAt" to timeformated,
                "title" to "${mappedData.movieName} - season 1",
                "year" to "2021",
                "e" to "${mappedData.episode}",
                "s" to "${mappedData.season}",
                "DNT" to "1",
                "Alt-Used" to "openvids.io",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
            )
        }
        val json = app.get("$mainUrl/api/servers.json?imdb=${mappedData.imdbID}", headers = headers).parsedSafe<OpenvidsMain>()

        val listservers = listOf(
            "https://streamsb.net/e/" to json?.servers?.streamsb?.code,
            "https://player.voxzer.org/view/" to json?.servers?.voxzer?.code,
            "https://mixdrop.co/e/" to json?.servers?.mixdrop?.code,
            "https://dood.pm/e/" to json?.servers?.doodstream?.code,
            "https://voe.sx/e/" to json?.servers?.voe?.code,
            "https://membed.net/streaming.php?id=" to json?.servers?.vidcloud?.code
        ).mapNotNull { (url, id) -> if(id==null) return@mapNotNull null else "$url$id" }

        if (json?.ok != true) return false
        listservers.apmap { links ->
            if (links.contains("membed")) {
                val membed = VidEmbedProvider()
                extractVidstream(
                    links,
                    this.name,
                    callback,
                    membed.iv,
                    membed.secretKey,
                    membed.secretDecryptKey,
                    membed.isUsingAdaptiveKeys,
                    membed.isUsingAdaptiveData)
            } else
                loadExtractor(links, data, subtitleCallback, callback)
        }
        return true
    }

}
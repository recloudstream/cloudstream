package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class SoaptwoDayProvider : MainAPI() {
    override var mainUrl = "https://secretlink.xyz" //Probably a rip off, but it has no captcha
    override var name = "Soap2Day"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        Pair("$mainUrl/movielist?page=", "Movies"),
        Pair("$mainUrl/tvlist?page=", "TV Series"),
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val url = request.data + page

        val soup = app.get(url).document
        val home =
            soup.select("div.container div.row div.col-sm-12.col-lg-12 div.row div.col-sm-12.col-lg-12 .col-xs-6")
                .map {
                    val title = it.selectFirst("h5 a")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        TvType.TvSeries,
                        fixUrl(it.selectFirst("img")!!.attr("src")),
                        null,
                        null,
                    )
                }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search/keyword/$query").document
        return doc.select("div.container div.row div.col-sm-12.col-lg-12 div.row div.col-sm-12.col-lg-12 .col-xs-6")
            .map {
                val title = it.selectFirst("h5 a")!!.text()
                val image = fixUrl(it.selectFirst("img")!!.attr("src"))
                val href = fixUrl(it.selectFirst("a")!!.attr("href"))
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = soup.selectFirst(".hidden-lg > div:nth-child(1) > h4")?.text() ?: ""
        val description = soup.selectFirst("p#wrap")?.text()?.trim()
        val poster =
            soup.selectFirst(".col-md-5 > div:nth-child(1) > div:nth-child(1) > img")?.attr("src")
        val episodes = mutableListOf<Episode>()
        soup.select("div.alert").forEach {
            val season = it?.selectFirst("h4")?.text()?.filter { c -> c.isDigit() }?.toIntOrNull()
            it?.select("div > div > a")?.forEach { entry ->
                val link = fixUrlNull(entry?.attr("href")) ?: return@forEach
                val text = entry?.text() ?: ""
                val name = text.replace(Regex("(^(\\d+)\\.)"), "")
                val epNum = text.substring(0, text.indexOf(".")).toIntOrNull()
                episodes.add(
                    Episode(
                        name = name,
                        data = link,
                        season = season,
                        episode = epNum
                    )
                )
            }
        }
        val otherInfoBody = soup.select("div.col-sm-8 div.panel-body").toString()
        //Fetch casts
        val casts = otherInfoBody.substringAfter("Stars : ")
            .substringBefore("Genre : ").let {
                Jsoup.parse(it).select("a")
            }.mapNotNull {
                val castName = it?.text() ?: return@mapNotNull null
                ActorData(
                    Actor(
                        name = castName
                    )
                )
            }
        //Fetch year
        val year = otherInfoBody.substringAfter("<h4>Release : </h4>")
            .substringBefore("<div").let {
                //Log.i(this.name, "Result => year string: $it")
                Jsoup.parse(it).select("p")[1]
            }?.text()?.take(4)?.toIntOrNull()
        //Fetch genres
        val genre = otherInfoBody.substringAfter("<h4>Genre : </h4>")
            .substringBefore("<h4>Release : </h4>").let {
                //Log.i(this.name, "Result => genre string: $it")
                Jsoup.parse(it).select("a")
            }.mapNotNull { it?.text()?.trim() ?: return@mapNotNull null }

        return when (val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes.reversed(),
                    fixUrlNull(poster),
                    year = year,
                    description,
                    actors = casts,
                    tags = genre
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    fixUrlNull(poster),
                    year = year,
                    description,
                    actors = casts,
                    tags = genre
                )
            }
            else -> null
        }
    }

    data class ServerJson(
        @JsonProperty("0") val zero: String?,
        @JsonProperty("key") val key: Boolean?,
        @JsonProperty("val") val stream: String?,
        @JsonProperty("val_bak") val streambackup: String?,
        @JsonProperty("pos") val pos: Int?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("subs") val subs: List<Subs>?,
        @JsonProperty("prev_epi_title") val prevEpiTitle: String?,
        @JsonProperty("prev_epi_url") val prevEpiUrl: String?,
        @JsonProperty("next_epi_title") val nextEpiTitle: String?,
        @JsonProperty("next_epi_url") val nextEpiUrl: String?
    )

    data class Subs(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("movieId") val movieId: Int?,
        @JsonProperty("tvId") val tvId: Int?,
        @JsonProperty("episodeId") val episodeId: Int?,
        @JsonProperty("default") val default: Int?,
        @JsonProperty("IsShow") val IsShow: Int?,
        @JsonProperty("name") val name: String,
        @JsonProperty("path") val path: String?,
        @JsonProperty("downlink") val downlink: String?,
        @JsonProperty("source_file_name") val sourceFileName: String?,
        @JsonProperty("createtime") val createtime: Int?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val idplayer = doc.selectFirst("#divU")?.text()
        val idplayer2 = doc.selectFirst("#divP")?.text()
        val movieid = doc.selectFirst("div.row input#hId")!!.attr("value")
        val tvType = try {
            doc.selectFirst(".col-md-5 > div:nth-child(1) > div:nth-child(1) > img")!!.attr("src")
                ?: ""
        } catch (e: Exception) {
            ""
        }
        val ajaxlink =
            if (tvType.contains("movie")) "$mainUrl/home/index/GetMInfoAjax" else "$mainUrl/home/index/GetEInfoAjax"
        listOf(
            idplayer,
            idplayer2,
        ).mapNotNull { playerID ->
            val url = app.post(
                ajaxlink,
                headers = mapOf(
                    "Host" to "secretlink.xyz",
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Origin" to "https://secretlink.xyz",
                    "DNT" to "1",
                    "Connection" to "keep-alive",
                    "Referer" to data,
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "same-origin",
                ),
                data = mapOf(
                    Pair("pass", movieid),
                    Pair("param", playerID ?: ""),
                )
            ).text.replace("\\\"", "\"").replace("\"{", "{").replace("}\"", "}")
                .replace("\\\\\\/", "\\/")
            val json = parseJson<ServerJson>(url)
            listOfNotNull(
                json.stream,
                json.streambackup
            ).apmap { stream ->
                val cleanstreamurl = stream.replace("\\/", "/").replace("\\\\\\", "")
                if (cleanstreamurl.isNotBlank()) {
                    callback(
                        ExtractorLink(
                            "Soap2Day",
                            "Soap2Day",
                            cleanstreamurl,
                            "https://soap2day.ac",
                            Qualities.Unknown.value,
                            isM3u8 = false
                        )
                    )
                }
            }
            json.subs?.forEach { subtitle ->
                val sublink = mainUrl + subtitle.path
                listOf(
                    sublink,
                    subtitle.downlink
                ).mapNotNull { subs ->
                    if (subs != null) {
                        if (subs.isNotBlank()) {
                            subtitleCallback(
                                SubtitleFile(subtitle.name, subs)
                            )
                        }
                    }
                }
            }
        }
        return true
    }
}

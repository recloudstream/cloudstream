package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*


class MundoDonghuaProvider : MainAPI() {

    override var mainUrl = "https://www.mundodonghua.com"
    override var name = "MundoDonghua"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/lista-donghuas", "Donghuas"),
        )

        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl, timeout = 120).document.select("div.row .col-xs-4").map {
                    val title = it.selectFirst("h5")?.text() ?: ""
                    val poster = it.selectFirst(".fit-1 img")?.attr("src")
                    val epRegex = Regex("(\\/(\\d+)\$)")
                    val url = it.selectFirst("a")?.attr("href")?.replace(epRegex,"")?.replace("/ver/","/donghua/")
                    val epnumRegex = Regex("((\\d+)$)")
                    val epNum = epnumRegex.find(title)?.value?.toIntOrNull()
                    val dubstat = if (title.contains("Latino") || title.contains("Castellano")) DubStatus.Dubbed else DubStatus.Subbed
                    newAnimeSearchResponse(title.replace(Regex("Episodio|(\\d+)"),"").trim(), fixUrl(url ?: "")) {
                        this.posterUrl = fixUrl(poster ?: "")
                        addDubStatus(dubstat, epNum)
                    }
                })
        )

        urls.apmap { (url, name) ->
            val home = app.get(url, timeout = 120).document.select(".col-xs-4").map {
                val title = it.selectFirst(".fs-14")?.text() ?: ""
                val poster = it.selectFirst(".fit-1 img")?.attr("src") ?: ""
                AnimeSearchResponse(
                    title,
                    fixUrl(it.selectFirst("a")?.attr("href") ?: ""),
                    this.name,
                    TvType.Anime,
                    fixUrl(poster),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }

            items.add(HomePageList(name, home))
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/busquedas/$query", timeout = 120).document.select(".col-xs-4").map {
            val title = it.selectFirst(".fs-14")?.text() ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val image = it.selectFirst(".fit-1 img")?.attr("src")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                fixUrl(image ?: ""),
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed),
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("head meta[property=og:image]")?.attr("content") ?: ""
        val title = doc.selectFirst(".ls-title-serie")?.text() ?: ""
        val description = doc.selectFirst("p.text-justify.fc-dark")?.text() ?: ""
        val genres = doc.select("span.label.label-primary.f-bold").map { it.text() }
        val status = when (doc.selectFirst("div.col-md-6.col-xs-6.align-center.bg-white.pt-10.pr-15.pb-0.pl-15 p span.badge.bg-default")?.text()) {
            "En Emisión" -> ShowStatus.Ongoing
            "Finalizada" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select("ul.donghua-list a").map {
            val name = it.selectFirst(".fs-16")?.text()
            val link = it.attr("href")
            Episode(fixUrl(link), name)
        }.reversed()
        val typeinfo = doc.select("div.row div.col-md-6.pl-15 p.fc-dark").text()
        val tvType = if (typeinfo.contains(Regex("Tipo.*Pel.cula"))) TvType.AnimeMovie else TvType.Anime
        return newAnimeLoadResponse(title, url, tvType) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }
    data class Protea (
        @JsonProperty("source") val source: List<Source>,
        @JsonProperty("poster") val poster: String?
    )

    data class Source (
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("default") val default: String?
    )

    private fun cleanStream(
        name: String,
        url: String,
        qualityString: String?,
        callback: (ExtractorLink) -> Unit,
        isM3U8: Boolean
    ): Boolean {
        callback(
            ExtractorLink(
                name,
                name,
                url,
                "",
                getQualityFromName(qualityString),
                isM3U8
            )
        )
        return true
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("eval(function(p,a,c,k,e")) {
                val packedRegex = Regex("eval\\(function\\(p,a,c,k,e,.*\\)\\)")
                packedRegex.findAll(script.data()).map {
                    it.value
                }.toList().apmap {
                    val unpack = getAndUnpack(it).replace("diasfem","embedsito")
                    fetchUrls(unpack).apmap { url ->
                        loadExtractor(url, data, subtitleCallback, callback)
                    }
                    if (unpack.contains("protea_tab")) {
                        val protearegex = Regex("(protea_tab.*slug.*,type)")
                        val slug = protearegex.findAll(unpack).map {
                            it.value.replace(Regex("(protea_tab.*slug\":\")"),"").replace("\"},type","")
                        }.first()
                        val requestlink = "$mainUrl/api_donghua.php?slug=$slug"
                        val response = app.get(requestlink, headers =
                        mapOf("Host" to "www.mundodonghua.com",
                            "User-Agent" to USER_AGENT,
                            "Accept" to "*/*",
                            "Accept-Language" to "en-US,en;q=0.5",
                            "Referer" to data,
                            "X-Requested-With" to "XMLHttpRequest",
                            "DNT" to "1",
                            "Connection" to "keep-alive",
                            "Sec-Fetch-Dest" to "empty",
                            "Sec-Fetch-Mode" to "no-cors",
                            "Sec-Fetch-Site" to "same-origin",
                            "TE" to "trailers",
                            "Pragma" to "no-cache",
                            "Cache-Control" to "no-cache",)
                        ).text.removePrefix("[").removeSuffix("]")
                        val json = parseJson<Protea>(response)
                        json.source.forEach { source ->
                            val protename = "Protea"
                            cleanStream(protename, fixUrl(source.file), source.label, callback, false)
                        }
                    }
                    if (unpack.contains("asura_player")) {
                        val asuraRegex = Regex("(asura_player.*type)")
                        asuraRegex.findAll(unpack).map {
                            it.value
                        }.toList().apmap { protea ->
                            val asuraname = "Asura"
                            val file = protea.substringAfter("{file:\"").substringBefore("\"")
                            generateM3u8(
                                asuraname,
                                file,
                                ""
                            ).forEach {
                                cleanStream(asuraname, it.url, it.quality.toString(), callback, true)
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}
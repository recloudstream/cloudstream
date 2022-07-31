package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.util.*
import kotlin.collections.ArrayList


class EstrenosDoramasProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.Movie
            else TvType.TvSeries
        }
    }

    override var mainUrl = "https://www23.estrenosdoramas.net"
    override var name = "EstrenosDoramas"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair(mainUrl, "Últimas series"),
            Pair("$mainUrl/category/peliculas", "Películas"),
        )

        val items = ArrayList<HomePageList>()

        urls.apmap { (url, name) ->
            val home = app.get(url, timeout = 120).document.select("div.clearfix").map {
                val title = cleanTitle(it.selectFirst("h3 a")?.text()!!)
                val poster = it.selectFirst("img.cate_thumb")?.attr("src")
                AnimeSearchResponse(
                    title,
                    it.selectFirst("a")?.attr("href")!!,
                    this.name,
                    TvType.AsianDrama,
                    poster,
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
        val searchob = ArrayList<AnimeSearchResponse>()
        val search =
            app.get("$mainUrl/?s=$query", timeout = 120).document.select("div.clearfix").map {
                val title = cleanTitle(it.selectFirst("h3 a")?.text()!!)
                val href = it.selectFirst("a")?.attr("href")
                val image = it.selectFirst("img.cate_thumb")?.attr("src")
                val lists =
                    AnimeSearchResponse(
                        title,
                        href!!,
                        this.name,
                        TvType.AsianDrama,
                        image,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                    )
                if (href.contains("capitulo")) {
                    //nothing
                }
                else {
                    searchob.add(lists)
                }
            }
        return searchob
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("head meta[property]")?.attr("content")
        val title = doc.selectFirst("h1.titulo")?.text()
        val description = try {
            doc.selectFirst("div.post div.highlight div.font")?.text()
        } catch (e:Exception){
            null
        }
        val finaldesc = description?.substringAfter("Sinopsis")?.replace(": ", "")?.trim()
        val epi = ArrayList<Episode>()
        val episodes = doc.select("div.post .lcp_catlist a").map {
            val name = it.selectFirst("a")?.text()
            val link = it.selectFirst("a")?.attr("href")
            val test = Episode(link!!, name)
            if (!link.equals(url)) {
                epi.add(test)
            }
        }.reversed()
        return when (val type = if (episodes.isEmpty()) TvType.Movie else TvType.AsianDrama) {
            TvType.AsianDrama -> {
                return newAnimeLoadResponse(title!!, url, type) {
                    japName = null
                    engName = title.replace(Regex("[Pp]elicula |[Pp]elicula"),"")
                    posterUrl = poster
                    addEpisodes(DubStatus.Subbed, epi.reversed())
                    plot = finaldesc
                }
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    cleanTitle(title!!),
                    url,
                    this.name,
                    TvType.Movie,
                    url,
                    poster,
                    null,
                    finaldesc,
                    null,
                    null,
                )
            }
            else -> null
        }

    }



    data class ReproDoramas (
        @JsonProperty("link") val link: String,
        @JsonProperty("time") val time: Int
    )

    private fun cleanTitle(title: String): String = title.replace(Regex("[Pp]elicula |[Pp]elicula"),"")

    private fun cleanExtractor(
        source: String,
        name: String,
        url: String,
        referer: String,
        m3u8: Boolean,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            ExtractorLink(
                source,
                name,
                url,
                referer,
                Qualities.Unknown.value,
                m3u8
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
        val headers = mapOf("Host" to "repro3.estrenosdoramas.us",
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to "https://repro3.estrenosdoramas.us",
            "DNT" to "1",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Cache-Control" to "max-age=0",)

        val document = app.get(data).document
        document.select("div.tab_container iframe").apmap { container ->
            val directlink = fixUrl(container.attr("src"))
            loadExtractor(directlink, data, subtitleCallback, callback)

            if (directlink.contains("/repro/amz/")) {
                val amzregex = Regex("https:\\/\\/repro3\\.estrenosdoramas\\.us\\/repro\\/amz\\/examples\\/.*\\.php\\?key=.*\$")
                amzregex.findAll(directlink).map {
                    it.value.replace(Regex("https:\\/\\/repro3\\.estrenosdoramas\\.us\\/repro\\/amz\\/examples\\/.*\\.php\\?key="),"")
                }.toList().apmap { key ->
                    val response = app.post("https://repro3.estrenosdoramas.us/repro/amz/examples/player/api/indexDCA.php",
                        headers = headers,
                        data = mapOf(
                            Pair("key",key),
                            Pair("token","MDAwMDAwMDAwMA=="),
                        ),
                        allowRedirects = false
                    ).text
                    val reprojson = parseJson<ReproDoramas>(response)
                    val decodeurl = base64Decode(reprojson.link)
                    if (decodeurl.contains("m3u8"))

                        cleanExtractor(
                            name,
                            name,
                            decodeurl,
                            "https://repro3.estrenosdoramas.us",
                            decodeurl.contains(".m3u8"),
                            callback
                        )
                }
            }


            if (directlink.contains("reproducir14")) {
                val regex = Regex("(https:\\/\\/repro.\\.estrenosdoramas\\.us\\/repro\\/reproducir14\\.php\\?key=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
                regex.findAll(directlink).map {
                    it.value
                }.toList().apmap {
                    val doc = app.get(it).text
                    val videoid = doc.substringAfter("vid=\"").substringBefore("\" n")
                    val token = doc.substringAfter("name=\"").substringBefore("\" s")
                    val acctkn = doc.substringAfter("{ acc: \"").substringBefore("\", id:")
                    val link = app.post("https://repro3.estrenosdoramas.us/repro/proto4.php",
                        headers = headers,
                        data = mapOf(
                            Pair("acc",acctkn),
                            Pair("id",videoid),
                            Pair("tk",token)),
                        allowRedirects = false
                    ).text
                    val extracteklink = link.substringAfter("\"urlremoto\":\"").substringBefore("\"}")
                        .replace("\\/", "/").replace("//ok.ru/","http://ok.ru/")
                    loadExtractor(extracteklink, data, subtitleCallback, callback)
                }
            }

            if (directlink.contains("reproducir120")) {
                val regex = Regex("(https:\\/\\/repro3.estrenosdoramas.us\\/repro\\/reproducir120\\.php\\?\\nkey=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
                regex.findAll(directlink).map {
                    it.value
                }.toList().apmap {
                    val doc = app.get(it).text
                    val videoid = doc.substringAfter("var videoid = '").substringBefore("';")
                    val token = doc.substringAfter("var tokens = '").substringBefore("';")
                    val acctkn = doc.substringAfter("{ acc: \"").substringBefore("\", id:")
                    val link = app.post("https://repro3.estrenosdoramas.us/repro/api3.php",
                        headers = headers,
                        data = mapOf(
                            Pair("acc",acctkn),
                            Pair("id",videoid),
                            Pair("tk",token)),
                        allowRedirects = false
                    ).text
                    val extractedlink = link.substringAfter("\"{file:'").substringBefore("',label:")
                        .replace("\\/", "/")
                    val quality = link.substringAfter(",label:'").substringBefore("',type:")
                    val type = link.substringAfter("type: '").substringBefore("'}\"")
                    if (extractedlink.isNotBlank())
                        if (quality.contains("File not found", ignoreCase = true)) {
                            //Nothing
                        } else {
                            cleanExtractor(
                                "Movil",
                                "Movil $quality",
                                extractedlink,
                                "",
                                !type.contains("mp4"),
                                callback
                            )
                        }
                }
            }
        }

        return true
    }
}

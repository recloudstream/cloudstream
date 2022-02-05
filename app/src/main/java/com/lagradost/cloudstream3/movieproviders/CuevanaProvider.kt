package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import java.util.*

class CuevanaProvider:MainAPI() {
    override val mainUrl = "https://cuevana3.io"
    override val name = "Cuevana"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair(mainUrl, "Recientemente actualizadas"),
            Pair("$mainUrl/estrenos/", "Estrenos"),
        )
        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select("section li.xxx.TPostMv").map {
                    val title = it.selectFirst("h2.Title").text()
                    val link = it.selectFirst("a").attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        if (link.contains("/pelicula/")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst("img.lazy").attr("data-src"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                logError(e)
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select("li.xxx.TPostMv").map {
            val title = it.selectFirst("h2.Title").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst("img.lazy").attr("data-src")
            val isSerie = href.contains("/serie/")

            if (isSerie) {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            } else {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null
                )
            }
        }.toList()
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document
        val title = soup.selectFirst("h1.Title").text()
        val description = soup.selectFirst(".Description p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".movtv-info div.Image img").attr("data-src")
        val year1 = soup.selectFirst("footer p.meta").toString()
        val yearRegex = Regex("(\\d+)<\\/span>")
        val year =  yearRegex.findAll(year1).map {
            it.value.replace("</span>","")
        }.toList().first().toIntOrNull()
        val episodes = soup.select(".all-episodes li.TPostMv article").map { li ->
            val href = li.select("a").attr("href")
            val epThumb =
                li.selectFirst("div.Image img").attr("data-src") ?: li.selectFirst("img.lazy").attr("data-srcc")
            val name = li.selectFirst("h2.Title").text()
            TvSeriesEpisode(
                name,
                null,
                null,
                href,
                fixUrl(epThumb)
            )
        }
        return when (val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year,
                    description,
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    year,
                    description,
                )
            }
            else -> null
        }
    }

    data class Femcuevana(
        @JsonProperty("url") val url: String,
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div.TPlayer.embed_div iframe").apmap {
            val iframe = fixUrl(it.attr("data-src"))
            if (iframe.contains("api.cuevana3.io/fembed/")) {
                val femregex = Regex("(https.\\/\\/api\\.cuevana3\\.io\\/fembed\\/\\?h=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
                femregex.findAll(iframe).map { femreg ->
                    femreg.value
                }.toList().apmap { fem ->
                    val key = fem.replace("https://api.cuevana3.io/fembed/?h=","")
                    val url = app.post("https://api.cuevana3.io/fembed/api.php", allowRedirects = false, headers = mapOf("Host" to "api.cuevana3.io",
                        "User-Agent" to USER_AGENT,
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to "https://api.cuevana3.io",
                        "DNT" to "1",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "same-origin",),
                        data = mapOf(Pair("h",key))).text
                    val json = parseJson<Femcuevana>(url)
                    val link = json.url
                    if (link.contains("fembed")) {
                        loadExtractor(link, data, callback)
                    }
                }
            }
            if (iframe.contains("tomatomatela")) {
                val tomatoRegex = Regex("(\\/\\/apialfa.tomatomatela.com\\/ir\\/player.php\\?h=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
                tomatoRegex.findAll(iframe).map { tomreg ->
                    tomreg.value
                }.toList().apmap { tom ->
                    val tomkey = tom.replace("//apialfa.tomatomatela.com/ir/player.php?h=","")
                    app.post("https://apialfa.tomatomatela.com/ir/rd.php", allowRedirects = false,
                        headers = mapOf("Host" to "apialfa.tomatomatela.com",
                            "User-Agent" to USER_AGENT,
                            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                            "Accept-Language" to "en-US,en;q=0.5",
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "Origin" to "null",
                            "DNT" to "1",
                            "Connection" to "keep-alive",
                            "Upgrade-Insecure-Requests" to "1",
                            "Sec-Fetch-Dest" to "iframe",
                            "Sec-Fetch-Mode" to "navigate",
                            "Sec-Fetch-Site" to "same-origin",),
                        data = mapOf(Pair("url",tomkey))
                    ).response.headers.values("location").apmap { loc ->
                        if (loc.contains("goto_ddh.php")) {
                            val gotoregex = Regex("(\\/\\/api.cuevana3.io\\/ir\\/goto_ddh.php\\?h=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
                            gotoregex.findAll(loc).map { goreg ->
                                goreg.value.replace("//api.cuevana3.io/ir/goto_ddh.php?h=","")
                            }.toList().apmap { gotolink ->
                                app.post("https://api.cuevana3.io/ir/redirect_ddh.php", allowRedirects = false,
                                    headers = mapOf("Host" to "api.cuevana3.io",
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
                                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                                        "Accept-Language" to "en-US,en;q=0.5",
                                        "Content-Type" to "application/x-www-form-urlencoded",
                                        "Origin" to "null",
                                        "DNT" to "1",
                                        "Connection" to "keep-alive",
                                        "Upgrade-Insecure-Requests" to "1",
                                        "Sec-Fetch-Dest" to "iframe",
                                        "Sec-Fetch-Mode" to "navigate",
                                        "Sec-Fetch-Site" to "same-origin",),
                                    data = mapOf(Pair("url",gotolink))
                                ).response.headers.values("location").apmap { golink ->
                                    loadExtractor(golink, data, callback)
                                }
                            }
                        }
                        if (loc.contains("index.php?h=")) {
                            val indexRegex = Regex("(\\/\\/api.cuevana3.io\\/sc\\/index.php\\?h=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
                            indexRegex.findAll(loc).map { indreg ->
                                indreg.value.replace("//api.cuevana3.io/sc/index.php?h=","")
                            }.toList().apmap { inlink ->
                                app.post("https://api.cuevana3.io/sc/r.php", allowRedirects = false,
                                    headers = mapOf("Host" to "api.cuevana3.io",
                                        "User-Agent" to USER_AGENT,
                                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                                        "Accept-Language" to "en-US,en;q=0.5",
                                        "Accept-Encoding" to "gzip, deflate, br",
                                        "Content-Type" to "application/x-www-form-urlencoded",
                                        "Origin" to "null",
                                        "DNT" to "1",
                                        "Connection" to "keep-alive",
                                        "Upgrade-Insecure-Requests" to "1",
                                        "Sec-Fetch-Dest" to "iframe",
                                        "Sec-Fetch-Mode" to "navigate",
                                        "Sec-Fetch-Site" to "same-origin",
                                        "Sec-Fetch-User" to "?1",),
                                    data = mapOf(Pair("h",inlink))
                                ).response.headers.values("location").apmap { link ->
                                    loadExtractor(link, data, callback)
                                }
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}
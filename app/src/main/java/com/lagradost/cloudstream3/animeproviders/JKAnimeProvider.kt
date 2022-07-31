package com.lagradost.cloudstream3.animeproviders


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*


class JKAnimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override var mainUrl = "https://jkanime.net"
    override var name = "JKAnime"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair(
                "$mainUrl/directorio/?filtro=fecha&tipo=TV&estado=1&fecha=none&temporada=none&orden=desc",
                "En emisión"
            ),
            Pair(
                "$mainUrl/directorio/?filtro=fecha&tipo=none&estado=none&fecha=none&temporada=none&orden=none",
                "Animes"
            ),
            Pair(
                "$mainUrl/directorio/?filtro=fecha&tipo=Movie&estado=none&fecha=none&temporada=none&orden=none",
                "Películas"
            ),
        )

        val items = ArrayList<HomePageList>()

        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select(".listadoanime-home a.bloqq").map {
                    val title = it.selectFirst("h5")?.text()
                    val dubstat = if (title!!.contains("Latino") || title.contains("Castellano"))
                        DubStatus.Dubbed else DubStatus.Subbed
                    val poster =
                        it.selectFirst(".anime__sidebar__comment__item__pic img")?.attr("src") ?: ""
                    val epRegex = Regex("/(\\d+)/|/especial/|/ova/")
                    val url = it.attr("href").replace(epRegex, "")
                    val epNum =
                        it.selectFirst("h6")?.text()?.replace("Episodio ", "")?.toIntOrNull()
                    newAnimeSearchResponse(title, url) {
                        this.posterUrl = poster
                        addDubStatus(dubstat, epNum)
                    }
                })
        )
        urls.apmap { (url, name) ->
            val soup = app.get(url).document
            val home = soup.select(".g-0").map {
                val title = it.selectFirst("h5 a")?.text()
                val poster = it.selectFirst("img")?.attr("src") ?: ""
                AnimeSearchResponse(
                    title!!,
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

    data class MainSearch(
        @JsonProperty("animes") val animes: List<Animes>,
        @JsonProperty("anime_types") val animeTypes: AnimeTypes
    )

    data class Animes(
        @JsonProperty("id") val id: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("image") val image: String,
        @JsonProperty("synopsis") val synopsis: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("thumbnail") val thumbnail: String
    )

    data class AnimeTypes(
        @JsonProperty("TV") val TV: String,
        @JsonProperty("OVA") val OVA: String,
        @JsonProperty("Movie") val Movie: String,
        @JsonProperty("Special") val Special: String,
        @JsonProperty("ONA") val ONA: String,
        @JsonProperty("Music") val Music: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val main = app.get("$mainUrl/ajax/ajax_search/?q=$query").text
        val json = parseJson<MainSearch>(main)
        return json.animes.map {
            val title = it.title
            val href = "$mainUrl/${it.slug}"
            val image = "https://cdn.jkanime.net/assets/images/animes/image/${it.slug}.jpg"
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                image,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed),
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".set-bg")?.attr("data-setbg")
        val title = doc.selectFirst(".anime__details__title > h3")?.text()
        val type = doc.selectFirst(".anime__details__text")?.text()
        val description = doc.selectFirst(".anime__details__text > p")?.text()
        val genres = doc.select("div.col-lg-6:nth-child(1) > ul:nth-child(1) > li:nth-child(2) > a")
            .map { it.text() }
        val status = when (doc.selectFirst("span.enemision")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "Concluido" -> ShowStatus.Completed
            else -> null
        }
        val animeID = doc.selectFirst("div.ml-2")?.attr("data-anime")?.toInt()
        val animeeps = "$mainUrl/ajax/last_episode/$animeID/"
        val jsoneps = app.get(animeeps).text
        val lastepnum =
            jsoneps.substringAfter("{\"number\":\"").substringBefore("\",\"title\"").toInt()
        val episodes = (1..lastepnum).map {
            val link = "${url.removeSuffix("/")}/$it"
            Episode(link)
        }

        return newAnimeLoadResponse(title!!, url, getType(type!!)) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    data class Nozomi(
        @JsonProperty("file") val file: String?
    )

    private fun streamClean(
        name: String,
        url: String,
        referer: String,
        quality: String?,
        callback: (ExtractorLink) -> Unit,
        m3u8: Boolean
    ): Boolean {
        callback(
            ExtractorLink(
                name,
                name,
                url,
                referer,
                getQualityFromName(quality),
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
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("var video = []")) {
                val videos = script.data().replace("\\/", "/")
                fetchUrls(videos).map {
                    it.replace("$mainUrl/jkfembed.php?u=", "https://embedsito.com/v/")
                        .replace("$mainUrl/jkokru.php?u=", "http://ok.ru/videoembed/")
                        .replace("$mainUrl/jkvmixdrop.php?u=", "https://mixdrop.co/e/")
                        .replace("$mainUrl/jk.php?u=", "$mainUrl/")
                }.apmap { link ->
                    loadExtractor(link, data, subtitleCallback, callback)
                    if (link.contains("um2.php")) {
                        val doc = app.get(link, referer = data).document
                        val gsplaykey = doc.select("form input[value]").attr("value")
                        app.post(
                            "$mainUrl/gsplay/redirect_post.php",
                            headers = mapOf(
                                "Host" to "jkanime.net",
                                "User-Agent" to USER_AGENT,
                                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                                "Accept-Language" to "en-US,en;q=0.5",
                                "Referer" to link,
                                "Content-Type" to "application/x-www-form-urlencoded",
                                "Origin" to "https://jkanime.net",
                                "DNT" to "1",
                                "Connection" to "keep-alive",
                                "Upgrade-Insecure-Requests" to "1",
                                "Sec-Fetch-Dest" to "iframe",
                                "Sec-Fetch-Mode" to "navigate",
                                "Sec-Fetch-Site" to "same-origin",
                                "TE" to "trailers",
                                "Pragma" to "no-cache",
                                "Cache-Control" to "no-cache",
                            ),
                            data = mapOf(Pair("data", gsplaykey)),
                            allowRedirects = false
                        ).okhttpResponse.headers.values("location").apmap { loc ->
                            val postkey = loc.replace("/gsplay/player.html#", "")
                            val nozomitext = app.post(
                                "$mainUrl/gsplay/api.php",
                                headers = mapOf(
                                    "Host" to "jkanime.net",
                                    "User-Agent" to USER_AGENT,
                                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                                    "Accept-Language" to "en-US,en;q=0.5",
                                    "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                                    "X-Requested-With" to "XMLHttpRequest",
                                    "Origin" to "https://jkanime.net",
                                    "DNT" to "1",
                                    "Connection" to "keep-alive",
                                    "Sec-Fetch-Dest" to "empty",
                                    "Sec-Fetch-Mode" to "cors",
                                    "Sec-Fetch-Site" to "same-origin",
                                ),
                                data = mapOf(Pair("v", postkey)),
                                allowRedirects = false
                            ).text
                            val json = parseJson<Nozomi>(nozomitext)
                            val nozomiurl = listOf(json.file)
                            if (nozomiurl.isEmpty()) null else
                                nozomiurl.forEach { url ->
                                    val nozominame = "Nozomi"
                                    streamClean(
                                        nozominame,
                                        url!!,
                                        "",
                                        null,
                                        callback,
                                        url.contains(".m3u8")
                                    )
                                }
                        }
                    }
                    if (link.contains("um.php")) {
                        val desutext = app.get(link, referer = data).text
                        val desuRegex = Regex("((https:|http:)//.*\\.m3u8)")
                        val file = desuRegex.find(desutext)?.value
                        val namedesu = "Desu"
                        generateM3u8(
                            namedesu,
                            file!!,
                            mainUrl,
                        ).forEach { desurl ->
                            streamClean(
                                namedesu,
                                desurl.url,
                                mainUrl,
                                desurl.quality.toString(),
                                callback,
                                true
                            )
                        }
                    }
                    if (link.contains("jkmedia")) {
                        app.get(
                            link,
                            referer = data,
                            allowRedirects = false
                        ).okhttpResponse.headers.values("location").apmap { xtremeurl ->
                            val namex = "Xtreme S"
                            streamClean(
                                namex,
                                xtremeurl,
                                "",
                                null,
                                callback,
                                xtremeurl.contains(".m3u8")
                            )
                        }
                    }
                }
            }
        }
        return true
    }
}
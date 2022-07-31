package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*


class MonoschinosProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }
    }

    override var mainUrl = "https://monoschinos2.com"
    override var name = "Monoschinos"
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
            Pair("$mainUrl/emision", "En emisión"),
            Pair(
                "$mainUrl/animes?categoria=pelicula&genero=false&fecha=false&letra=false",
                "Peliculas"
            ),
            Pair("$mainUrl/animes", "Animes"),
        )

        val items = ArrayList<HomePageList>()

        items.add(
            HomePageList(
                "Capítulos actualizados",
                app.get(mainUrl, timeout = 120).document.select(".col-6").map {
                    val title = it.selectFirst("p.animetitles")?.text() ?: it.selectFirst(".animetitles")?.text() ?: ""
                    val poster = it.selectFirst(".animeimghv")!!.attr("data-src")
                    val epRegex = Regex("episodio-(\\d+)")
                    val url = it.selectFirst("a")?.attr("href")!!.replace("ver/", "anime/")
                        .replace(epRegex, "sub-espanol")
                    val epNum = (it.selectFirst(".positioning h5")?.text() ?: it.selectFirst("div.positioning p")?.text())?.toIntOrNull()
                    newAnimeSearchResponse(title, url) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(getDubStatus(title), epNum)
                    }
                })
        )

        for (i in urls) {
            try {
                val home = app.get(i.first, timeout = 120).document.select(".col-6").map {
                    val title = it.selectFirst(".seristitles")!!.text()
                    val poster = it.selectFirst("img.animemainimg")!!.attr("src")
                    newAnimeSearchResponse(title, fixUrl(it.selectFirst("a")!!.attr("href"))) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(getDubStatus(title))
                    }
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val search =
            app.get("$mainUrl/buscar?q=$query", timeout = 120).document.select(".col-6").map {
                val title = it.selectFirst(".seristitles")!!.text()
                val href = fixUrl(it.selectFirst("a")!!.attr("href"))
                val image = it.selectFirst("img.animemainimg")!!.attr("src")
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    fixUrl(image),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
        return ArrayList(search)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".chapterpic img")!!.attr("src")
        val title = doc.selectFirst(".chapterdetails h1")!!.text()
        val type = doc.selectFirst("div.chapterdetls2")!!.text()
        val description = doc.selectFirst("p.textComplete")!!.text().replace("Ver menos", "")
        val genres = doc.select(".breadcrumb-item a").map { it.text() }
        val status = when (doc.selectFirst("button.btn1")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select("div.col-item").map {
            val name = it.selectFirst("p.animetitles")!!.text()
            val link = it.selectFirst("a")!!.attr("href")
            val epThumb = it.selectFirst(".animeimghv")!!.attr("data-src")
            Episode(link, name, posterUrl = epThumb)
        }
        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div.playother p").forEach {
            val encodedurl = it.select("p").attr("data-player")
            val urlDecoded = base64Decode(encodedurl)
            val url = (urlDecoded).replace("https://monoschinos2.com/reproductor?url=", "")
            if (url.startsWith("https://www.fembed.com")) {
                val extractor = FEmbed()
                extractor.getUrl(url).forEach { link ->
                    callback.invoke(link)
                }
            } else {
                loadExtractor(url, mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
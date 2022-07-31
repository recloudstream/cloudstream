package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*


class DoramasYTProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.Movie
            else TvType.TvSeries
        }
        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }
    }

    override var mainUrl = "https://doramasyt.com"
    override var name = "DoramasYT"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/emision", "En emisión"),
            Pair(
                "$mainUrl/doramas?categoria=pelicula&genero=false&fecha=false&letra=false",
                "Peliculas"
            ),
            Pair("$mainUrl/doramas", "Doramas"),
            Pair(
                "$mainUrl/doramas?categoria=live-action&genero=false&fecha=false&letra=false",
                "Live Action"
            ),
        )

        val items = ArrayList<HomePageList>()

        items.add(
            HomePageList(
                "Capítulos actualizados",
                app.get(mainUrl, timeout = 120).document.select(".col-6").map {
                    val title = it.selectFirst("p")!!.text()
                    val poster = it.selectFirst(".chapter img")!!.attr("src")
                    val epRegex = Regex("episodio-(\\d+)")
                    val url = it.selectFirst("a")!!.attr("href").replace("ver/", "dorama/")
                        .replace(epRegex, "sub-espanol")
                    val epNum = it.selectFirst("h3")!!.text().toIntOrNull()
                    newAnimeSearchResponse(title,url) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(getDubStatus(title), epNum)
                    }
                })
        )

        for (i in urls) {
            try {
                val home = app.get(i.first, timeout = 120).document.select(".col-6").map {
                    val title = it.selectFirst(".animedtls p")!!.text()
                    val poster = it.selectFirst(".anithumb img")!!.attr("src")
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

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/buscar?q=$query", timeout = 120).document.select(".col-6").map {
            val title = it.selectFirst(".animedtls p")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst(".animes img")!!.attr("src")
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
        val poster = doc.selectFirst("div.flimimg img.img1")!!.attr("src")
        val title = doc.selectFirst("h1")!!.text()
        val type = doc.selectFirst("h4")!!.text()
        val description = doc.selectFirst("p.textComplete")!!.text().replace("Ver menos", "")
        val genres = doc.select(".nobel a").map { it.text() }
        val status = when (doc.selectFirst(".state h6")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select(".heromain .col-item").map {
            val name = it.selectFirst(".dtlsflim p")!!.text()
            val link = it.selectFirst("a")!!.attr("href")
            val epThumb = it.selectFirst(".flimimg img.img1")!!.attr("src")
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
        app.get(data).document.select("div.playother p").apmap {
            val encodedurl = it.select("p").attr("data-player")
            val urlDecoded = base64Decode(encodedurl)
            val url = (urlDecoded).replace("https://doramasyt.com/reproductor?url=", "")
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
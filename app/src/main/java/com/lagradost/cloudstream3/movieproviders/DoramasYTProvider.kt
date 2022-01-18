package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import java.util.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlin.collections.ArrayList



class DoramasYTProvider:MainAPI() {

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.ONA
            else if (t.contains("Pelicula")) TvType.Movie
            else TvType.TvSeries
        }
    }

    override val mainUrl: String
        get() = "https://doramasyt.com"
    override val name: String
        get() = "DoramasYT"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/emision", "En emisión"),
            Pair("$mainUrl/doramas?categoria=pelicula&genero=false&fecha=false&letra=false", "Peliculas"),
            Pair("$mainUrl/doramas", "Doramas"),
            Pair("$mainUrl/doramas?categoria=live-action&genero=false&fecha=false&letra=false", "Live Action"),
        )

        val items = ArrayList<HomePageList>()

        items.add(HomePageList("Capítulos actualizados", app.get(mainUrl, timeout = 120).document.select(".col-6").map{
            val title = it.selectFirst("p").text()
            val poster = it.selectFirst(".chapter img").attr("src")
            val epRegex = Regex("episodio-(\\d+)")
            val url = it.selectFirst("a").attr("href").replace("ver/","dorama/").replace(epRegex,"sub-espanol")
            val epNum = it.selectFirst("h3").text().toIntOrNull()
            AnimeSearchResponse(
                title,
                url,
                this.name,
                TvType.Anime,
                poster,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                subEpisodes = epNum,
                dubEpisodes = epNum,
            )
        }))

        for (i in urls) {
            try {

                val home = app.get(i.first, timeout = 120).document.select(".col-6").map {
                    val title = it.selectFirst(".animedtls p").text()
                    val poster = it.selectFirst(".anithumb img").attr("src")
                    AnimeSearchResponse(
                        title,
                        it.selectFirst("a").attr("href"),
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                    )
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
        val search = app.get("$mainUrl/buscar?q=$query", timeout = 120).document.select(".col-6").map {
            val title = it.selectFirst(".animedtls p").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst(".animes img").attr("src")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                image,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
            )
        }
        return ArrayList(search)
    }
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("div.flimimg img.img1").attr("src")
        val title = doc.selectFirst("h1").text()
        val type = doc.selectFirst("h4").text()
        val description = doc.selectFirst("p.textComplete").text().replace("Ver menos","")
        val genres = doc.select(".nobel a").map { it.text() }
        val status = when (doc.selectFirst(".state h6")?.text()) {
            "Estreno" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select(".heromain .col-item").map {
            val name = it.selectFirst(".dtlsflim p").text()
            val link = it.selectFirst("a").attr("href")
            val epThumb = it.selectFirst(".flimimg img.img1").attr("src")
            AnimeEpisode(link, name, posterUrl = epThumb)
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
            val url = (urlDecoded).replace("https://doramasyt.com/reproductor?url=", "")
            if (url.startsWith("https://www.fembed.com")) {
                val extractor = FEmbed()
                extractor.getUrl(url).forEach { link ->
                    callback.invoke(link)
                }
            } else {
                loadExtractor(url, mainUrl, callback)
            }
        }
        return true
    }
}
package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*

class AnimeflvnetProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Película")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }
    }

    override var mainUrl = "https://www3.animeflv.net"
    override var name = "Animeflv.net"
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
            Pair("$mainUrl/browse?type[]=movie&order=updated", "Películas"),
            Pair("$mainUrl/browse?status[]=2&order=default", "Animes"),
            Pair("$mainUrl/browse?status[]=1&order=rating", "En emision"),
        )
        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select("main.Main ul.ListEpisodios li").mapNotNull {
                    val title = it.selectFirst("strong.Title")?.text() ?: return@mapNotNull null
                    val poster = it.selectFirst("span img")?.attr("src") ?: return@mapNotNull null
                    val epRegex = Regex("(-(\\d+)\$)")
                    val url = it.selectFirst("a")?.attr("href")?.replace(epRegex, "")
                        ?.replace("ver/", "anime/") ?: return@mapNotNull null
                    val epNum =
                        it.selectFirst("span.Capi")?.text()?.replace("Episodio ", "")?.toIntOrNull()
                    newAnimeSearchResponse(title, url) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(getDubStatus(title), epNum)
                    }
                })
        )
        for ((url, name) in urls) {
            try {
                val doc = app.get(url).document
                val home = doc.select("ul.ListAnimes li article").mapNotNull {
                    val title = it.selectFirst("h3.Title")?.text() ?: return@mapNotNull null
                    val poster = it.selectFirst("figure img")?.attr("src") ?: return@mapNotNull null
                    newAnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                    ) {
                        this.posterUrl = fixUrl(poster)
                        addDubStatus(MonoschinosProvider.getDubStatus(title))
                    }
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class SearchObject(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("last_id") val lastId: String,
        @JsonProperty("slug") val slug: String
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "https://www3.animeflv.net/api/animes/search",
            data = mapOf(Pair("value", query))
        ).text
        val json = parseJson<List<SearchObject>>(response)
        return json.map { searchr ->
            val title = searchr.title
            val href = "$mainUrl/anime/${searchr.slug}"
            val image = "$mainUrl/uploads/animes/covers/${searchr.id}.jpg"
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                fixUrl(image),
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed
                ),
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val title = doc.selectFirst("h1.Title")!!.text()
        val poster = doc.selectFirst("div.AnimeCover div.Image figure img")?.attr("src")!!
        val description = doc.selectFirst("div.Description p")?.text()
        val type = doc.selectFirst("span.Type")?.text() ?: ""
        val status = when (doc.selectFirst("p.AnmStts span")?.text()) {
            "En emision" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val genre = doc.select("nav.Nvgnrs a")
            .map { it?.text()?.trim().toString() }

        doc.select("script").map { script ->
            if (script.data().contains("var episodes = [")) {
                val data = script.data().substringAfter("var episodes = [").substringBefore("];")
                data.split("],").forEach {
                    val epNum = it.removePrefix("[").substringBefore(",")
                    // val epthumbid = it.removePrefix("[").substringAfter(",").substringBefore("]")
                    val animeid = doc.selectFirst("div.Strs.RateIt")?.attr("data-id")
                    val epthumb = "https://cdn.animeflv.net/screenshots/$animeid/$epNum/th_3.jpg"
                    val link = url.replace("/anime/", "/ver/") + "-$epNum"
                    episodes.add(
                        Episode(
                            link,
                            null,
                            posterUrl = epthumb,
                            episode = epNum.toIntOrNull()
                        )
                    )
                }
            }
        }
        return newAnimeLoadResponse(title, url, getType(type)) {
            posterUrl = fixUrl(poster)
            addEpisodes(DubStatus.Subbed, episodes.reversed())
            showStatus = status
            plot = description
            tags = genre
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("var videos = {") || script.data()
                    .contains("var anime_id =") || script.data().contains("server")
            ) {
                val videos = script.data().replace("\\/", "/")
                fetchUrls(videos).map {
                    it.replace("https://embedsb.com/e/", "https://watchsb.com/e/")
                        .replace("https://ok.ru", "http://ok.ru")
                }.apmap {
                    loadExtractor(it, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}

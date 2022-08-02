package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class WcofunProvider : MainAPI() {
    override var mainUrl = "https://www.wcofun.com"
    override var name = "WCO Fun"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div#sidebar_right,div#sidebar_right2").forEach { block ->
            val header = block.previousElementSibling()?.ownText() ?: return@forEach
            val animes = block.select("ul.items li").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)

    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains(Regex("-season-[0-9]+-episode"))) && title.contains("-dubbed") -> title.substringBefore("-season")
                (title.contains(Regex("-season-[0-9]+-episode"))) && title.contains("-subbed") -> title.replace(Regex("-season-[0-9]+-episode-[0-9]+"), "")
                title.contains("-subbed") -> title.replace(Regex("-episode-[0-9]+"), "")
                title.contains("-dubbed") -> title.substringBefore("-episode")
                else -> title
            }
            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val header = this.selectFirst("div.recent-release-episodes a")?.text()
        val title = header?.trim() ?: return null
        val href = getProperAnimeLink(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val epNum = header.let { eps ->
            Regex("Episode\\s?([0-9]+)").find(eps)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        val isDub = header.contains("Dubbed")
        val isSub = header.contains("Subbed")
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(isDub, isSub, epNum, epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/search",
            referer = mainUrl,
            data = mapOf("catara" to query, "konuara" to "series")
        ).document

        return document.select("div#sidebar_right2 li").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("div.h1-tag a")?.text() ?: return null
        val eps = document.select("div#sidebar_right3 div.cat-eps")
        val type = if (eps.size == 1 || eps.first()?.text()
                ?.contains(Regex("Episode\\s?[0-9]+")) != true
        ) TvType.AnimeMovie else TvType.Anime
        val episodes = eps.map {
            val name = it.select("a").text()
            val link = it.selectFirst("a")!!.attr("href")
            Episode(link, name = name)
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = fixUrlNull(document.selectFirst("img.img5")?.attr("src"))
            addEpisodes(DubStatus.Subbed, episodes)
            plot = document.select("div#sidebar_cat > p").text()
            this.tags = document.select("div#sidebar_cat a").map { it.text() }
        }
    }

    private suspend fun getIframe(url: String): String? {
        val document = app.get(url).document
        val scriptData =
            document.select("script").find { it.data().contains("= \"\";") }?.data() ?: return null
        val subtractionNumber =
            Regex("""(?<=\.replace\(/\\D/g,''\)\) - )\d+""").find(scriptData)?.value?.toInt()
                ?: return null
        val html = Regex("""(?<=\["|, ").+?(?=")""").findAll(scriptData).map {
            val number = base64Decode(it.value).replace(Regex("\\D"), "").toInt()
            (number - subtractionNumber).toChar()
        }.joinToString("")
        return Jsoup.parse(html).select("iframe").attr("src").let { fixUrl(it) }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        getIframe(data)?.let { iframe ->
            val link = app.get(iframe, referer = data).text.let {
                fixUrlNull(
                    Regex("\"(/inc/embed/getvidlink.php.*)\"").find(it)?.groupValues?.getOrNull(
                        1
                    )
                )
            }
            app.get(
                link ?: return@let,
                referer = iframe,
                headers = mapOf("x-requested-with" to "XMLHttpRequest")
            ).parsedSafe<Sources>()?.let {
                listOf(
                    Pair(it.hd, "HD"),
                    Pair(it.enc, "SD")
                ).map { source ->
                    suspendSafeApiCall {
                        callback.invoke(
                            ExtractorLink(
                                "${this.name} ${source.second}",
                                "${this.name} ${source.second}",
                                "${it.server}/getvid?evid=${source.first}",
                                mainUrl,
                                if (source.second == "HD") Qualities.P720.value else Qualities.P480.value
                            )
                        )
                    }
                }
            }
        }

        return true
    }

    data class Sources(
        @JsonProperty("enc") val enc: String?,
        @JsonProperty("server") val server: String?,
        @JsonProperty("cdn") val cdn: String?,
        @JsonProperty("hd") val hd: String?,
    )


}
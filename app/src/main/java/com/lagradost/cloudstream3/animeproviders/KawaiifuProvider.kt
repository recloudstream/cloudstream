package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.*

class KawaiifuProvider : MainAPI() {
    override var mainUrl = "https://kawaiifu.com"
    override var name = "Kawaiifu"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val resp = app.get(mainUrl).text

        val soup = Jsoup.parse(resp)

        items.add(HomePageList("Latest Updates", soup.select(".today-update .item").mapNotNull {
            val title = it.selectFirst("img")?.attr("alt")
            AnimeSearchResponse(
                title ?: return@mapNotNull null,
                it.selectFirst("a")?.attr("href") ?: return@mapNotNull null,
                this.name,
                TvType.Anime,
                it.selectFirst("img")?.attr("src"),
                it.selectFirst("h4 > a")?.attr("href")?.split("-")?.last()?.toIntOrNull(),
                if (title.contains("(DUB)")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed
                ),
            )
        }))
        for (section in soup.select(".section")) {
            try {
                val title = section.selectFirst(".title")!!.text()
                val anime = section.select(".list-film > .item").mapNotNull { ani ->
                    val animTitle = ani.selectFirst("img")?.attr("alt")
                    AnimeSearchResponse(
                        animTitle ?: return@mapNotNull null,
                        ani.selectFirst("a")?.attr("href") ?: return@mapNotNull null,
                        this.name,
                        TvType.Anime,
                        ani.selectFirst("img")?.attr("src"),
                        ani.selectFirst(".vl-chil-date")?.text()?.toIntOrNull(),
                        if (animTitle.contains("(DUB)")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                            DubStatus.Subbed
                        ),
                    )
                }
                items.add(HomePageList(title, anime))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }


    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/search-movie?keyword=${query}"
        val html = app.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".item").mapNotNull {
            val year = it.selectFirst("h4 > a")?.attr("href")?.split("-")?.last()?.toIntOrNull()
            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val uri = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            AnimeSearchResponse(
                title,
                uri,
                this.name,
                TvType.Anime,
                poster,
                year,
                if (title.contains("(DUB)")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
            )
        })
    }

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        val title = soup.selectFirst(".title")!!.text()
        val tags = soup.select(".table a[href*=\"/tag/\"]").map { tag -> tag.text() }
        val description = soup.select(".sub-desc p")
            .filter { it -> it.select("strong").isEmpty() && it.select("iframe").isEmpty() }
            .joinToString("\n") { it.text() }
        val year = url.split("/").filter { it.contains("-") }[0].split("-")[1].toIntOrNull()

        val episodesLink = soup.selectFirst("a[href*=\".html-episode\"]")?.attr("href")
            ?: throw ErrorLoadingException("Error getting episode list")
        val episodes = Jsoup.parse(
            app.get(episodesLink).text
        ).selectFirst(".list-ep")?.select("li")?.map {
            Episode(
                it.selectFirst("a")!!.attr("href"),
                if (it.text().trim().toIntOrNull() != null) "Episode ${
                    it.text().trim()
                }" else it.text().trim()
            )
        }
        val poster = soup.selectFirst("a.thumb > img")?.attr("src")

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.year = year
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val htmlSource = app.get(data).text
        val soupa = Jsoup.parse(htmlSource)

        val episodeNum =
            if (data.contains("ep=")) data.split("ep=")[1].split("&")[0].toIntOrNull() else null

        val servers = soupa.select(".list-server").map {
            val serverName = it.selectFirst(".server-name")!!.text()
            val episodes = it.select(".list-ep > li > a")
                .map { episode -> Pair(episode.attr("href"), episode.text()) }
            val episode = if (episodeNum == null) episodes[0] else episodes.mapNotNull { ep ->
                if ((if (ep.first.contains("ep=")) ep.first.split("ep=")[1].split("&")[0].toIntOrNull() else null) == episodeNum) {
                    ep
                } else null
            }[0]
            Pair(serverName, episode)
        }.map {
            if (it.second.first == data) {
                val sources = soupa.select("video > source")
                    .map { source -> Pair(source.attr("src"), source.attr("data-quality")) }
                Triple(it.first, sources, it.second.second)
            } else {
                val html = app.get(it.second.first).text
                val soup = Jsoup.parse(html)

                val sources = soup.select("video > source")
                    .map { source -> Pair(source.attr("src"), source.attr("data-quality")) }
                Triple(it.first, sources, it.second.second)
            }
        }

        servers.forEach {
            it.second.forEach { source ->
                callback(
                    ExtractorLink(
                        "Kawaiifu",
                        it.first,
                        source.first,
                        "",
                        getQualityFromName(source.second),
                        source.first.contains(".m3u")
                    )
                )
            }
        }
        return true
    }
}

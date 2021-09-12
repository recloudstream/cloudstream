package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.*

class KawaiifuProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://kawaiifu.com"
    override val name: String
        get() = "Kawaiifu"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = true
    
    
    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Anime, TvType.AnimeMovie, TvType.ONA)


    override fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = Jsoup.parse(khttp.get(mainUrl).text)

        items.add(HomePageList("Latest Updates", soup.select(".today-update .item").map {
            AnimeSearchResponse(
                it.selectFirst("img").attr("alt"),
                it.selectFirst("a").attr("href"),
                this.name,
                TvType.Anime,
                it.selectFirst("img").attr("src"),
                it.selectFirst("h4 > a").attr("href").split("-").last().toIntOrNull(),
                null,
                EnumSet.of(DubStatus.Subbed),
                null,
                null
            )
        }))
        for (section in soup.select(".section")) {
            try {
                val title = section.selectFirst(".title").text()
                val anime = section.select(".list-film > .item").map { ani ->
                    AnimeSearchResponse(
                        ani.selectFirst("img").attr("alt"),
                        ani.selectFirst("a").attr("href"),
                        this.name,
                        TvType.Anime,
                        ani.selectFirst("img").attr("src"),
                        ani.selectFirst(".vl-chil-date").text().toIntOrNull(),
                        null,
                        EnumSet.of(DubStatus.Subbed),
                        null,
                        null
                    )
                }
                items.add(HomePageList(title, anime))

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if(items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }


    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/search-movie?keyword=${query}"
        val html = khttp.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".item").map {
            val year = it.selectFirst("h4 > a").attr("href").split("-").last().toIntOrNull()
            val title = it.selectFirst("img").attr("alt")
            val poster = it.selectFirst("img").attr("src")
            val uri = it.selectFirst("a").attr("href")
            AnimeSearchResponse(
                title,
                uri,
                this.name,
                TvType.Anime,
                poster,
                year,
                null,
                EnumSet.of(DubStatus.Subbed),
                null,
                null,
            )
        })
    }

    override fun load(url: String): LoadResponse {
        val html = khttp.get(url).text
        val soup = Jsoup.parse(html)

        val title = soup.selectFirst(".title").text()
        val tags = soup.select(".table a[href*=\"/tag/\"]").map { tag -> tag.text() }
        val description = soup.select(".sub-desc p")
            .filter { it.select("strong").isEmpty() && it.select("iframe").isEmpty() }.joinToString("\n") { it.text() }
        val year = url.split("/").filter { it.contains("-") }[0].split("-")[1].toIntOrNull()
        val episodes = Jsoup.parse(
            khttp.get(
                soup.selectFirst("a[href*=\".html-episode\"]").attr("href")
            ).text
        ).selectFirst(".list-ep").select("li").map {
            AnimeEpisode(
                it.selectFirst("a").attr("href"),
                if (it.text().trim().toIntOrNull() != null) "Episode ${it.text().trim()}" else it.text().trim()
            )
        }
        val poster = soup.selectFirst("a.thumb > img").attr("src")


        return AnimeLoadResponse(
            title,
            null,
            title,
            url,
            this.name,
            TvType.Anime,
            poster,
            year,
            null,
            episodes,
            ShowStatus.Ongoing,
            description,
            ArrayList(tags),
            ArrayList()
        )
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val htmlSource = khttp.get(data).text
        val soupa = Jsoup.parse(htmlSource)

        val episodeNum = if (data.contains("ep=")) data.split("ep=")[1].split("&")[0].toIntOrNull() else null

        val servers = soupa.select(".list-server").map {
            val serverName = it.selectFirst(".server-name").text()
            val episodes = it.select(".list-ep > li > a").map { episode ->  Pair(episode.attr("href"), episode.text()) }
            val episode = if (episodeNum == null) episodes[0] else episodes.mapNotNull { ep ->
                if ((if (ep.first.contains("ep=")) ep.first.split("ep=")[1].split("&")[0].toIntOrNull() else null) == episodeNum) {
                    ep
                } else null
            }[0]
            Pair(serverName, episode)
        }.map {
            if (it.second.first == data) {
                val sources = soupa.select("video > source").map { source -> Pair(source.attr("src"), source.attr("data-quality")) }
                Triple(it.first, sources, it.second.second)
            } else {
                val html = khttp.get(it.second.first).text
                val soup = Jsoup.parse(html)

                val sources = soup.select("video > source").map { source -> Pair(source.attr("src"), source.attr("data-quality")) }
                Triple(it.first, sources, it.second.second)
            }
        }

        servers.forEach {
            it.second.forEach { source ->
                callback(ExtractorLink(
                    "Kawaiifu",
                    "${it.first} - ${source.second}",
                    source.first,
                    "",
                    getQualityFromName(source.second),
                    source.first.contains(".m3u")
                ))
            }
        }
        return true
    }
}

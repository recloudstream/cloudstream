package com.lagradost.cloudstream3.movieproviders

import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Vidstream
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.util.*
import kotlin.collections.ArrayList


class VidEmbedProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://vidembed.cc"
    override val name: String
        get() = "VidEmbed"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = true

    private fun fixUrl(url: String): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("/")) {
            "$mainUrl$url"
        } else {
            url
        }
    }

    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.Anime, TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)

    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/search.html?keyword=$query"
        val html = khttp.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".listing.items > .video-block").map { li ->
            val href = fixUrl(li.selectFirst("a").attr("href"))
            val poster = li.selectFirst("img")?.attr("src")
            val title = li.selectFirst(".name").text()
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

            TvSeriesSearchResponse(
                if (!title.contains("Episode")) title else title.split("Episode")[0].trim(),
                href,
                this.name,
                TvType.TvSeries,
                poster, year,
                null
            )
        })
    }

    override fun load(url: String): LoadResponse? {
        val html = khttp.get(url).text
        val soup = Jsoup.parse(html)

        var title = soup.selectFirst("h1,h2,h3").text()
        title = if (!title.contains("Episode")) title else title.split("Episode")[0].trim()

        val description = soup.selectFirst(".post-entry")?.text()?.trim()
        var poster: String? = null

        val episodes = soup.select(".listing.items.lists > .video-block").withIndex().map { (index, li) ->
            val epTitle = if (li.selectFirst(".name") != null)
                if (li.selectFirst(".name").text().contains("Episode"))
                    "Episode " + li.selectFirst(".name").text().split("Episode")[1].trim()
                else
                    li.selectFirst(".name").text()
            else ""
            val epThumb = li.selectFirst("img")?.attr("src")
            val epDate = li.selectFirst(".meta > .date").text()

            if (poster == null) {
                poster = li.selectFirst("img")?.attr("onerror")?.split("=")?.get(1)?.replace(Regex("[';]"), "")
            }

            val epNum = Regex("""Episode (\d+)""").find(epTitle)?.destructured?.component1()?.toIntOrNull()

            TvSeriesEpisode(
                epTitle,
                null,
                epNum,
                fixUrl(li.selectFirst("a").attr("href")),
                epThumb,
                epDate
            )
        }.reversed()
        val year = if (episodes.isNotEmpty()) episodes.first().date?.split("-")?.get(0)?.toIntOrNull() else null

        val tvType = if (episodes.size == 1 && episodes[0].name == title) TvType.Movie else TvType.TvSeries

        return when (tvType) {
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
                    ShowStatus.Ongoing,
                    null,
                    null
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes[0].data,
                    poster,
                    year,
                    description,
                    null,
                    null
                )
            }
            else -> null
        }
    }

    override fun getMainPage(): HomePageResponse? {
        val urls = listOf(
            mainUrl,
            "$mainUrl/movies",
            "$mainUrl/series",
            "$mainUrl/recommended-series",
            "$mainUrl/cinema-movies"
        )
        val homePageList = ArrayList<HomePageList>()
        urls.pmap { url ->
            val response = khttp.get(url, timeout = 20.0)
            val document = Jsoup.parse(response.text)
            document.select("div.main-inner")?.forEach {
                val title = it.select(".widget-title").text().trim()
                val elements = it.select(".video-block").map {
                    val link = fixUrl(it.select("a").attr("href"))
                    val image = it.select(".picture > img").attr("src")
                    val name = it.select("div.name").text().trim()
                    val isSeries = (name.contains("Season") || name.contains("Episode"))

                    if (isSeries) {
                        TvSeriesSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.TvSeries,
                            image,
                            null,
                            null,
                        )
                    } else {
                        MovieSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.Movie,
                            image,
                            null,
                            null,
                        )
                    }
                }

                homePageList.add(
                    HomePageList(
                        title, elements
                    )
                )

            }

        }
        return HomePageResponse(homePageList)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val iframeLink = Jsoup.parse(khttp.get(data).text).selectFirst("iframe")?.attr("src") ?: return false
        val vidstreamObject = Vidstream("https://vidembed.cc")
        // https://vidembed.cc/streaming.php?id=MzUwNTY2&... -> MzUwNTY2
        val id = Regex("""id=([^&]*)""").find(iframeLink)?.groupValues?.get(1)

        if (id != null) {
            vidstreamObject.getUrl(id, isCasting, callback)
        }

        val html = khttp.get(fixUrl(iframeLink)).text
        val soup = Jsoup.parse(html)

        val servers = soup.select(".list-server-items > .linkserver").mapNotNull { li ->
            if (!li?.attr("data-video").isNullOrEmpty()) {
                Pair(li.text(), fixUrl(li.attr("data-video")))
            } else {
                null
            }
        }
        servers.forEach {
            if (it.first.toLowerCase(Locale.ROOT).trim() == "beta server") {
                // Group 1: link, Group 2: Label
                val sourceRegex = Regex("""sources:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")
                val trackRegex = Regex("""tracks:[\W\w]*?file:\s*["'](.*?)["'][\W\w]*?label:\s*["'](.*?)["']""")

                val html = khttp.get(it.second, headers = mapOf("referer" to iframeLink)).text
                sourceRegex.findAll(html).forEach { match ->
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            match.groupValues.getOrNull(2)?.let { "${this.name} $it" } ?: this.name,
                            match.groupValues[1],
                            it.second,
                            getQualityFromName(match.groupValues.getOrNull(2) ?: ""),
                            // Kinda risky
                            match.groupValues[1].endsWith(".m3u8"),
                        )
                    )
                }
                trackRegex.findAll(html).forEach { match ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            match.groupValues.getOrNull(2) ?: "Unknown",
                            match.groupValues[1]
                        )
                    )
                }
            }
        }

        return true
    }
}
package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.extractorApis
import org.jsoup.Jsoup
import java.util.*

class AnimeFlickProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.ONA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override val mainUrl = "https://animeflick.net"
    override val name = "AnimeFlick"
    override val hasQuickSearch = false
    override val hasMainPage = false

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.ONA
    )

    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "https://animeflick.net/search.php?search=$query"
        val html = app.get(link).text
        val doc = Jsoup.parse(html)

        return ArrayList(doc.select(".row.mt-2").map {
            val href = mainUrl + it.selectFirst("a").attr("href")
            val title = it.selectFirst("h5 > a").text()
            val poster = mainUrl + it.selectFirst("img").attr("src").replace("70x110", "225x320")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                getType(title),
                poster,
                null,
                EnumSet.of(DubStatus.Subbed),
            )
        })
    }

    override fun load(url: String): LoadResponse {
        val html = app.get(url).text
        val doc = Jsoup.parse(html)

        val poster = mainUrl + doc.selectFirst("img.rounded").attr("src")
        val title = doc.selectFirst("h2.title").text()

        val yearText = doc.selectFirst(".trending-year")?.text()
        val year = if (yearText != null) Regex("""(\d{4})""").find(yearText)?.destructured?.component1()
            ?.toIntOrNull() else null
        val description = doc.selectFirst("p").text()

        val genres = doc.select("a[href*=\"genre-\"]").map { it.text() }

        val episodes = doc.select("#collapseOne .block-space > .row > div:nth-child(2)").map {
            val name = it.selectFirst("a").text()
            val link = mainUrl + it.selectFirst("a").attr("href")
            AnimeEpisode(link, name)
        }.reversed()

        return newAnimeLoadResponse(title, url, getType(title)) {
            posterUrl = poster
            this.year = year

            addEpisodes(DubStatus.Subbed, episodes)

            plot = description
            tags = genres
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text

        val episodeRegex = Regex("""(https://.*?\.mp4)""")
        val links = episodeRegex.findAll(html).map {
            it.value
        }.toList()
        for (link in links) {
            var alreadyAdded = false
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        callback(it)
                    }
                    alreadyAdded = true
                    break
                }
            }
            if (!alreadyAdded) {
                callback(
                    ExtractorLink(
                        this.name,
                        "${this.name} - Auto",
                        link,
                        "",
                        Qualities.P1080.value
                    )
                )
            }
        }

        return true
    }
}

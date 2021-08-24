package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
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

    override val mainUrl: String
        get() = "https://animeflick.net"
    override val name: String
        get() = "AnimeFlick"
    override val hasQuickSearch: Boolean
        get() = false
    override val hasMainPage: Boolean
        get() = false

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.AnimeMovie,
            TvType.Anime,
            TvType.ONA
        )


    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "https://animeflick.net/search.php?search=$query"
        val html = khttp.get(link).text
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
                null,
                EnumSet.of(DubStatus.Subbed),
                null,
                null
            )
        })
    }

    override fun load(url: String): LoadResponse {
        val html = khttp.get(url).text
        val doc = Jsoup.parse(html)

        val poster = mainUrl + doc.selectFirst("img.rounded").attr("src")
        val title = doc.selectFirst("h2.title").text()

        val year = Regex("""(\d{4})""").find(doc.selectFirst(".trending-year").text())!!.destructured.component1().toIntOrNull()
        val description = doc.selectFirst("p").text()

        val genres = doc.select("a[href*=\"genre-\"]").map { it.text() }

        val episodes = doc.select("#collapseOne .block-space > .row > div:nth-child(2)").map {
            val name = it.selectFirst("a").text()
            val link = mainUrl +  it.selectFirst("a").attr("href")
            AnimeEpisode(link, name)
        }.reversed()

        return AnimeLoadResponse(
            title,
            null,
            title,
            url,
            this.name,
            getType(title),
            poster,
            year,
            null,
            episodes,
            ShowStatus.Ongoing,
            description,
            genres
        )
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = khttp.get(data).text

        val episodeRegex = Regex("""(https://.*?\.mp4)""")
        episodeRegex.findAll(html).map {
            it.value
        }.toList().forEach {
            callback(
                ExtractorLink(
                    "Animeflick",
                    "Animeflick - Auto",
                    it,
                    "",
                    getQualityFromName("1080")
                )
            )
        }
        return true
    }
}

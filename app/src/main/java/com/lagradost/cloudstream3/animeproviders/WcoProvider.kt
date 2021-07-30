package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.extractors.WcoStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.*
import kotlin.collections.ArrayList


class WcoProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.ONA
            else if (t.contains("Movie")) TvType.Movie
            else TvType.Anime
        }
    }

    override val mainUrl: String
        get() = "https://wcostream.cc"
    override val name: String
        get() = "WCO Stream"
    override val hasQuickSearch: Boolean
        get() = true


    private fun getSlug(href: String): String {
        return href.replace("$mainUrl/anime/", "").replace("/", "")
    }

    private fun fixAnimeLink(url: String): String {
        val regex = "watch/([a-zA-Z\\-0-9]*)-episode".toRegex()
        val (aniId) = regex.find(url)!!.destructured
        return "$mainUrl/anime/$aniId"
    }

    private fun parseSearchPage(soup: Document): ArrayList<SearchResponse> {
        val items = soup.select(".film_list-wrap > .flw-item")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (i in items) {
            val href = fixAnimeLink(i.selectFirst("a").attr("href"))
            val img = fixUrl(i.selectFirst("img").attr("data-src"))
            val title = i.selectFirst("img").attr("title")
            val isDub = !i.select(".pick.film-poster-quality").isEmpty()
            val year = i.selectFirst(".film-detail.film-detail-fix > div > span:nth-child(1)").text().toIntOrNull()
            val type = i.selectFirst(".film-detail.film-detail-fix > div > span:nth-child(3)").text()

            returnValue.add(
                if (getType(type) == TvType.Movie) {
                    MovieSearchResponse(
                        title, href, getSlug(href), this.name, TvType.Movie, img, year
                    )
                } else {
                    AnimeSearchResponse(
                        title,
                        href,
                        getSlug(href),
                        this.name,
                        TvType.Anime,
                        img,
                        year,
                        null,
                        EnumSet.of(if (isDub) DubStatus.Dubbed else DubStatus.Subbed),
                        null,
                        null
                    )
                }
            )
        }
        return returnValue
    }

    override fun search(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/search"
        val response = khttp.get(url, params=mapOf("keyword" to query))
        var document = Jsoup.parse(response.text)
        val returnValue = parseSearchPage(document)

        while (!document.select(".pagination").isEmpty()) {
            val link = document.select("a.page-link[rel=\"next\"]")
            if (!link.isEmpty()) {
                val extraResponse = khttp.get(fixUrl(link[0].attr("href")))
                document = Jsoup.parse(extraResponse.text)
                returnValue.addAll(parseSearchPage(document))
            } else {
                break
            }
        }
        return returnValue
    }

    override fun quickSearch(query: String): ArrayList<SearchResponse> {
        val returnValue: ArrayList<SearchResponse> = ArrayList()

        val response = khttp.post(
            "https://wcostream.cc/ajax/search",
            data=mapOf("keyword" to query)
        ).jsonObject.getString("html") // I won't make a dataclass for this shit
        val document = Jsoup.parse(response)

        document.select("a.nav-item").forEach {
            val title = it.selectFirst("img")?.attr("title").toString()
            val img = it?.selectFirst("img")?.attr("src")
            val href = it?.attr("href").toString()
            val isDub = title.contains("(Dub)")
            val filmInfo = it?.selectFirst(".film-infor")
            val year = filmInfo?.select("span")?.get(0)?.text()?.toIntOrNull()
            val type = filmInfo?.select("span")?.get(1)?.text().toString()
            if (title != "null") {
                returnValue.add(
                    if (getType(type) == TvType.Movie) {
                        MovieSearchResponse(
                            title, href, getSlug(href), this.name, TvType.Movie, img, year
                        )
                    } else {
                        AnimeSearchResponse(
                            title,
                            href,
                            getSlug(href),
                            this.name,
                            TvType.Anime,
                            img,
                            year,
                            null,
                            EnumSet.of(if (isDub) DubStatus.Dubbed else DubStatus.Subbed),
                            null,
                            null
                        )
                    }
                )
            }
        }
        return returnValue
    }

    override fun load(slug: String): LoadResponse {
        val url = "$mainUrl/anime/${slug}"

        val response = khttp.get(url, timeout = 120.0)
        val document = Jsoup.parse(response.text)

        val japaneseTitle = document.selectFirst("div.elements div.row > div:nth-child(1) > div.row-line:nth-child(1)")
            ?.text()?.trim()?.replace("Other names:", "")?.trim()

        val canonicalTitle = document.selectFirst("meta[name=\"title\"]")
            ?.attr("content")?.split("| W")?.get(0).toString()

        val isDubbed = canonicalTitle.contains("Dub")
        val episodeNodes = document.select(".tab-content .nav-item > a")

        val episodes = ArrayList<AnimeEpisode>(episodeNodes?.map {
            AnimeEpisode(it.attr("href"))
        }
            ?: ArrayList<AnimeEpisode>())
        val statusElem = document.selectFirst("div.elements div.row > div:nth-child(1) > div.row-line:nth-child(2)")
        val status = when (statusElem?.text()?.replace("Status:", "")?.trim()) {
            "Ongoing" -> ShowStatus.Ongoing
            "Completed" -> ShowStatus.Completed
            else -> null
        }
        val yearText = document.selectFirst("div.elements div.row > div:nth-child(2) > div.row-line:nth-child(4)")?.text()
        val year = yearText?.replace("Date release:", "")?.trim()?.split("-")?.get(0)?.toIntOrNull()

        val poster = document.selectFirst(".film-poster-img")?.attr("src")
        val type = document.selectFirst("span.item.mr-1 > a")?.text()?.trim()

        val synopsis = document.selectFirst(".description > p")?.text()?.trim()
        val genre = document.select("div.elements div.row > div:nth-child(1) > div.row-line:nth-child(5) > a").map { it?.text()?.trim().toString() }

        return AnimeLoadResponse(
            canonicalTitle,
            japaneseTitle,
            canonicalTitle,
            "$mainUrl/anime/${slug}",
            this.name,
            getType(type ?: ""),
            poster,
            year,
            if(isDubbed) episodes else null,
            if(!isDubbed) episodes else null,
            status,
            synopsis,
            ArrayList(genre),
            ArrayList(),
        )
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = khttp.get(data)
        val servers = Jsoup.parse(response.text).select("#servers-list > ul > li").map {
            mapOf(
                "link" to it?.selectFirst("a")?.attr("data-embed"),
                "title" to it?.selectFirst("span")?.text()?.trim()
            )
        }

        for (server in servers) {
            WcoStream().getSafeUrl(server["link"].toString(), "")?.forEach {
                callback.invoke(it)
            }
        }
        return true
    }
}

package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.post
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.network.url
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okio.Buffer
import org.jsoup.Jsoup

// referer = https://vf-serie.org, USERAGENT ALSO REQUIRED
class VfSerieProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://vf-serie.org"
    override val name: String
        get() = "vf-serie.org"

    override val lang: String = "fr"

    override val hasQuickSearch: Boolean
        get() = false

    override val hasMainPage: Boolean
        get() = false

    override val hasChromecastSupport: Boolean
        get() = false

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.TvSeries,
        )


    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val response = get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("ul.MovieList > li > article > a")
        if (items.isNullOrEmpty()) return ArrayList()

        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val href = item.attr("href")

            val poster = item.selectFirst("> div.Image > figure > img").attr("src").replace("//image", "https://image")

            if (poster == "$mainUrl/wp-content/themes/toroplay/img/cnt/noimg-thumbnail.png") {  // if the poster is missing (the item is just a redirect to something like https://vf-serie.org/series-tv/)
                continue
            }
            val name = item.selectFirst("> h3.Title").text()

            val year = item.selectFirst("> span.Year").text()?.toIntOrNull()

            returnValue.add(TvSeriesSearchResponse(name, href, this.name, TvType.TvSeries, poster, year, null))
        }
        return returnValue
    }





    private fun getDirect(original: String): String {  // original data, https://vf-serie.org/?trembed=1&trid=80467&trtype=2 for example
        val response = get(original).text
        val url = "iframe .*src=\\\"(.*?)\\\"".toRegex().find(response)?.groupValues?.get(1).toString()  // https://vudeo.net/embed-7jdb1t5b2mvo.html for example
        val vudoResponse = get(url).text
        val document = Jsoup.parse(vudoResponse)
        val vudoUrl = Regex("sources: \\[\"(.*?)\"\\]").find(document.html())?.groupValues?.get(1).toString()  // direct mp4 link, https://m5.vudeo.net/2vp3xgpw2avjdohilpfbtyuxzzrqzuh4z5yxvztral5k3rjnba6f4byj3saa/v.mp4 for exemple
        return vudoUrl
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "") return false

        val response = get(data).text
        val document = Jsoup.parse(response)
        val players = document.select("ul.TPlayerNv > li")
        val trembed_url = document.selectFirst("div.TPlayerTb > iframe").attr("src")
        var number_player = Regex(".*trembed=(.*?)&").find(trembed_url)?.groupValues?.get(1)!!.toInt()  // the starting trembed number of the first player website, some start at 0 other at 1
        var found = false
        for (player in players) {
            if (player.selectFirst("> span").text() == "Vudeo") {
                found = true
                break
            } else {
                number_player += 1
            }
        }
        if (found == false) {
            number_player = 1
        }
        val i = number_player.toString()
        val trid = Regex("iframe .*trid=(.*?)&").find(document.html())?.groupValues?.get(1)

        val data = getDirect("$mainUrl/?trembed=$i&trid=$trid&trtype=2")
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                data,
                "",
                Qualities.P720.value,
                false
            )
        )
        return true
    }


    override fun load(url: String): LoadResponse {
        val response = get(url).text
        val document = Jsoup.parse(response)
        val title = document?.selectFirst(".Title")?.text()?.replace("Regarder Serie ","")?.replace(" En Streaming", "")
            ?: throw ErrorLoadingException("Service might be unavailable")


        val year = document.select("span.Date").text()?.toIntOrNull()

        val rating = document.select("span.AAIco-star").text()?.toIntOrNull()

        val duration = document.select("span.Time").text()?.toIntOrNull()

        val backgroundPoster = document.selectFirst("div.Image > figure > img").attr("src").replace("//image", "https://image")

        val descript = document.selectFirst("div.Description > p").text()



        val list = ArrayList<Int>()

        // episode begin
        document.select(".Wdgt").forEach { element ->
            val season = element.selectFirst("> .AA-Season > span")?.text()?.toIntOrNull()
            if (season != null && season > 0) {
                list.add(season)
            }
        }
        if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

        val episodeList = ArrayList<TvSeriesEpisode>()

        for (season in list) {
            val episodes = document.select("table > tbody > tr")
            if (episodes.isNotEmpty()) {
                episodes.forEach { episode ->
                    val epNum = episode.selectFirst("> span.Num")?.text()?.toIntOrNull()
                    val poster = episode.selectFirst("> td.MvTbImg > a > img")?.attr("src")?.replace("//image", "https://image")
                    val aName = episode.selectFirst("> td.MvTbTtl > a")
                    val date = episode.selectFirst("> td.MvTbTtl > span")?.text()?.toString()
                    val name = aName.text()
                    val href = aName.attr("href")
                    episodeList.add(
                        TvSeriesEpisode(
                            name,
                            season,
                            epNum,
                            href,
                            poster,
                            date
                        )
                    )
                }
            }
        }
        return TvSeriesLoadResponse(
            title,
            url,
            this.name,
            TvType.TvSeries,
            episodeList,
            backgroundPoster,
            year,
            descript,
            null,
            null,
            rating
        )



    }
}

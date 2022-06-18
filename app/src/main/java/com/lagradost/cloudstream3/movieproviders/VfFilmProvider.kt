package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

// referer = https://vf-film.org, USERAGENT ALSO REQUIRED
class VfFilmProvider : MainAPI() {
    override var mainUrl = "https://vf-film.me"
    override var name = "vf-film.me"
    override var lang = "fr"
    override val hasQuickSearch = false
    override val hasMainPage = false
    override val hasChromecastSupport = false

    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("ul.MovieList > li > article > a")
        if (items.isNullOrEmpty()) return ArrayList()

        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val href = item.attr("href")

            val poster = item.selectFirst("> div.Image > figure > img")!!.attr("src")
                .replace("//image", "https://image")

            val name = item.selectFirst("> h3.Title")!!.text()

            val year = item.selectFirst("> span.Year")!!.text().toIntOrNull()

            returnValue.add(MovieSearchResponse(name, href, this.name, TvType.Movie, poster, year))
        }
        return returnValue
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.length <= 4) return false
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

    private suspend fun getDirect(original: String): String {  // original data, https://vf-film.org/?trembed=1&trid=55313&trtype=1 for example
        val response = app.get(original).text
        val url = "iframe .*src=\"(.*?)\"".toRegex().find(response)?.groupValues?.get(1)
            .toString()  // https://vudeo.net/embed-uweno86lzx8f.html for example
        val vudoResponse = app.get(url).text
        val document = Jsoup.parse(vudoResponse)
        val vudoUrl = Regex("sources: \\[\"(.*?)\"]").find(document.html())?.groupValues?.get(1)
            .toString()  // direct mp4 link, https://m11.vudeo.net/2vp3ukyw2avjdohilpebtzuct42q5jwvpmpsez3xjs6d7fbs65dpuey2rbra/v.mp4 for exemple
        return vudoUrl
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val title = document.selectFirst("div.SubTitle")?.text()
            ?: throw ErrorLoadingException("Service might be unavailable")

        val year = document.select("span.Date").text().toIntOrNull()

//        val rating = document.select("span.AAIco-star").text()

        val duration = document.select("span.Time").text().toIntOrNull()

        val poster = document.selectFirst("div.Image > figure > img")!!.attr("src")
            .replace("//image", "https://image")

        val descript = document.selectFirst("div.Description > p")!!.text()

        val players = document.select("ul.TPlayerNv > li")
        var number_player = 0
        var found = false
        for (player in players) {
            if (player.selectFirst("> span")!!.text() == "Vudeo") {
                found = true
                break
            } else {
                number_player += 1
            }
        }
        if (!found) {
            number_player = 0
        }
        val i = number_player.toString()
        val trid = Regex("iframe .*trid=(.*?)&").find(document.html())?.groupValues?.get(1)

        val data = getDirect("$mainUrl/?trembed=$i&trid=$trid&trtype=1")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            data
        ) {
            this.posterUrl = poster
            this.year = year
            this.plot = descript
            //this.rating = rating
            this.duration = duration
        }
    }
}

package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.util.*


class WatchCartoonOnlineProvider : MainAPI() {
    override var name = "WatchCartoonOnline"
    override var mainUrl = "https://www.wcostream.com"

    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.TvSeries
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://www.wcostream.com/search"

        var response =
            app.post(
                url,
                headers = mapOf("Referer" to url),
                data = mapOf("catara" to query, "konuara" to "series")
            ).text
        var document = Jsoup.parse(response)
        var items = document.select("div#blog > div.cerceve").toList()

        val returnValue = ArrayList<SearchResponse>()

        for (item in items) {
            val header = item.selectFirst("> div.iccerceve")
            val titleHeader = header!!.selectFirst("> div.aramadabaslik > a")
            val title = titleHeader!!.text()
            val href = fixUrl(titleHeader.attr("href"))
            val poster = fixUrl(header.selectFirst("> a > img")!!.attr("src"))
            val genreText = item.selectFirst("div.cerceve-tur-ve-genre")!!.ownText()
            if (genreText.contains("cartoon")) {
                returnValue.add(TvSeriesSearchResponse(title, href, this.name, TvType.Cartoon, poster, null, null))
            } else {
                val isDubbed = genreText.contains("dubbed")
                val set: EnumSet<DubStatus> =
                    EnumSet.of(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed)
                returnValue.add(
                    AnimeSearchResponse(
                        title,
                        href,
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        set,
                    )
                )
            }
        }

        // "episodes-search", is used for finding movies, anime episodes should be filtered out
        response =
            app.post(
                url,
                headers = mapOf("Referer" to url),
                data = mapOf("catara" to query, "konuara" to "episodes")
            ).text
        document = Jsoup.parse(response)
        items = document.select("#catlist-listview2 > ul > li")
            .filter { it -> it?.text() != null && !it.text().toString().contains("Episode") }

        for (item in items) {
            val titleHeader = item.selectFirst("a")
            val title = titleHeader!!.text()
            val href = fixUrl(titleHeader.attr("href"))
            //val isDubbed = title.contains("dubbed")
            //val set: EnumSet<DubStatus> =
            //   EnumSet.of(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed)
            returnValue.add(
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.AnimeMovie,
                    null,
                    null,
                    null,
                )
            )
        }

        return returnValue
    }

    override suspend fun load(url: String): LoadResponse {
        val isMovie = !url.contains("/anime/")
        val response = app.get(url).text
        val document = Jsoup.parse(response)

        return if (!isMovie) {
            val title = document.selectFirst("td.vsbaslik > h2")!!.text()
            val poster = fixUrlNull(document.selectFirst("div#cat-img-desc > div > img")?.attr("src"))
            val plot = document.selectFirst("div.iltext")!!.text()
            val genres = document.select("div#cat-genre > div.wcobtn > a").map { it.text() }
            val episodes = document.select("div#catlist-listview > ul > li > a").reversed().map {
                val text = it.text()
                val match = Regex("Season ([0-9]*) Episode ([0-9]*).*? (.*)").find(text)
                val href = it.attr("href")
                if (match != null) {
                    val last = match.groupValues[3]
                    return@map Episode(
                        href,
                        if (last.startsWith("English")) null else last,
                        match.groupValues[1].toIntOrNull(),
                        match.groupValues[2].toIntOrNull(),
                    )
                }
                val match2 = Regex("Episode ([0-9]*).*? (.*)").find(text)
                if (match2 != null) {
                    val last = match2.groupValues[2]
                    return@map Episode(
                        href,
                        if (last.startsWith("English")) null else last,
                        null,
                        match2.groupValues[1].toIntOrNull(),
                    )
                }
                return@map Episode(
                    href,
                    text
                )
            }
            TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                episodes,
                poster,
                null,
                plot,
                null,
                null,
                tags = genres
            )
        } else {
            val title = document.selectFirst(".iltext .Apple-style-span")?.text().toString()
            val b = document.select(".iltext b")
            val description = if (b.isNotEmpty()) {
                b.last()!!.html().split("<br>")[0]
            } else null

            TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                listOf(Episode(url,title)),
                null,
                null,
                description,
                null,
                null
            )
        }
    }

    data class LinkResponse(
        //  @JsonProperty("cdn")
        //  val cdn: String,
        @JsonProperty("enc")
        val enc: String,
        @JsonProperty("hd")
        val hd: String,
        @JsonProperty("server")
        val server: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data).text
        /*val embedUrl = fixUrl(
            Regex("itemprop=\"embedURL\" content=\"(.*?)\"").find(response.text)?.groupValues?.get(1) ?: return false
        )*/
        val start = response.indexOf("itemprop=\"embedURL")
        val foundJS = Regex("<script>(.*?)</script>").find(response, start)?.groupValues?.get(1)
            ?.replace("document.write", "var returnValue = ")

        val rhino = Context.enter()
        rhino.initStandardObjects()
        rhino.optimizationLevel = -1
        val scope: Scriptable = rhino.initStandardObjects()

        val decodeBase64 = "atob = function(s) {\n" +
                "    var e={},i,b=0,c,x,l=0,a,r='',w=String.fromCharCode,L=s.length;\n" +
                "    var A=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\";\n" +
                "    for(i=0;i<64;i++){e[A.charAt(i)]=i;}\n" +
                "    for(x=0;x<L;x++){\n" +
                "        c=e[s.charAt(x)];b=(b<<6)+c;l+=6;\n" +
                "        while(l>=8){((a=(b>>>(l-=8))&0xff)||(x<(L-2)))&&(r+=w(a));}\n" +
                "    }\n" +
                "    return r;\n" +
                "};"

        rhino.evaluateString(scope, decodeBase64 + foundJS, "JavaScript", 1, null)
        val jsEval = scope.get("returnValue", scope) ?: return false
        val src = fixUrl(Regex("src=\"(.*?)\"").find(jsEval as String)?.groupValues?.get(1) ?: return false)

        val embedResponse = app.get(
            (src),
            headers = mapOf("Referer" to data)
        )

        val getVidLink = fixUrl(
            Regex("get\\(\"(.*?)\"").find(embedResponse.text)?.groupValues?.get(1) ?: return false
        )
        val linkResponse = app.get(
            getVidLink, headers = mapOf(
                "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-fetch-dest" to "empty",
                "sec-fetch-mode" to "cors",
                "sec-fetch-site" to "same-origin",
                "accept" to "*/*",
                "x-requested-with" to "XMLHttpRequest",
                "referer" to src.replace(" ", "%20"),
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "cookie" to "countrytabs=0"
            )
        )

        val link = parseJson<LinkResponse>(linkResponse.text)

        val hdLink = "${link.server}/getvid?evid=${link.hd}"
        val sdLink = "${link.server}/getvid?evid=${link.enc}"

        if (link.hd.isNotBlank())
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name + " HD",
                    hdLink,
                    "",
                    Qualities.P720.value
                )
            )

        if (link.enc.isNotBlank())
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name + " SD",
                    sdLink,
                    "",
                    Qualities.P480.value
                )
            )

        return true
    }
}

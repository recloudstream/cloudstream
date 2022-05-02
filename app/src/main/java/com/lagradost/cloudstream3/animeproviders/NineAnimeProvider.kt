package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.*

class NineAnimeProvider : MainAPI() {
    override var mainUrl = "https://9anime.id"
    override var name = "9Anime"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    companion object {
        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("(dub)", ignoreCase = true)) {
                DubStatus.Dubbed
            } else {
                DubStatus.Subbed
            }
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val items = listOf(
            Pair("$mainUrl/ajax/home/widget?name=trending", "Trending"),
            Pair("$mainUrl/ajax/home/widget?name=updated_all", "All"),
            Pair("$mainUrl/ajax/home/widget?name=updated_sub&page=1", "Recently Updated (SUB)"),
            Pair(
                "$mainUrl/ajax/home/widget?name=updated_dub&page=1",
                "Recently Updated (DUB)(DUB)"
            ),
            Pair(
                "$mainUrl/ajax/home/widget?name=updated_chinese&page=1",
                "Recently Updated (Chinese)"
            ),
            Pair("$mainUrl/ajax/home/widget?name=random", "Random"),
        ).apmap { (url, name) ->
            val home = Jsoup.parse(
                app.get(
                    url
                ).parsed<Response>().html
            ).select("ul.anime-list li").map {
                val title = it.selectFirst("a.name")!!.text()
                val link = it.selectFirst("a")!!.attr("href")
                val poster = it.selectFirst("a.poster img")!!.attr("src")

                newAnimeSearchResponse(title, link) {
                    this.posterUrl = poster
                    addDubStatus(getDubStatus(title))
                }
            }

            HomePageList(name, home)
        }

        return HomePageResponse(items)
    }

    //Credits to https://github.com/jmir1
    private val key = "0wMrYU+ixjJ4QdzgfN2HlyIVAt3sBOZnCT9Lm7uFDovkb/EaKpRWhqXS5168ePcG"

    private fun getVrf(id: String): String? {
        val reversed = ue(encode(id) + "0000000").slice(0..5).reversed()

        return reversed + ue(je(reversed, encode(id) ?: return null)).replace(
            """=+$""".toRegex(),
            ""
        )
    }

    private fun getLink(url: String): String? {
        val i = url.slice(0..5)
        val n = url.slice(6..url.lastIndex)
        return decode(je(i, ze(n)))
    }

    private fun ue(input: String): String {
        if (input.any { it.code >= 256 }) throw Exception("illegal characters!")
        var output = ""
        for (i in input.indices step 3) {
            val a = intArrayOf(-1, -1, -1, -1)
            a[0] = input[i].code shr 2
            a[1] = (3 and input[i].code) shl 4
            if (input.length > i + 1) {
                a[1] = a[1] or (input[i + 1].code shr 4)
                a[2] = (15 and input[i + 1].code) shl 2
            }
            if (input.length > i + 2) {
                a[2] = a[2] or (input[i + 2].code shr 6)
                a[3] = 63 and input[i + 2].code
            }
            for (n in a) {
                if (n == -1) output += "="
                else {
                    if (n in 0..63) output += key[n]
                }
            }
        }
        return output;
    }

    private fun je(inputOne: String, inputTwo: String): String {
        val arr = IntArray(256) { it }
        var output = ""
        var u = 0
        var r: Int
        for (a in arr.indices) {
            u = (u + arr[a] + inputOne[a % inputOne.length].code) % 256
            r = arr[a]
            arr[a] = arr[u]
            arr[u] = r
        }
        u = 0
        var c = 0
        for (f in inputTwo.indices) {
            c = (c + f) % 256
            u = (u + arr[c]) % 256
            r = arr[c]
            arr[c] = arr[u]
            arr[u] = r
            output += (inputTwo[f].code xor arr[(arr[c] + arr[u]) % 256]).toChar()
        }
        return output
    }

    private fun ze(input: String): String {
        val t = if (input.replace("""[\t\n\f\r]""".toRegex(), "").length % 4 == 0) {
            input.replace(Regex("""/==?$/"""), "")
        } else input
        if (t.length % 4 == 1 || t.contains(Regex("""[^+/0-9A-Za-z]"""))) throw Exception("bad input")
        var i: Int
        var r = ""
        var e = 0
        var u = 0
        for (o in t.indices) {
            e = e shl 6
            i = key.indexOf(t[o])
            e = e or i
            u += 6
            if (24 == u) {
                r += ((16711680 and e) shr 16).toChar()
                r += ((65280 and e) shr 8).toChar()
                r += (255 and e).toChar()
                e = 0
                u = 0
            }
        }
        return if (12 == u) {
            e = e shr 4
            r + e.toChar()
        } else {
            if (18 == u) {
                e = e shr 2
                r += ((65280 and e) shr 8).toChar()
                r += (255 and e).toChar()
            }
            r
        }
    }

    private fun encode(input: String): String? = java.net.URLEncoder.encode(input, "utf-8")

    private fun decode(input: String): String? = java.net.URLDecoder.decode(input, "utf-8")

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/filter?sort=title%3Aasc&keyword=$query"

        return app.get(url).document.select("ul.anime-list li").mapNotNull {
            val title = it.selectFirst("a.name")!!.text()
            val href =
                fixUrlNull(it.selectFirst("a")!!.attr("href"))?.replace(Regex("(\\?ep=(\\d+)\$)"), "")
                    ?: return@mapNotNull null
            val image = it.selectFirst("a.poster img")!!.attr("src")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                image,
                null,
                if (title.contains("(DUB)") || title.contains("(Dub)")) EnumSet.of(
                    DubStatus.Dubbed
                ) else EnumSet.of(DubStatus.Subbed),
            )
        }
    }

    data class Response(
        @JsonProperty("html") val html: String
    )

    override suspend fun load(url: String): LoadResponse? {
        val validUrl = url.replace("https://9anime.to", mainUrl)
        val doc = app.get(validUrl).document
        val animeid = doc.selectFirst("div.player-wrapper.watchpage")!!.attr("data-id") ?: return null
        val animeidencoded = encode(getVrf(animeid) ?: return null)
        val poster = doc.selectFirst("aside.main div.thumb div img")!!.attr("src")
        val title = doc.selectFirst(".info .title")!!.text()
        val description = doc.selectFirst("div.info p")!!.text().replace("Ver menos", "").trim()
        val episodes = Jsoup.parse(
            app.get(
                "$mainUrl/ajax/anime/servers?ep=1&id=${animeid}&vrf=$animeidencoded&ep=8&episode=&token="
            ).parsed<Response>().html
        ).select("ul.episodes li a").mapNotNull {
            val link = it?.attr("href") ?: return@mapNotNull null
            val name = "Episode ${it.text()}"
            Episode(link, name)
        }

        val recommendations =
            doc.select("div.container aside.main section div.body ul.anime-list li")
                .mapNotNull { element ->
                    val recTitle = element.select("a.name").text() ?: return@mapNotNull null
                    val image = element.select("a.poster img").attr("src")
                    val recUrl = fixUrl(element.select("a").attr("href"))
                    newAnimeSearchResponse(recTitle, recUrl) {
                        this.posterUrl = image
                        addDubStatus(getDubStatus(recTitle))
                    }
                }

        val infodoc = doc.selectFirst("div.info .meta .col1")!!.text()
        val tvType = if (infodoc.contains("Movie")) TvType.AnimeMovie else TvType.Anime
        val status =
            if (infodoc.contains("Completed")) ShowStatus.Completed
            else if (infodoc.contains("Airing")) ShowStatus.Ongoing
            else null
        val tags = doc.select("div.info .meta .col1 div:contains(Genre) a").map { it.text() }

        return newAnimeLoadResponse(title, validUrl, tvType) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
            this.showStatus = status
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    data class Links(
        @JsonProperty("url") val url: String
    )

    data class Servers(
        @JsonProperty("28") val mcloud: String?,
        @JsonProperty("35") val mp4upload: String?,
        @JsonProperty("40") val streamtape: String?,
        @JsonProperty("41") val vidstream: String?,
        @JsonProperty("43") val videovard: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val animeid =
            document.selectFirst("div.player-wrapper.watchpage")!!.attr("data-id") ?: return false
        val animeidencoded = encode(getVrf(animeid) ?: return false)

        Jsoup.parse(
            app.get(
                "$mainUrl/ajax/anime/servers?&id=${animeid}&vrf=$animeidencoded&episode=&token="
            ).parsed<Response>().html
        ).select("div.body").map { element ->
            val jsonregex = Regex("(\\{.+\\}.*$data)")
            val servers = jsonregex.find(element.toString())?.value?.replace(
                Regex("(\".*data-base=.*href=\"$data)"),
                ""
            )?.replace("&quot;", "\"") ?: return@map

            val jsonservers = parseJson<Servers?>(servers) ?: return@map
            listOfNotNull(
                jsonservers.vidstream,
                jsonservers.mcloud,
                jsonservers.mp4upload,
                jsonservers.streamtape
            ).mapNotNull {
                try {
                    val epserver = app.get("$mainUrl/ajax/anime/episode?id=$it").text
                    (if (epserver.contains("url")) {
                        parseJson<Links>(epserver)
                    } else null)?.url?.let { it1 -> getLink(it1.replace("=", "")) }
                        ?.replace("/embed/", "/e/")
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }.apmap { url ->
                loadExtractor(
                    url, data, callback
                )
            }
        }

        return true
    }
}

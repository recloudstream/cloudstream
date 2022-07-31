package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class NineAnimeProvider : MainAPI() {
    override var mainUrl = "https://9anime.id"
    override var name = "9Anime"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)
    override val hasQuickSearch = true

    // taken from https://github.com/saikou-app/saikou/blob/b35364c8c2a00364178a472fccf1ab72f09815b4/app/src/main/java/ani/saikou/parsers/anime/NineAnime.kt
    // GNU General Public License v3.0 https://github.com/saikou-app/saikou/blob/main/LICENSE.md
    companion object {
        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("(dub)", ignoreCase = true)) {
                DubStatus.Dubbed
            } else {
                DubStatus.Subbed
            }
        }


        private const val nineAnimeKey =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private const val cipherKey = "rTKp3auwu0ULA6II"

        fun encodeVrf(text: String, mainKey: String): String {
            return encode(
                encrypt(
                    cipher(mainKey, encode(text)),
                    nineAnimeKey
                ).replace("""=+$""".toRegex(), "")
            )
        }

        fun decodeVrf(text: String, mainKey: String): String {
            return decode(cipher(mainKey, decrypt(text, nineAnimeKey)))
        }

        fun encrypt(input: String, key: String): String {
            if (input.any { it.code > 255 }) throw Exception("illegal characters!")
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
            return output
        }

        fun cipher(key: String, text: String): String {
            val arr = IntArray(256) { it }

            var u = 0
            var r: Int
            arr.indices.forEach {
                u = (u + arr[it] + key[it % key.length].code) % 256
                r = arr[it]
                arr[it] = arr[u]
                arr[u] = r
            }
            u = 0
            var c = 0

            return text.indices.map { j ->
                c = (c + 1) % 256
                u = (u + arr[c]) % 256
                r = arr[c]
                arr[c] = arr[u]
                arr[u] = r
                (text[j].code xor arr[(arr[c] + arr[u]) % 256]).toChar()
            }.joinToString("")
        }

        @Suppress("SameParameterValue")
        private fun decrypt(input: String, key: String): String {
            val t = if (input.replace("""[\t\n\f\r]""".toRegex(), "").length % 4 == 0) {
                input.replace("""==?$""".toRegex(), "")
            } else input
            if (t.length % 4 == 1 || t.contains("""[^+/0-9A-Za-z]""".toRegex())) throw Exception("bad input")
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

        fun encode(input: String): String =
            java.net.URLEncoder.encode(input, "utf-8").replace("+", "%20")

        private fun decode(input: String): String = java.net.URLDecoder.decode(input, "utf-8")
    }

    override val mainPage = mainPageOf(
        "$mainUrl/ajax/home/widget/trending?page=" to "Trending",
        "$mainUrl/ajax/home/widget/updated-all?page=" to "All",
        "$mainUrl/ajax/home/widget/updated-sub?page=" to "Recently Updated (SUB)",
        "$mainUrl/ajax/home/widget/updated-dub?page=" to "Recently Updated (DUB)",
        "$mainUrl/ajax/home/widget/updated-china?page=" to "Recently Updated (Chinese)",
        "$mainUrl/ajax/home/widget/random?page=" to "Random",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data + page
        val home = Jsoup.parse(
            app.get(
                url
            ).parsed<Response>().html
        ).select("div.item").mapNotNull { element ->
            val title = element.selectFirst(".info > .name") ?: return@mapNotNull null
            val link = title.attr("href")
            val poster = element.selectFirst(".poster > a > img")?.attr("src")
            val meta = element.selectFirst(".poster > a > .meta > .inner > .left")
            val subbedEpisodes = meta?.selectFirst(".sub")?.text()?.toIntOrNull()
            val dubbedEpisodes = meta?.selectFirst(".dub")?.text()?.toIntOrNull()

            newAnimeSearchResponse(title.text() ?: return@mapNotNull null, link) {
                this.posterUrl = poster
                addDubStatus(
                    dubbedEpisodes != null,
                    subbedEpisodes != null,
                    dubbedEpisodes,
                    subbedEpisodes
                )
            }
        }

        return newHomePageResponse(request.name, home)
    }

    data class Response(
        @JsonProperty("result") val html: String
    )

    data class QuickSearchResponse(
        //@JsonProperty("status") val status: Int? = null,
        @JsonProperty("result") val result: QuickSearchResult? = null,
        //@JsonProperty("message") val message: String? = null,
        //@JsonProperty("messages") val messages: ArrayList<String> = arrayListOf()
    )

    data class QuickSearchResult(
        @JsonProperty("html") val html: String? = null,
        //@JsonProperty("linkMore") val linkMore: String? = null
    )

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val vrf = encodeVrf(query, cipherKey)
        val url =
            "$mainUrl/ajax/anime/search?keyword=$query&vrf=$vrf"
        val response = app.get(url).parsedSafe<QuickSearchResponse>()
        val document = Jsoup.parse(response?.result?.html ?: return null)
        return document.select(".items > a").mapNotNull { element ->
            val link = fixUrl(element?.attr("href") ?: return@mapNotNull null)
            val title = element.selectFirst(".info > .name")?.text() ?: return@mapNotNull null
            newAnimeSearchResponse(title, link) {
                posterUrl = element.selectFirst(".poster > span > img")?.attr("src")
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val vrf = encodeVrf(query, cipherKey)
        //?language%5B%5D=${if (selectDub) "dubbed" else "subbed"}&
        val url =
            "$mainUrl/filter?keyword=${encode(query)}&vrf=${vrf}&page=1"
        return app.get(url).document.select("#list-items div.ani.poster.tip > a").mapNotNull {
            val link = fixUrl(it.attr("href") ?: return@mapNotNull null)
            val img = it.select("img")
            val title = img.attr("alt")
            newAnimeSearchResponse(title, link) {
                posterUrl = img.attr("src")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val validUrl = url.replace("https://9anime.to", mainUrl)
        val doc = app.get(validUrl).document

        val meta = doc.selectFirst("#w-info") ?: throw ErrorLoadingException("Could not find info")
        val ratingElement = meta.selectFirst(".brating > #w-rating")
        val id = ratingElement?.attr("data-id") ?: throw ErrorLoadingException("Could not find id")
        val binfo =
            meta.selectFirst(".binfo") ?: throw ErrorLoadingException("Could not find binfo")
        val info = binfo.selectFirst(".info") ?: throw ErrorLoadingException("Could not find info")

        val title = (info.selectFirst(".title") ?: info.selectFirst(".d-title"))?.text()
            ?: throw ErrorLoadingException("Could not find title")

        val vrf = encodeVrf(id, cipherKey)
        val req = app.get("$mainUrl/ajax/episode/list/$id?vrf=$vrf")
        val body = req.parsedSafe<Response>()?.html
            ?: throw ErrorLoadingException("Could not parse json with cipherKey=$cipherKey code=${req.code}")

        val subEpisodes = ArrayList<Episode>()
        val dubEpisodes = ArrayList<Episode>()

        //TODO RECOMMENDATIONS

        Jsoup.parse(body).body().select(".episodes > ul > li > a").mapNotNull { element ->
            val ids = element.attr("data-ids").split(",", limit = 2)

            val epNum = element.attr("data-num")
                .toIntOrNull() // might fuck up on 7.5 ect might use data-slug instead
            val epTitle = element.selectFirst("span.d-title")?.text()
            //val filler = element.hasClass("filler")
            ids.getOrNull(1)?.let { dub ->
                dubEpisodes.add(
                    Episode(
                        "$mainUrl/ajax/server/list/$dub?vrf=${encodeVrf(dub, cipherKey)}",
                        epTitle,
                        episode = epNum
                    )
                )
            }
            ids.getOrNull(0)?.let { sub ->
                subEpisodes.add(
                    Episode(
                        "$mainUrl/ajax/server/list/$sub?vrf=${encodeVrf(sub, cipherKey)}",
                        epTitle,
                        episode = epNum
                    )
                )
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            addEpisodes(DubStatus.Dubbed, dubEpisodes)
            addEpisodes(DubStatus.Subbed, subEpisodes)

            plot = info.selectFirst(".synopsis > .shorting > .content")?.text()
            posterUrl = binfo.selectFirst(".poster > span > img")?.attr("src")
            rating = ratingElement.attr("data-score").toFloat().times(1000f).toInt()

            info.select(".bmeta > .meta > div").forEach { element ->
                when (element.ownText()) {
                    "Genre: " -> {
                        tags = element.select("span > a").mapNotNull { it?.text() }
                    }
                    "Duration: " -> {
                        duration = getDurationFromString(element.selectFirst("span")?.text())
                    }
                    "Type: " -> {
                        type = when (element.selectFirst("span > a")?.text()) {
                            "ONA" -> TvType.OVA
                            else -> {
                                type
                            }
                        }
                    }
                    "Status: " -> {
                        showStatus = when (element.selectFirst("span")?.text()) {
                            "Releasing" -> ShowStatus.Ongoing
                            "Completed" -> ShowStatus.Completed
                            else -> {
                                showStatus
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    data class Result(
        @JsonProperty("url")
        val url: String? = null
    )

    data class Links(
        @JsonProperty("result")
        val result: Result? = null
    )

    //TODO 9anime outro into {"status":200,"result":{"url":"","skip_data":{"intro_begin":67,"intro_end":154,"outro_begin":1337,"outro_end":1415,"count":3}},"message":"","messages":[]}
    private suspend fun getEpisodeLinks(id: String): Links? {
        return app.get("$mainUrl/ajax/server/$id?vrf=${encodeVrf(id, cipherKey)}").parsedSafe()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val body = app.get(data).parsed<Response>().html
        val document = Jsoup.parse(body)

        document.select("li").apmap {
            try {
                val name = it.text()
                val encodedStreamUrl =
                    getEpisodeLinks(it.attr("data-link-id"))?.result?.url ?: return@apmap
                val url = decodeVrf(encodedStreamUrl, cipherKey)
                if (!loadExtractor(url, mainUrl, subtitleCallback, callback)) {
                    callback(
                        ExtractorLink(
                            this.name,
                            name,
                            url,
                            mainUrl,
                            Qualities.Unknown.value,
                            url.contains(".m3u8")
                        )
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        return true
    }
}

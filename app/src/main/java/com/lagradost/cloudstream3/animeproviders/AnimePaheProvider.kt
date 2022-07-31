package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.Jsoup
import kotlin.math.pow

class AnimePaheProvider : MainAPI() {
    // credit to https://github.com/justfoolingaround/animdl/tree/master/animdl/core/codebase/providers/animepahe
    companion object {
        const val MAIN_URL = "https://animepahe.com"

        var cookies: Map<String, String> = mapOf()
        private fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        suspend fun generateSession(): Boolean {
            if (cookies.isNotEmpty()) return true
            return try {
                val response = app.get("$MAIN_URL/")
                cookies = response.cookies
                true
            } catch (e: Exception) {
                false
            }
        }

        val YTSM = Regex("ysmm = '([^']+)")

        val KWIK_PARAMS_RE = Regex("""\("(\w+)",\d+,"(\w+)",(\d+),(\d+),\d+\)""")
        val KWIK_D_URL = Regex("action=\"([^\"]+)\"")
        val KWIK_D_TOKEN = Regex("value=\"([^\"]+)\"")
        val YOUTUBE_VIDEO_LINK =
            Regex("""(^(?:https?:)?(?://)?(?:www\.)?(?:youtu\.be/|youtube(?:-nocookie)?\.(?:[A-Za-z]{2,4}|[A-Za-z]{2,3}\.[A-Za-z]{2})/)(?:watch|embed/|vi?/)*(?:\?[\w=&]*vi?=)?[^#&?/]{11}.*${'$'})""")
    }

    override var mainUrl = MAIN_URL
    override var name = "AnimePahe"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        data class Data(
            @JsonProperty("id") val id: Int,
            @JsonProperty("anime_id") val animeId: Int,
            @JsonProperty("anime_title") val animeTitle: String,
            @JsonProperty("anime_slug") val animeSlug: String,
            @JsonProperty("episode") val episode: Int,
            @JsonProperty("snapshot") val snapshot: String,
            @JsonProperty("created_at") val createdAt: String,
            @JsonProperty("anime_session") val animeSession: String,
        )

        data class AnimePaheLatestReleases(
            @JsonProperty("total") val total: Int,
            @JsonProperty("data") val data: List<Data>
        )

        val urls = listOf(
            Pair("$mainUrl/api?m=airing&page=1", "Latest Releases"),
        )

        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val response = app.get(i.first).text
                val episodes = parseJson<AnimePaheLatestReleases>(response).data.map {
                    newAnimeSearchResponse(
                        it.animeTitle,
                        "https://pahe.win/a/${it.animeId}?slug=${it.animeTitle}",
                        fix = false
                    ) {
                        this.posterUrl = it.snapshot
                        addDubStatus(DubStatus.Subbed, it.episode)
                    }
                }

                items.add(HomePageList(i.second, episodes))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    data class AnimePaheSearchData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("status") val status: String,
        @JsonProperty("season") val season: String,
        @JsonProperty("year") val year: Int,
        @JsonProperty("score") val score: Double,
        @JsonProperty("poster") val poster: String,
        @JsonProperty("session") val session: String,
        @JsonProperty("relevance") val relevance: String
    )

    data class AnimePaheSearch(
        @JsonProperty("total") val total: Int,
        @JsonProperty("data") val data: List<AnimePaheSearchData>
    )

    private suspend fun getAnimeByIdAndTitle(title: String, animeId: Int): String? {
        val url = "$mainUrl/api?m=search&l=8&q=$title"
        val headers = mapOf("referer" to "$mainUrl/")

        val req = app.get(url, headers = headers).text
        val data = parseJson<AnimePaheSearch>(req)
        for (anime in data.data) {
            if (anime.id == animeId) {
                return "https://animepahe.com/anime/${anime.session}"
            }
        }
        return null
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api?m=search&l=8&q=$query"
        val headers = mapOf("referer" to "$mainUrl/")

        val req = app.get(url, headers = headers).text
        val data = parseJson<AnimePaheSearch>(req)

        return data.data.map {
            newAnimeSearchResponse(
                it.title,
                "https://pahe.win/a/${it.id}?slug=${it.title}",
                fix = false
            ) {
                this.posterUrl = it.poster
                addDubStatus(DubStatus.Subbed, it.episodes)
            }
        }
    }

    private data class AnimeData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("anime_id") val animeId: Int,
        @JsonProperty("episode") val episode: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("snapshot") val snapshot: String,
        @JsonProperty("session") val session: String,
        @JsonProperty("filler") val filler: Int,
        @JsonProperty("created_at") val createdAt: String
    )

    private data class AnimePaheAnimeData(
        @JsonProperty("total") val total: Int,
        @JsonProperty("per_page") val perPage: Int,
        @JsonProperty("current_page") val currentPage: Int,
        @JsonProperty("last_page") val lastPage: Int,
        @JsonProperty("next_page_url") val nextPageUrl: String?,
        @JsonProperty("prev_page_url") val prevPageUrl: String?,
        @JsonProperty("from") val from: Int,
        @JsonProperty("to") val to: Int,
        @JsonProperty("data") val data: List<AnimeData>
    )

    private suspend fun generateListOfEpisodes(link: String): ArrayList<Episode> {
        try {
            val attrs = link.split('/')
            val id = attrs[attrs.size - 1].split("?")[0]

            val uri = "$mainUrl/api?m=release&id=$id&sort=episode_asc&page=1"
            val headers = mapOf("referer" to "$mainUrl/")

            val req = app.get(uri, headers = headers).text
            val data = parseJson<AnimePaheAnimeData>(req)

            val lastPage = data.lastPage
            val perPage = data.perPage
            val total = data.total
            var ep = 1
            val episodes = ArrayList<Episode>()

            fun getEpisodeTitle(k: AnimeData): String {
                return k.title.ifEmpty {
                    "Episode ${k.episode}"
                }
            }

            if (lastPage == 1 && perPage > total) {
                data.data.forEach {
                    episodes.add(
                        newEpisode("$mainUrl/api?m=links&id=${it.animeId}&session=${it.session}&p=kwik!!TRUE!!") {
                            addDate(it.createdAt)
                            this.name = getEpisodeTitle(it)
                            this.posterUrl = it.snapshot
                        }
                    )
                }
            } else {
                for (page in 0 until lastPage) {
                    for (i in 0 until perPage) {
                        if (ep <= total) {
                            episodes.add(
                                Episode(
                                    "$mainUrl/api?m=release&id=${id}&sort=episode_asc&page=${page + 1}&ep=${ep}!!FALSE!!"
                                )
                            )
                            ++ep
                        }
                    }
                }
            }
            return episodes
        } catch (e: Exception) {
            return ArrayList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return suspendSafeApiCall {
            val regex = Regex("""a/(\d+)\?slug=(.+)""")
            val (animeId, animeTitle) = regex.find(url)!!.destructured
            val link = getAnimeByIdAndTitle(animeTitle, animeId.toInt())!!

            val html = app.get(link).text
            val doc = Jsoup.parse(html)

            val japTitle = doc.selectFirst("h2.japanese")?.text()
            val poster = doc.selectFirst(".anime-poster a")?.attr("href")

            val tvType = doc.selectFirst("""a[href*="/anime/type/"]""")?.text()

            val trailer: String? = if (html.contains("https://www.youtube.com/watch")) {
                YOUTUBE_VIDEO_LINK.find(html)?.destructured?.component1()
            } else {
                null
            }

            val episodes = generateListOfEpisodes(url)
            val year = Regex("""<strong>Aired:</strong>[^,]*, (\d+)""")
                .find(html)!!.destructured.component1()
                .toIntOrNull()
            val status =
                when (Regex("""<strong>Status:</strong>[^a]*a href=["']/anime/(.*?)["']""")
                    .find(html)!!.destructured.component1()) {
                    "airing" -> ShowStatus.Ongoing
                    "completed" -> ShowStatus.Completed
                    else -> null
                }
            val synopsis = doc.selectFirst(".anime-synopsis")?.text()

            var anilistId: Int? = null
            var malId: Int? = null

            doc.select(".external-links > a").forEach { aTag ->
                val split = aTag.attr("href").split("/")

                if (aTag.attr("href").contains("anilist.co")) {
                    anilistId = split[split.size - 1].toIntOrNull()
                } else if (aTag.attr("href").contains("myanimelist.net")) {
                    malId = split[split.size - 1].toIntOrNull()
                }
            }

            newAnimeLoadResponse(animeTitle, url, getType(tvType.toString())) {
                engName = animeTitle
                japName = japTitle

                this.posterUrl = poster
                this.year = year

                addEpisodes(DubStatus.Subbed, episodes)
                this.showStatus = status
                plot = synopsis
                tags = if (!doc.select(".anime-genre > ul a").isEmpty()) {
                    ArrayList(doc.select(".anime-genre > ul a").map { it.text().toString() })
                } else {
                    null
                }

                addMalId(malId)
                addAniListId(anilistId)
                addTrailer(trailer)
            }
        }
    }


    private fun isNumber(s: String?): Boolean {
        return s?.toIntOrNull() != null
    }

    private fun cookieStrToMap(cookie: String): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        for (string in cookie.split("; ")) {
            val split = string.split("=").toMutableList()
            val name = split.removeFirst().trim()
            val value = if (split.size == 0) {
                "true"
            } else {
                split.joinToString("=")
            }
            cookies[name] = value
        }
        return cookies.toMap()
    }

    private fun getString(content: String, s1: Int, s2: Int): String {
        val characterMap = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"

        val slice2 = characterMap.slice(0 until s2)
        var acc: Long = 0

        for ((n, i) in content.reversed().withIndex()) {
            acc += (when (isNumber("$i")) {
                true -> "$i".toLong()
                false -> "0".toLong()
            }) * s1.toDouble().pow(n.toDouble()).toInt()
        }

        var k = ""

        while (acc > 0) {
            k = slice2[(acc % s2).toInt()] + k
            acc = (acc - (acc % s2)) / s2
        }

        return when (k != "") {
            true -> k
            false -> "0"
        }
    }

    private fun decrypt(fullString: String, key: String, v1: Int, v2: Int): String {
        var r = ""
        var i = 0

        while (i < fullString.length) {
            var s = ""

            while (fullString[i] != key[v2]) {
                s += fullString[i]
                ++i
            }
            var j = 0

            while (j < key.length) {
                s = s.replace(key[j].toString(), j.toString())
                ++j
            }
            r += (getString(s, v2, 10).toInt() - v1).toChar()
            ++i
        }
        return r
    }

    private fun zipGen(gen: Sequence<Pair<Int, Int>>): ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        val allItems = gen.toList().toMutableList()
        val newList = ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>>()

        while (allItems.size > 1) {
            newList.add(Pair(allItems[0], allItems[1]))
            allItems.removeAt(0)
            allItems.removeAt(0)
        }
        return newList
    }

    private fun decodeAdfly(codedKey: String): String {
        var r = ""
        var j = ""

        for ((n, l) in codedKey.withIndex()) {
            if (n % 2 != 0) {
                j = l + j
            } else {
                r += l
            }
        }

        val encodedUri = ((r + j).toCharArray().map { it.toString() }).toMutableList()
        val numbers = sequence {
            for ((i, n) in encodedUri.withIndex()) {
                if (isNumber(n)) {
                    yield(Pair(i, n.toInt()))
                }
            }
        }

        for ((first, second) in zipGen(numbers)) {
            val xor = first.second.xor(second.second)
            if (xor < 10) {
                encodedUri[first.first] = xor.toString()
            }
        }
        var returnValue = String(encodedUri.joinToString("").toByteArray(), Charsets.UTF_8)
        returnValue = base64Decode(returnValue)
        return returnValue.slice(16..returnValue.length - 17)
    }

    private data class VideoQuality(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("audio") val audio: String?,
        @JsonProperty("kwik") val kwik: String?,
        @JsonProperty("kwik_pahewin") val kwikPahewin: String
    )

    private data class AnimePaheEpisodeLoadLinks(
        @JsonProperty("data") val data: List<Map<String, VideoQuality>>
    )

    private suspend fun bypassAdfly(adflyUri: String): String {
        if (!generateSession()) {
            return bypassAdfly(adflyUri)
        }

        var responseCode = 302
        var adflyContent: NiceResponse? = null
        var tries = 0

        while (responseCode != 200 && tries < 20) {
            adflyContent = app.get(
                app.get(adflyUri, cookies = cookies, allowRedirects = false).url,
                cookies = cookies,
                allowRedirects = false
            )
            cookies = cookies + adflyContent.cookies
            responseCode = adflyContent.code
            ++tries
        }
        if (tries > 19) {
            throw Exception("Failed to bypass adfly.")
        }
        return decodeAdfly(YTSM.find(adflyContent?.text.toString())!!.destructured.component1())
    }

    private suspend fun getStreamUrlFromKwik(url: String?): String? {
        if (url == null) return null
        val response =
            app.get(
                url,
                headers = mapOf("referer" to mainUrl),
                cookies = cookies
            ).text
        Regex("eval((.|\\n)*?)</script>").find(response)?.groupValues?.get(1)?.let { jsEval ->
            JsUnpacker("eval$jsEval").unpack()?.let { unPacked ->
                Regex("source=\'(.*?)\'").find(unPacked)?.groupValues?.get(1)?.let { link ->
                    return link
                }
            }
        }
        return null
    }

    private suspend fun getStreamUrlFromKwikAdfly(adflyUri: String): String {
        val fContent =
            app.get(
                bypassAdfly(adflyUri),
                headers = mapOf("referer" to "https://kwik.cx/"),
                cookies = cookies
            )
        cookies = cookies + fContent.cookies

        val (fullString, key, v1, v2) = KWIK_PARAMS_RE.find(fContent.text)!!.destructured
        val decrypted = decrypt(fullString, key, v1.toInt(), v2.toInt())
        val uri = KWIK_D_URL.find(decrypted)!!.destructured.component1()
        val tok = KWIK_D_TOKEN.find(decrypted)!!.destructured.component1()
        var content: NiceResponse? = null

        var code = 419
        var tries = 0

        while (code != 302 && tries < 20) {
            content = app.post(
                uri,
                allowRedirects = false,
                data = mapOf("_token" to tok),
                headers = mapOf("referer" to fContent.url),
                cookies = fContent.cookies
            )
            code = content.code
            ++tries
        }
        if (tries > 19) {
            throw Exception("Failed to extract the stream uri from kwik.")
        }
        return content?.headers?.values("location").toString()
    }

    private suspend fun extractVideoLinks(
        episodeLink: String,
        callback: (ExtractorLink) -> Unit
    ) {
        var link = episodeLink
        val headers = mapOf("referer" to "$mainUrl/")

        if (link.contains("!!TRUE!!")) {
            link = link.replace("!!TRUE!!", "")
        } else {
            val regex = """&ep=(\d+)!!FALSE!!""".toRegex()
            val episodeNum = regex.find(link)?.destructured?.component1()?.toIntOrNull()
            link = link.replace(regex, "")

            val req = app.get(link, headers = headers).text
            val jsonResponse = parseJson<AnimePaheAnimeData>(req)
            val ep = ((jsonResponse.data.map {
                if (it.episode == episodeNum) {
                    it
                } else {
                    null
                }
            }).filterNotNull())[0]
            link = "$mainUrl/api?m=links&id=${ep.animeId}&session=${ep.session}&p=kwik"
        }
        val req = app.get(link, headers = headers).text
        val data = mapper.readValue<AnimePaheEpisodeLoadLinks>(req)

        data.data.forEach {
            it.entries.toList().apmap { quality ->
                getStreamUrlFromKwik(quality.value.kwik)?.let { link ->
                    callback(
                        ExtractorLink(
                            "KWIK",
                            "KWIK - ${quality.key} [${quality.value.audio ?: "jpn"}]",
                            link,
                            "https://kwik.cx/",
                            getQualityFromName(quality.key),
                            link.contains(".m3u8")
                        )
                    )
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        extractVideoLinks(data, callback)
        return true
    }
}

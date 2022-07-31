package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class GomunimeProvider : MainAPI() {
    override var mainUrl = "https://185.231.223.76"
    override var name = "Gomunime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    private data class Response(
        @JsonProperty("status") val status: Boolean,
        @JsonProperty("html") val html: String
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("e", "Episode Baru"),
            Pair("c", "Completed"),
            Pair("la", "Live Action"),
            Pair("t", "Trending"),
        )

        val items = ArrayList<HomePageList>()

        for ((payload, name) in urls) {
            try {
                val home = Jsoup.parse(
                    parseJson<Response>(
                        app.post(
                            url = "$mainUrl/wp-admin/admin-ajax.php/wp-admin/admin-ajax.php",
                            headers = mapOf("Referer" to mainUrl),
                            data = mapOf("action" to "home_ajax", "fungsi" to payload, "pag" to "1")
                        ).text
                    ).html
                ).select("li").map {
                    val title = it.selectFirst("a.name")!!.text().trim()
                    val href = getProperAnimeLink(it.selectFirst("a")!!.attr("href"))
                    val posterUrl = it.selectFirst("img")!!.attr("src")
                    val type = getType(it.selectFirst(".taglist > span")!!.text().trim())
                    val epNum = it.select(".tag.ep").text().replace(Regex("[^0-9]"), "").trim()
                        .toIntOrNull()
                    newAnimeSearchResponse(title, href, type) {
                        this.posterUrl = posterUrl
                        addSub(epNum)
                    }
                }
                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("-episode")) {
            val href =
                "$mainUrl/anime/" + Regex("\\w\\d/(.*)-episode.*").find(uri)?.groupValues?.get(1)
                    .toString()
            when {
                href.contains("pokemon") -> href.replace(Regex("-[0-9]+"), "")
                else -> href
            }
        } else {
            uri
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select(".anime-list > li").map {
            val title = it.selectFirst("a.name")!!.text()
            val poster = it.selectFirst("img")!!.attr("src")
            val tvType = getType(it.selectFirst(".taglist > span")?.text().toString())
            val href = fixUrl(it.selectFirst("a.name")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    private data class EpisodeElement(
        @JsonProperty("data-index") val dataIndex: Long?,
        @JsonProperty("ep-num") val epNum: String?,
        @JsonProperty("ep-title") val epTitle: String?,
        @JsonProperty("ep-link") val epLink: String,
        @JsonProperty("ep-date") val epDate: String?
    )

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".entry-title")?.text().toString()
        val poster = document.selectFirst(".thumbposter > img")?.attr("data-lazy-src")
        val tags = document.select(".genxed > a").map { it.text() }

        val year = Regex("\\d, ([0-9]*)").find(
            document.select("time[itemprop = datePublished]").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(document.selectFirst(".spe > span")!!.ownText())
        val description = document.select("div[itemprop = description] > p").text()
        val trailer = document.selectFirst("div.embed-responsive noscript iframe")?.attr("src")
        val episodes = parseJson<List<EpisodeElement>>(
            Regex("var episodelist = (\\[.*])").find(
                document.select(".bixbox.bxcl.epcheck > script").toString().trim()
            )?.groupValues?.get(1).toString().replace(Regex("""\\"""), "").trim()
        ).map {
            val name =
                Regex("(Episode\\s?[0-9]+)").find(it.epTitle.toString())?.groupValues?.getOrNull(0)
                    ?: it.epTitle
            val link = it.epLink
            Episode(link, name)
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            addTrailer(trailer)
        }
    }

    data class MobiSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type") val type: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val scriptData = document.select("aside.sidebar > script").dataNodes().toString()
        val key = scriptData.substringAfter("var a_ray = '").substringBefore("';")
        val title = scriptData.substringAfter("var judul_postingan = \"").substringBefore("\";")

        val sources: List<Pair<String, String>> = app.post(
            url = "https://path.gomuni.me/app/vapi.php",
            data = mapOf("data" to key, "judul" to title, "func" to "mirror")
        ).document.select("div.gomunime-server-mirror").map {
            Pair(
                it.attr("data-vhash"),
                it.attr("data-type")
            )
        }

        sources.apmap {
            safeApiCall {
                when {
                    it.second.contains("frame") -> {
                        loadExtractor(it.first, data, subtitleCallback, callback)
                    }
                    it.second.contains("hls") -> {
                        app.post(
                            url = "https://path.gomuni.me/app/vapi.php",
                            data = mapOf("fid" to it.first, "func" to "hls")
                        ).text.let { link ->
                            M3u8Helper.generateM3u8(
                                this.name,
                                link,
                                "$mainUrl/",
                                headers = mapOf("Origin" to mainUrl)
                            ).forEach(callback)
                        }
                    }
                    it.second.contains("mp4") -> {
                        app.post(
                            url = "https://path.gomuni.me/app/vapi.php",
                            data = mapOf("data" to it.first, "func" to "blogs")
                        ).parsed<List<MobiSource>>().map {
                            callback.invoke(
                                ExtractorLink(
                                    source = name,
                                    name = "Mobi SD",
                                    url = it.file,
                                    referer = "$mainUrl/",
                                    quality = Qualities.P360.value
                                )
                            )
                        }
                    }
                    else -> null
                }
            }
        }

        return true
    }
}
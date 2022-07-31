package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class OtakudesuProvider : MainAPI() {
    override var mainUrl = "https://otakudesu.watch"
    override var name = "Otakudesu"
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

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.rseries").forEach { block ->
            val header = block.selectFirst("div.rvad > h1")!!.text().trim()
            val items = block.select("div.venz > ul > li").map {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val title = this.selectFirst("h2.jdlflm")!!.text().trim()
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = this.select("div.thumbz > img").attr("src").toString()
        val epNum = this.selectFirst("div.epz")?.ownText()?.replace(Regex("[^0-9]"), "")?.trim()
            ?.toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query&post_type=anime"
        val document = app.get(link).document

        return document.select("ul.chivsrc > li").map {
            val title = it.selectFirst("h2 > a")!!.ownText().trim()
            val href = it.selectFirst("h2 > a")!!.attr("href")
            val posterUrl = it.selectFirst("img")!!.attr("src").toString()
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.infozingle > p:nth-child(1) > span")?.ownText()
            ?.replace(":", "")?.trim().toString()
        val poster = document.selectFirst("div.fotoanime > img")?.attr("src")
        val tags = document.select("div.infozingle > p:nth-child(11) > span > a").map { it.text() }
        val type = getType(
            document.selectFirst("div.infozingle > p:nth-child(5) > span")?.ownText()
                ?.replace(":", "")?.trim().toString()
        )
        val year = Regex("\\d, ([0-9]*)").find(
            document.select("div.infozingle > p:nth-child(9) > span").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(
            document.selectFirst("div.infozingle > p:nth-child(6) > span")!!.ownText()
                .replace(":", "")
                .trim()
        )
        val description = document.select("div.sinopc > p").text()

        val episodes = document.select("div.episodelist")[1].select("ul > li").mapNotNull {
            val name = Regex("(Episode\\s?[0-9]+)").find(it.selectFirst("a")?.text().toString())?.groupValues?.getOrNull(0) ?: it.selectFirst("a")?.text()
            val link = fixUrl(it.selectFirst("a")!!.attr("href"))
            Episode(link, name)
        }.reversed()

        val recommendations =
            document.select("div.isi-recommend-anime-series > div.isi-konten").map {
                val recName = it.selectFirst("span.judul-anime > a")!!.text()
                val recHref = it.selectFirst("a")!!.attr("href")
                val recPosterUrl = it.selectFirst("a > img")?.attr("src").toString()
                newAnimeSearchResponse(recName, recHref, TvType.Anime) {
                    this.posterUrl = recPosterUrl
                }
            }

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }


    data class ResponseSources(
        @JsonProperty("id") val id: String,
        @JsonProperty("i") val i: String,
        @JsonProperty("q") val q: String,
    )

    data class ResponseData(
        @JsonProperty("data") val data: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val scriptData = document.select("script").last()?.data()
        val token = scriptData?.substringAfter("{action:\"")?.substringBefore("\"}").toString()

        val nonce = app.post("$mainUrl/wp-admin/admin-ajax.php", data = mapOf("action" to token))
            .parsed<ResponseData>().data
        val action = scriptData?.substringAfter(",action:\"")?.substringBefore("\"}").toString()

        val mirrorData = document.select("div.mirrorstream > ul > li").mapNotNull {
            base64Decode(it.select("a").attr("data-content"))
        }.toString()

        tryParseJson<List<ResponseSources>>(mirrorData)?.apmap { res ->
            val id = res.id
            val i = res.i
            val q = res.q

            var sources = Jsoup.parse(
                base64Decode(
                    app.post(
                        "${mainUrl}/wp-admin/admin-ajax.php", data = mapOf(
                            "id" to id,
                            "i" to i,
                            "q" to q,
                            "nonce" to nonce,
                            "action" to action
                        )
                    ).parsed<ResponseData>().data
                )
            ).select("iframe").attr("src")

            if (sources.startsWith("https://desustream.me")) {
                if (!sources.contains("/arcg/") && !sources.contains("/odchan/") && !sources.contains("/desudrive/")) {
                    sources = app.get(sources).document.select("iframe").attr("src")
                }
                if (sources.startsWith("https://yourupload.com")) {
                    sources = sources.replace("//", "//www.")
                }
            }

            loadExtractor(sources, data, subtitleCallback, callback)

        }

        return true
    }

}
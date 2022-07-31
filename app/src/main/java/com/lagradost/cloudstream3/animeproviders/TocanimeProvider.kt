package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import java.util.*

class TocanimeProvider : MainAPI() {
    override var mainUrl = "https://tocanime.co"
    override var name = "Tocanime"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("OVA") || t.contains("Special") -> TvType.OVA
                t.contains("Movie") -> TvType.AnimeMovie
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Đã hoàn thành" -> ShowStatus.Completed
                "Chưa hoàn thành" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div#playlists > div").forEach { block ->
            val header = block.selectFirst("h2")?.text()?.trim() ?: ""
            val items = block.select("div.col-lg-3.col-md-4.col-6").map {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val title = this.selectFirst("h3 a")?.text()?.trim() ?: ""
        val href = fixUrl(this.selectFirst("h3 a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("div.card-item-img")?.attr("data-original"))
        val epNum = this.selectFirst("div.card-item-badget.rtl")?.text()?.let { eps ->
            val num = eps.filter { it.isDigit() }.toIntOrNull()
            if(eps.contains("Preview")) {
                num?.minus(1)
            } else {
                num
            }
        }
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/content/search?t=kw&q=$query").document

        return document.select("div.col-lg-3.col-md-4.col-6").map {
            it.toSearchResult()
        }

    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text() ?: return null
        val type =
            if (document.select("div.me-list.scroller a").size == 1) TvType.AnimeMovie else TvType.Anime
        val episodes = document.select("div.me-list.scroller a").mapNotNull {
            Episode(fixUrl(it.attr("href")), it.text())
        }.reversed()
        val trailer =
            document.selectFirst("div#trailer script")?.data()?.substringAfter("<iframe src=\"")
                ?.substringBefore("\"")

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = fixUrlNull(document.selectFirst("img.img")?.attr("data-original"))
            year = document.select("dl.movie-des dd")[1].text().split("/").last().toIntOrNull()
            showStatus = getStatus(
                document.select("dl.movie-des dd")[0].text()
                    .toString()
            )
            plot = document.select("div.box-content > p").text()
            tags = document.select("dl.movie-des dd")[4].select("li")
                .map { it.select("a").text().removeSuffix(",").trim() }
            recommendations =
                document.select("div.col-lg-3.col-md-4.col-6").map { it.toSearchResult() }
            addEpisodes(DubStatus.Subbed, episodes)
            addTrailer(trailer)
        }
    }

    private fun encode(input: String): String? = java.net.URLEncoder.encode(input, "utf-8")

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(
            data,
            headers = mapOf("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"),
        ).document

        document.select("script").apmap { script ->
            if (script.data().contains("var PnPlayer=")) {
                val key = script.data().substringAfter("\"btsurl\":[[").substringBefore("]}]")
                    .replace("]", "").replace("\"", "").split(",")
                val id = data.split("_").last().substringBefore(".html")

                app.get(
                    url = "$mainUrl/content/parseUrl?v=2&len=0&prefer=&ts=${Date().time}&item_id=$id&username=$id&sv=btsurl&${
                        encode(
                            "bts_url[]"
                        )
                    }=${
                        encode(
                            key.first()
                        )
                    }&sig=${key.last()}",
                    referer = data,
                    headers = mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).parsedSafe<Responses>()?.let { res ->
                    callback.invoke(
                        ExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = res.formats?.auto ?: return@let,
                            referer = "$mainUrl/",
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                }

            }
        }

        return true
    }

    data class Formats(
        @JsonProperty("auto") val auto: String?,
    )

    data class Responses(
        @JsonProperty("formats") val formats: Formats?,
    )

}
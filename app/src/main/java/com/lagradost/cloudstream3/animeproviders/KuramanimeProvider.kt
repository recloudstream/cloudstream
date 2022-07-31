package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.*

class KuramanimeProvider : MainAPI() {
    override var mainUrl = "https://kuramanime.com"
    override var name = "Kuramanime"
    override val hasQuickSearch = false
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
                "Selesai Tayang" -> ShowStatus.Completed
                "Sedang Tayang" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.trending__product").forEach { block ->
            val header = block.selectFirst("h4")?.text().toString()
            val animes = block.select("div.col-lg-4.col-md-6.col-sm-6").map {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        document.select("div.product__sidebar__view").forEach { block ->
            val header = block.selectFirst("h5")?.text().toString()
            val animes = block.select("div.product__sidebar__comment__item").map {
                it.toSearchResultView()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/episode")) {
            Regex("(.*)/episode/.+").find(uri)?.groupValues?.get(1).toString() + "/"
        } else {
            uri
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")!!.attr("href")))
        val title = this.selectFirst("h5 a")?.text().toString()
        val posterUrl = fixUrl(this.select("div.product__item__pic.set-bg").attr("data-setbg"))
        val episode = Regex("([0-9*])\\s?/").find(
            this.select("div.ep span").text()
        )?.groupValues?.getOrNull(1)?.toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(episode)
        }

    }

    private fun Element.toSearchResultView(): AnimeSearchResponse {
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")?.attr("href").toString()))
        val title = this.selectFirst("h5")?.text()?.trim().toString()
        val posterUrl = fixUrlNull(
            this.selectFirst("div.product__sidebar__comment__item__pic.set-bg")?.attr("data-setbg")
        )
        val episode =
            this.selectFirst("h5")?.nextElementSibling()?.text()?.replace(Regex("[^0-9]"), "")
                ?.toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(episode)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/anime?search=$query&order_by=oldest"
        val document = app.get(link).document

        return document.select(".product__item").mapNotNull {
            val title = it.selectFirst("div.product__item__text > h5")!!.text().trim()
            val poster = it.selectFirst("a > div")!!.attr("data-setbg")
            val tvType =
                getType(it.selectFirst(".product__item__text > ul > li")!!.text().toString())
            val href = fixUrl(it.selectFirst("a")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".anime__details__title > h3")!!.text().trim()
        val poster = document.selectFirst(".anime__details__pic")?.attr("data-setbg")
        val tags =
            document.select("div.anime__details__widget > div > div:nth-child(2) > ul > li:nth-child(1)")
                .text().trim().replace("Genre: ", "").split(", ")

        val year = Regex("[^0-9]").replace(
            document.select("div.anime__details__widget > div > div:nth-child(1) > ul > li:nth-child(5)")
                .text().trim().replace("Musim: ", ""), ""
        ).toIntOrNull()
        val status = getStatus(
            document.select("div.anime__details__widget > div > div:nth-child(1) > ul > li:nth-child(3)")
                .text().trim().replace("Status: ", "")
        )
        val description = document.select(".anime__details__text > p").text().trim()

        val episodes =
            Jsoup.parse(document.select("#episodeLists").attr("data-content")).select("a").map {
                val name = it.text().trim()
                val link = it.attr("href")
                Episode(link, name)
            }

        val recommendations = document.select("div#randomList > a").mapNotNull {
            val epHref = it.attr("href")
            val epTitle = it.select("h5.sidebar-title-h5.px-2.py-2").text()
            val epPoster = it.select(".product__sidebar__view__item.set-bg").attr("data-setbg")

            newAnimeSearchResponse(epTitle, epHref, TvType.Anime) {
                this.posterUrl = epPoster
                addDubStatus(dubExist = false, subExist = true)
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val servers = app.get(data).document
        servers.select("video#player > source").map {
            suspendSafeApiCall {
                val url = it.attr("src")
                val quality = it.attr("size").toInt()
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        url,
                        referer = "$mainUrl/",
                        quality = quality
                    )
                )
            }
        }

        return true
    }

}
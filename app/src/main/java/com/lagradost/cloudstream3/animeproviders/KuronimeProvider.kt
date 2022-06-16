package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.ArrayList

class KuronimeProvider : MainAPI() {
    override var mainUrl = "https://185.231.223.254"
    override var name = "Kuronime"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val lang = "id"
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

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select(".bixbox").forEach { block ->
            val header = block.select(".releases > h3").text().trim()
            val animes = block.select("article").mapNotNull {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title = when {
                (title.contains("-episode")) && !(title.contains("-movie")) -> Regex("nonton-(.+)-episode").find(
                    title
                )?.groupValues?.get(1).toString()
                (title.contains("-movie")) -> Regex("nonton-(.+)-movie").find(title)?.groupValues?.get(
                    1
                ).toString()
                else -> title
            }

            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = getProperAnimeLink(fixUrl(this.select("a").attr("href")))
        val title = this.select(".bsuxtt, .tt > h4").text().trim()
        val posterUrl = fixUrl(this.select("img").attr("src"))
        val epNum = this.select(".ep").text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true, subEpisodes = epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select("article.bs").mapNotNull {
            val title = it.selectFirst(".tt > h4")!!.text().trim()
            val poster = it.select("img").attr("src")
            val tvType = getType(it.selectFirst(".bt > span")?.text().toString())
            val href = getProperAnimeLink(fixUrl(it.selectFirst("a")!!.attr("href")))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(".entry-title")?.text().toString().trim()
        val poster = document.select("div[itemprop=image]").joinToString {
            it.select("img").attr("src")
        }
        val tags = document.select(".infodetail > ul > li:nth-child(2) > a").map { it.text() }
        val type = getType(
            document.selectFirst(".infodetail > ul > li:nth-child(7)")?.ownText()?.trim().toString()
        )
        val trailer = document.select("iframe.entered.lazyloaded").attr("src")
        val year = Regex("\\d, ([0-9]*)").find(
            document.select(".infodetail > ul > li:nth-child(5)").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(
            document.selectFirst(".infodetail > ul > li:nth-child(3)")!!.ownText()
                .replace(Regex("\\W"), "")
        )
        val description = document.select("span.const > p").text()

        val episodes = document.select("div.bixbox.bxcl > ul > li").map {
            val name = it.selectFirst("a")?.text()?.trim()?.replace("Episode", title)
            val link = it.selectFirst("a")!!.attr("href")
            Episode(link, name)
        }.reversed()

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = poster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            addTrailer(trailer)
            this.tags = tags
            trailers = listOf(trailer)
        }
    }

    private suspend fun invokeKuroSource(
        url: String,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document

        doc.select("script").map { script ->
            if (script.data().contains("function jalankan_jwp() {")) {
                val data = script.data()
                val doma = data.substringAfter("var doma = \"").substringBefore("\";")
                val token = data.substringAfter("var token = \"").substringBefore("\";")
                val pat = data.substringAfter("var pat = \"").substringBefore("\";")
                val link = "$doma$token$pat/index.m3u8"
                val quality =
                    Regex("\\d{3,4}p").find(doc.select("title").text())?.groupValues?.get(0)

                sourceCallback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        referer = "https://animeku.org/",
                        quality = getQualityFromName(quality),
                        headers = mapOf("Origin" to "https://animeku.org"),
                        isM3u8 = true
                    )
                )
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val sources = document.select(".mobius > .mirror > option").mapNotNull {
            fixUrl(Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src"))
        }

        sources.apmap {
            safeApiCall {
                when {
                    it.startsWith("https://animeku.org") -> invokeKuroSource(it, callback)
                    else -> loadExtractor(it, mainUrl, callback)
                }
            }
        }
        return true
    }

}
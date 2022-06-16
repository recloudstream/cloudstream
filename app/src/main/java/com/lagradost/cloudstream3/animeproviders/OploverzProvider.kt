package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.ArrayList

class OploverzProvider : MainAPI() {
    override var mainUrl = "https://oploverz.asia"
    override var name = "Oploverz"
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
            return when {
                t.contains("TV") -> TvType.Anime
                t.contains("Movie") -> TvType.AnimeMovie
                else -> TvType.OVA
            }
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

        document.select(".bixbox.bbnofrm").forEach { block ->
            val header = block.selectFirst("h3")!!.text().trim()
            val animes = block.select("article[itemscope=itemscope]").mapNotNull {
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
                (title.contains("-episode")) && !(title.contains("-ova")) -> Regex("(.+)-episode").find(
                    title
                )?.groupValues?.get(1).toString()
                (title.contains("-ova")) -> Regex("(.+)-ova").find(title)?.groupValues?.get(1)
                    .toString()
                else -> Regex("(.+)-subtitle").find(title)?.groupValues?.get(1).toString()
                    .replace(Regex("-\\d+"), "")
            }

            when {
                title.contains("overlord") -> {
                    title = title.replace("s", "season-")
                }
                title.contains("kaguya-sama") -> {
                    title = title.replace("s3", "ultra-romantic")
                }
            }

            "$mainUrl/anime/$title"
        }

    }

    private fun Element.toSearchResult(): SearchResponse {
        val href = getProperAnimeLink(this.selectFirst("a.tip")!!.attr("href"))
        val title = this.selectFirst("h2[itemprop=headline]")!!.text().trim()
        val posterUrl = fixUrl(this.selectFirst("img")!!.attr("src"))
        val type = getType(this.selectFirst(".eggtype, .typez")!!.text().trim())
        val epNum =
            this.selectFirst(".eggepisode, span.epx")!!.text().replace(Regex("[^0-9]"), "").trim()
                .toIntOrNull()

        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true, subEpisodes = epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select("article[itemscope=itemscope]").map {
            val title = it.selectFirst(".tt")!!.ownText().trim()
            val poster = it.selectFirst("img")!!.attr("src")
            val tvType = getType(it.selectFirst(".typez")?.text().toString())
            val href = fixUrl(it.selectFirst("a.tip")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")!!.text().trim()
        val poster = document.select(".thumb > img").attr("src")
        val tags = document.select(".genxed > a").map { it.text() }

        val year = Regex("\\d, ([0-9]*)").find(
            document.selectFirst(".info-content > .spe > span > time")!!.text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val status = getStatus(
            document.select(".info-content > .spe > span:nth-child(1)")
                .text().trim().replace("Status: ", "")
        )
        val typeCheck =
            when {
                document.select(".info-content > .spe > span:nth-child(5), .info-content > .spe > span")
                    .text().trim().contains("TV") -> "TV"
                document.select(".info-content > .spe > span:nth-child(5), .info-content > .spe > span")
                    .text().trim().contains("TV") -> "Movie"
                else -> "OVA"
            }
        val type = getType(typeCheck)
        val description = document.select(".entry-content > p").text().trim()

        val episodes = document.select(".eplister > ul > li").map {
            val name = it.select(".epl-title").text().trim()
            val link = fixUrl(it.select("a").attr("href"))
            Episode(link, name)
        }.reversed()

        val recommendations =
            document.select(".listupd > article[itemscope=itemscope]").mapNotNull { rec ->
                val epTitle = rec.selectFirst(".tt")!!.ownText().trim()
                val epPoster = rec.selectFirst("img")!!.attr("src")
                val epType = getType(rec.selectFirst(".typez")?.text().toString())
                val epHref = fixUrl(rec.selectFirst("a.tip")!!.attr("href"))

                newAnimeSearchResponse(epTitle, epHref, epType) {
                    this.posterUrl = epPoster
                    addDubStatus(dubExist = false, subExist = true)
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

    data class Source(
        @JsonProperty("play_url") val play_url: String,
        @JsonProperty("format_id") val format_id: Int
    )

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
            loadExtractor(it, data, callback)
        }

        return true
    }

}
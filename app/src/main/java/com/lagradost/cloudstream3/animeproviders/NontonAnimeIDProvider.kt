package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class NontonAnimeIDProvider : MainAPI() {
    override var mainUrl = "https://75.119.159.228"
    override var name = "NontonAnimeID"
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
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("section#postbaru").forEach { block ->
            val header = block.selectFirst("h2")!!.text().trim()
            val animes = block.select("article.animeseries").map {
                it.toSearchResult()
            }
            if (animes.isNotEmpty()) homePageList.add(HomePageList(header, animes))
        }

        document.select("aside#sidebar_right > div:nth-child(4)").forEach { block ->
            val header = block.selectFirst("h3")!!.ownText().trim()
            val animes = block.select("li.fullwdth").map {
                it.toSearchResultPopular()
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
            val fixTitle = Regex("(.*)-episode.*").find(title)?.groupValues?.getOrNull(1).toString()
            title = when {
                title.contains("utawarerumono-season-3") -> fixTitle.replace(
                    "season-3",
                    "futari-no-hakuoro"
                )
                title.contains("kingdom-season-4") -> fixTitle.replace("season-4", "4th-season")
                title.contains("maou-sama-season-2") -> fixTitle.replace("season-2", "2")
                title.contains("overlord-season-4") -> fixTitle.replace("season-4", "iv")
                title.contains("kyoushitsu-e-season-2") -> fixTitle.replace(
                    "kyoushitsu-e-season-2",
                    "kyoushitsu-e-tv-2nd-season"
                )
                title.contains("season-2") -> fixTitle.replace("season-2", "2nd-season")
                title.contains("season-3") -> fixTitle.replace("season-3", "3rd-season")
                title.contains("movie") -> title.substringBefore("-movie")
                else -> fixTitle
            }
            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")!!.attr("href")))
        val title = this.selectFirst("h3.title")!!.text()
        val posterUrl = fixUrl(this.select("img").attr("data-src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true)
        }

    }

    private fun Element.toSearchResultPopular(): AnimeSearchResponse {
        val href = getProperAnimeLink(fixUrl(this.selectFirst("a")!!.attr("href")))
        val title = this.select("h4").text().trim()
        val posterUrl = fixUrl(this.select("img").attr("data-src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addDubStatus(dubExist = false, subExist = true)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select(".result > ul > li").mapNotNull {
            val title = it.selectFirst("h2")!!.text().trim()
            val poster = it.selectFirst("img")!!.attr("src")
            val tvType = getType(
                it.selectFirst(".boxinfores > span.typeseries")!!.text().toString()
            )
            val href = fixUrl(it.selectFirst("a")!!.attr("href"))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addDubStatus(dubExist = false, subExist = true)
            }
        }
    }

    private data class EpResponse(
        @JsonProperty("posts") val posts: String?,
        @JsonProperty("max_page") val max_page: Int?,
        @JsonProperty("found_posts") val found_posts: Int?,
        @JsonProperty("content") val content: String
    )

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title.cs")!!.text().trim()
        val poster = document.selectFirst(".poster > img")?.attr("data-src")
        val tags = document.select(".tagline > a").map { it.text() }

        val year = Regex("\\d, ([0-9]*)").find(
            document.select(".bottomtitle > span:nth-child(5)").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val status = getStatus(
            document.select("span.statusseries").text().trim()
        )
        val type = getType(document.select("span.typeseries").text().trim())
        val rating = document.select("span.nilaiseries").text().trim().toIntOrNull()
        val description = document.select(".entry-content.seriesdesc > p").text().trim()
        val trailer = document.selectFirst("iframe#traileryt")?.attr("data-src")

        val episodes = if (document.select("button.buttfilter").isNotEmpty()) {
            val id = document.select("input[name=series_id]").attr("value")
            val numEp =
                document.selectFirst(".latestepisode > a")?.text()?.replace(Regex("[^0-9]"), "")
                    .toString()
            Jsoup.parse(
                app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "misha_number_of_results" to numEp,
                        "misha_order_by" to "date-DESC",
                        "action" to "mishafilter",
                        "series_id" to id
                    )
                ).parsed<EpResponse>().content
            ).select("li").map {
                val name = Regex("(Episode\\s?[0-9]+)").find(
                    it.selectFirst("a")?.text().toString()
                )?.groupValues?.getOrNull(0) ?: it.selectFirst("a")?.text()
                val link = fixUrl(it.selectFirst("a")!!.attr("href"))
                Episode(link, name)
            }.reversed()
        } else {
            document.select("ul.misha_posts_wrap2 > li").map {
                val name = Regex("(Episode\\s?[0-9]+)").find(
                    it.selectFirst("a")?.text().toString()
                )?.groupValues?.getOrNull(0) ?: it.selectFirst("a")?.text()
                val link = it.select("a").attr("href")
                Episode(link, name)
            }.reversed()
        }


        val recommendations = document.select(".result > li").mapNotNull {
            val epHref = it.selectFirst("a")!!.attr("href")
            val epTitle = it.selectFirst("h3")!!.text()
            val epPoster = it.select(".top > img").attr("data-src")

            newAnimeSearchResponse(epTitle, epHref, TvType.Anime) {
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
            this.rating = rating
            plot = description
            addTrailer(trailer)
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

        val document = app.get(data).document
        val sources = ArrayList<String>()

        document.select(".container1 > ul > li").apmap {
            val dataPost = it.attr("data-post")
            val dataNume = it.attr("data-nume")
            val dataType = it.attr("data-type")

            val iframe = app.post(
                url = "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "player_ajax",
                    "post" to dataPost,
                    "nume" to dataNume,
                    "type" to dataType
                )
            ).document.select("iframe").attr("src")

            sources.add(fixUrl(iframe))
        }

        sources.apmap {
            loadExtractor(it, "$mainUrl/", subtitleCallback, callback)
        }

        return true
    }
}

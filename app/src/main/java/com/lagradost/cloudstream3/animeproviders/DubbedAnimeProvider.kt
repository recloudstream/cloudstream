package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList


class DubbedAnimeProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://bestdubbedanime.com"
    override val name: String
        get() = "DubbedAnime"
    override val hasQuickSearch: Boolean
        get() = true

    data class QueryEpisodeResultRoot(
        @JsonProperty("result")
        val result: QueryEpisodeResult,
    )

    data class QueryEpisodeResult(
        @JsonProperty("anime") val anime: List<EpisodeInfo>,
        @JsonProperty("error") val error: Boolean,
        @JsonProperty("errorMSG") val errorMSG: String?,
    )

    data class EpisodeInfo(
        @JsonProperty("serversHTML") val serversHTML: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("preview_img") val previewImg: String?,
        @JsonProperty("wideImg") val wideImg: String?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("desc") val desc: String?,

        /*
        @JsonProperty("rowid") val rowid: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("skips") val skips: String,
        @JsonProperty("totalEp") val totalEp: Long,
        @JsonProperty("ep") val ep: String,
        @JsonProperty("NextEp") val nextEp: Long,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("showid") val showid: String,
        @JsonProperty("Epviews") val epviews: String,
        @JsonProperty("TotalViews") val totalViews: String,
        @JsonProperty("tags") val tags: String,*/
    )

    private fun getAnimeEpisode(slug: String, isMovie: Boolean): EpisodeInfo {
        val url =
            mainUrl + (if (isMovie) "/movies/jsonMovie" else "/xz/v3/jsonEpi") + ".php?slug=$slug&_=$unixTime"
        val response = khttp.get(url)
        val mapped = response.let { mapper.readValue<QueryEpisodeResultRoot>(it.text) }

        return mapped.result.anime.first()
    }


    private fun getIsMovie(href: String): Boolean {
        return href.contains("movies/")
    }

    private fun getSlug(href: String): String {
        return href.replace("$mainUrl/", "")
    }

    override fun quickSearch(query: String): ArrayList<Any> {
        val url = "$mainUrl/xz/searchgrid.php?p=1&limit=12&s=$query&_=${unixTime}"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.grid__item > a")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<Any>()
        for (i in items) {
            val href = fixUrl(i.attr("href"))
            val title = i.selectFirst("div.gridtitlek").text()
            val img = fixUrl(i.selectFirst("img.grid__img").attr("src"))
            returnValue.add(
                if (getIsMovie(href)) {
                    MovieSearchResponse(
                        title, href, getSlug(href), this.name, TvType.Movie, img, null
                    )
                } else {
                    AnimeSearchResponse(title,
                        href,
                        getSlug(href),
                        this.name,
                        TvType.Anime,
                        img,
                        null,
                        null,
                        EnumSet.of(DubStatus.Dubbed),
                        null,
                        null)
                })
        }
        return returnValue
    }

    override fun search(query: String): ArrayList<Any> {
        val url = "$mainUrl/search/$query"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.resultinner > a.resulta")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<Any>()
        for (i in items) {
            val innerDiv = i.selectFirst("> div.result")
            val href = fixUrl(i.attr("href"))
            val img = fixUrl(innerDiv.selectFirst("> div.imgkz > img").attr("src"))
            val title = innerDiv.selectFirst("> div.titleresults").text()

            returnValue.add(
                if (getIsMovie(href)) {
                    MovieSearchResponse(
                        title, href, getSlug(href), this.name, TvType.Movie, img, null
                    )
                } else {
                    AnimeSearchResponse(title,
                        href,
                        getSlug(href),
                        this.name,
                        TvType.Anime,
                        img,
                        null,
                        null,
                        EnumSet.of(DubStatus.Dubbed),
                        null,
                        null)
                })
        }

        return returnValue
    }

    override fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        val serversHTML = (if (data.startsWith(mainUrl)) { // CLASSIC EPISODE
            val slug = getSlug(data)
            getAnimeEpisode(slug, false).serversHTML
        } else data).replace("\\", "")

        val hls = ArrayList("hl=\"(.*?)\"".toRegex().findAll(serversHTML).map {
            it.groupValues[1]
        }.toList())
        for (hl in hls) {
            try {
                val sources = khttp.get("$mainUrl/xz/api/playeri.php?url=$hl&_=$unixTime")
                val txt = sources.text
                val find = "src=\"(.*?)\".*?label=\"(.*?)\"".toRegex().find(txt)
                if (find != null) {
                    val quality = find.groupValues[2]
                    callback.invoke(ExtractorLink(this.name,
                        this.name + " " + quality + if (quality.endsWith('p')) "" else 'p',
                        fixUrl(find.groupValues[1]),
                        this.mainUrl,
                        getQualityFromName(quality)))
                }
            } catch (e: Exception) {
                //IDK
            }
        }
        return true
    }

    override fun load(slug: String): Any {
        if (getIsMovie(slug)) {
            val realSlug = slug.replace("movies/", "")
            val episode = getAnimeEpisode(realSlug, true)
            val poster = episode.previewImg ?: episode.wideImg
            return MovieLoadResponse(episode.title,
                realSlug,
                this.name,
                TvType.Movie,
                episode.serversHTML,
                if (poster == null) null else fixUrl(poster),
                episode.year?.toIntOrNull(),
                episode.desc,
                null)
        } else {
            val response = khttp.get("$mainUrl/$slug")
            val document = Jsoup.parse(response.text)
            val title = document.selectFirst("h4").text()
            val descriptHeader = document.selectFirst("div.animeDescript")
            val descript = descriptHeader.selectFirst("> p").text()
            val year = descriptHeader.selectFirst("> div.distatsx > div.sroverd").text().replace("Released: ", "")
                .toIntOrNull()

            val episodes = document.select("a.epibloks").map {
                val epTitle = it.selectFirst("> div.inwel > span.isgrxx")?.text()
                AnimeEpisode(fixUrl(it.attr("href")), epTitle)
            }

            val img = fixUrl(document.select("div.fkimgs > img").attr("src"))
            return AnimeLoadResponse(
                null, null, title, slug, this.name, TvType.Anime, img, year, ArrayList(episodes), null, null, descript,
            )
        }
    }
}
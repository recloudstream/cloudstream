package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element

class AnimeWorldProvider : MainAPI() {
    override var mainUrl = "https://www.animeworld.tv"
    override var name = "AnimeWorld"
    override val lang = "it"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        private const val cookieName = "AWCookieVerify"
        private val cookieRegex = Regex("$cookieName=(.+?)(\\s?);")
        private val cookies = mutableMapOf(cookieName to "")

        private suspend fun request(url: String): NiceResponse {
            val response = app.get(url, cookies = cookies)
            return cookieRegex.find(response.text)?.let {
                val verify = it.groups[1]?.value ?: throw ErrorLoadingException("Can't bypass protection")
                cookies[cookieName] = verify
                return app.get(url, cookies = cookies)
            } ?: response
        }

        fun getType(t: String?): TvType {
            return when (t?.lowercase()) {
                "movie" -> TvType.AnimeMovie
                "ova" -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "finito" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }

    private fun Element.toSearchResult(showEpisode: Boolean = true): AnimeSearchResponse {
        fun String.parseHref(): String {
            val h = this.split('.').toMutableList()
            h[1] = h[1].substringBeforeLast('/')
            return h.joinToString(".")
        }

        val anchor = this.select("a.name").firstOrNull() ?: throw ErrorLoadingException("Error")
        val title = anchor.text().removeSuffix(" (ITA)")
        val otherTitle = anchor.attr("data-jtitle").removeSuffix(" (ITA)")

        val url = fixUrl(anchor.attr("href").parseHref())
        val poster = this.select("a.poster img").attr("src")

        val statusElement = this.select("div.status") // .first()
        val dub = statusElement.select(".dub").isNotEmpty()

        val episode = if (showEpisode) statusElement.select(".ep").text().split(' ').last()
            .toIntOrNull() else null
        val type = when {
            statusElement.select(".movie").isNotEmpty() -> TvType.AnimeMovie
            statusElement.select(".ova").isNotEmpty() -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub, episode)
            this.otherName = otherTitle
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val document = request(mainUrl).document
        val list = ArrayList<HomePageList>()

        val widget = document.select(".widget.hotnew")
        widget.select(".tabs [data-name=\"sub\"], .tabs [data-name=\"dub\"]").forEach { tab ->
            val tabId = tab.attr("data-name")
            val tabName = tab.text().removeSuffix("-ITA")
            val animeList = widget.select("[data-name=\"$tabId\"] .film-list .item").map {
                it.toSearchResult()
            }
            list.add(HomePageList(tabName, animeList))
        }
        widget.select(".tabs [data-name=\"trending\"]").forEach { tab ->
            val tabId = tab.attr("data-name")
            val tabName = tab.text()
            val animeList = widget.select("[data-name=\"$tabId\"] .film-list .item").map {
                it.toSearchResult(showEpisode = false)
            }.distinctBy { it.url }
            list.add(HomePageList(tabName, animeList))
        }
        return HomePageResponse(list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = request("$mainUrl/search?keyword=$query").document
        return document.select(".film-list > .item").map {
            it.toSearchResult(showEpisode = false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        fun String.parseDuration(): Int? {
            val arr = this.split(" e ")
            return if (arr.size == 1)
                arr[0].split(' ')[0].toIntOrNull()
            else
                arr[1].split(' ')[0].toIntOrNull()?.let {
                    arr[0].removeSuffix("h").toIntOrNull()?.times(60)!!.plus(it)
                }
        }

        val document = request(url).document

        val widget = document.select("div.widget.info")
        val title = widget.select(".info .title").text().removeSuffix(" (ITA)")
        val otherTitle = widget.select(".info .title").attr("data-jtitle").removeSuffix(" (ITA)")
        val description =
            widget.select(".desc .long").first()?.text() ?: widget.select(".desc").text()
        val poster = document.select(".thumb img").attr("src")

        val type: TvType = getType(widget.select("dd").first()?.text())
        val genres = widget.select(".meta").select("a[href*=\"/genre/\"]").map { it.text() }
        val rating = widget.select("#average-vote")?.text()

        val trailerUrl = document.select(".trailer[data-url]").attr("data-url")
        val malId = document.select("#mal-button").attr("href")
            .split('/').last().toIntOrNull()
        val anlId = document.select("#anilist-button").attr("href")
            .split('/').last().toIntOrNull()

        var dub = false
        var year: Int? = null
        var status: ShowStatus? = null
        var duration: Int? = null

        for (meta in document.select(".meta dt, .meta dd")) {
            val text = meta.text()
            if (text.contains("Audio"))
                dub = meta.nextElementSibling()?.text() == "Italiano"
            else if (year == null && text.contains("Data"))
                year = meta.nextElementSibling()?.text()?.split(' ')?.last()?.toIntOrNull()
            else if (status == null && text.contains("Stato"))
                status = getStatus(meta.nextElementSibling()?.text())
            else if (status == null && text.contains("Durata"))
                duration = meta.nextElementSibling()?.text()?.parseDuration()
        }

        val servers = document.select(".widget.servers")
        val episodes = servers.select(".server[data-name=\"9\"] .episode").map {
            val id = it.select("a").attr("data-id")
            val number = it.select("a").attr("data-episode-num").toIntOrNull()
            Episode(
                "$mainUrl/api/episode/info?id=$id",
                episode = number
            )
        }
        val comingSoon = episodes.isEmpty()

        val recommendations = document.select(".film-list.interesting .item").map {
            it.toSearchResult(showEpisode = false)
        }

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            japName = otherTitle
            posterUrl = poster
            this.year = year
            addEpisodes(if (dub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
            addMalId(malId)
            addAniListId(anlId)
            addRating(rating)
            this.duration = duration
            addTrailer(trailerUrl)
            this.recommendations = recommendations
            this.comingSoon = comingSoon
        }
    }

    data class Json (
        @JsonProperty("grabber") val grabber: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("target") val target: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = tryParseJson<Json>(
            request(data).text
        )?.grabber

        if (url.isNullOrEmpty())
            return false

        callback.invoke(
            ExtractorLink(
                name,
                name,
                url,
                referer = mainUrl,
                quality = Qualities.Unknown.value
            )
        )
        return true
    }
}

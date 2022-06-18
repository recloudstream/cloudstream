package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element

class DreamSubProvider : MainAPI() {
    override var mainUrl = "https://dreamsub.me"
    override var name = "DreamSub"
    override var lang = "it"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String?): TvType {
            return when (t?.lowercase()) {
                "film" -> TvType.AnimeMovie
                "ova" -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "conclusa" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }

    private fun Element.toSearchResponse(): AnimeSearchResponse {
        val title = this.select(".title").first()?.text()
            ?: throw ErrorLoadingException("No title found")
        val url = this.select(".showStreaming a").first()?.attr("href")
            ?: throw ErrorLoadingException("No url found")

        val type = TvType.Anime
        val posterUrl = this.select(".cover").first()?.attr("style")
            ?.substringAfter("url(")?.substringBefore(")")?.trim()

        val dubInfo = this.select(".showStreaming").first()?.text()
            ?.substringAfter("Lingua:")?.substringBefore("\n")?.trim()
        val dub = dubInfo?.contains("DUB ITA", ignoreCase = true) ?: false
        val sub = dubInfo?.contains("SUB ITA", ignoreCase = true) ?: true

        val desc = this.select(".desc").text()
        val episodes = desc.substringAfter("Episodi:").substringBefore(",")
            .removeSuffix("+").trim().toIntOrNull()
        val year = desc.substringAfter("Anno di inizio:").substringBefore(",")
            .trim().toIntOrNull()

        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub, sub, episodes, episodes)
            this.posterUrl = fixUrlNull(posterUrl)
            this.year = year
        }
    }

    private fun Element.episodeToSearchResponse(): AnimeSearchResponse {
        val title = this.select(".item-title a").first()?.text()
            ?: throw ErrorLoadingException("No title found")
        val url = fixUrlNull(this.select("a").first()?.attr("href"))
            ?: throw ErrorLoadingException("No url found")

        val type = getType(this.select(".gr-eps").first()?.text())
        val posterUrl = this.select("img").first()?.attr("src")
        val episodes = this.select("div[style]").first()?.text()
            ?.replace("Ep", "")?.trim()?.toIntOrNull()

        val dubInfo = this.select(".grt-dub").first()?.text()
        val dub = dubInfo?.contains("DUB ITA", ignoreCase = true) ?: false
        val sub = dubInfo?.contains("SUB ITA", ignoreCase = true) ?: true

        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub, sub, episodes, episodes)
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    private fun Element.toEpisode(season: Int? = null): Episode? {
        val available = this.select(".sli-btn").first() != null
        if (!available)
            return null

        val anchor = this.select(".sli-name a")
        val href = anchor.attr("href")
        val name = anchor.text().substringAfter(":").trim()
        val episode = anchor.text().substringAfter("Episodio").substringBefore(":")
            .trim().toIntOrNull()

        val date = this.select(".sli-name span").text()

        return newEpisode(href) {
            this.name = if (name != "TBA") name else null
            this.season = season
            this.episode = episode
            addDate(date, "dd MMMM yyyy")
        }
    }

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document
        val list = ArrayList<HomePageList>()

        val episodeList = document.select("#episodiRecenti ul.grid-item li").map {
            it.episodeToSearchResponse()
        }
        list.add(HomePageList("Ultimi Episodi", episodeList))

        val updatedList = document.select("#episodiNuovi ul.grid-item li").map {
            it.episodeToSearchResponse()
        }
        list.add(HomePageList("Ultimi Aggiornamenti", updatedList))

        val recommendedList = document.select("#sliderEvidenza .tvBlock").map {
            it.toSearchResponse()
        }
        list.add(HomePageList("In Evidenza", recommendedList))

        val lastedList = document.select("#sliderUltimeInserite .tvBlock").map {
            it.toSearchResponse()
        }
        list.add(HomePageList("Ultime Inserite", lastedList))
        return HomePageResponse(list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/?q=$query").document
        return document.select(".tvBlock").map {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val details = document.select("#animeDetails").first()
            ?: throw ErrorLoadingException("Not found")

        val title = details.select(".dc-title").text()
        val plot = document.select("#tramaLong").first()?.text()
            ?: document.select(".dci-desc").first()?.text()
        val posterUrl = fixUrl(details.select(".dc-thumb img").attr("src"))

        val rating = document.select("#vote_percent").text()
        val trailerUrl = document.select("#media-play iframe").attr("data-url")

        var type: TvType = TvType.Anime
        var duration: String? = null
        var dub = false
        var tags: List<String> = listOf()
        var year: Int? = null
        var status: ShowStatus? = null
        var japName: String? = null

        for (meta in document.select(".dci-spe .dcis")) {
            val key = meta.select("b").text()
            val value = meta.text().removePrefix(key).trim()
            when (key) {
                "Tipo:" -> type = getType(value)
                "Durata:" -> duration = value
                "Lingua:" -> dub = value.contains("DUB ITA", ignoreCase = true)
                "Genere:" -> tags = meta.select("a").map { it.text() }
                "Data:" -> {
                    val values = value.split(",")
                    year = values[0].substringBefore(" a ").trim()
                        .split(" ").getOrNull(2)?.toIntOrNull()
                    status = getStatus(values.getOrNull(1)?.trim())
                }
                // "Titolo giapponese:" -> japName = value
                "Altri titoli:" -> japName = value
            }
        }

        val episodes = mutableListOf<Episode>()
        val seasonElements = document.select("#episodes-sv > li.mainSeas")
        val episodeElements = document.select("#episodes-sv li.ep-item")
        when {
            seasonElements.isNotEmpty() -> {
                seasonElements.forEachIndexed { index, element ->
                    val season = index + 1
                    element.select("li.ep-item").forEach {
                        val episode = it.toEpisode(season)
                        if (episode != null)
                            episodes.add(episode)
                    }
                }
            }
            episodeElements.isNotEmpty() -> {
                episodeElements.forEach {
                    val episode = it.toEpisode()
                    if (episode != null)
                        episodes.add(episode)
                }
            }
            else -> episodes.add(newEpisode(url)) // Movie or Special
        }

        val recommendations = document.select(".related-list ul.grid-item li").map {
            it.episodeToSearchResponse()
        }
        val comingSoon = episodes.isEmpty()

        return newAnimeLoadResponse(title, url, type) {
            addEpisodes(if (dub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            addPoster(posterUrl)
            addRating(rating)
            addDuration(duration)
            addTrailer(trailerUrl)
            this.japName = japName
            this.year = year
            this.showStatus = status
            this.plot = plot
            this.tags = tags
            this.recommendations = recommendations
            this.comingSoon = comingSoon
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val links = document.select("#main-content.onlyDesktop .goblock-content > div")
        if (links.isEmpty())
            return false

        links.forEach { tab ->
            val sub = tab.select("b").text().contains("SUB")
            tab.select("a.dwButton").forEach {
                val title = document.select("#current_episode_name").text()
                    .substringAfter(":").trim()
                val url = it.attr("href")
                val quality = getQualityFromName(it.text())
                callback.invoke(
                    ExtractorLink(
                        name,
                        (if (sub) "SUB ITA" else "DUB") + (if (title != "TBA") " - $title" else ""),
                        url,
                        referer = url,
                        headers = mapOf("host" to "https://cdn.dreamsub.me"),
                        quality = quality
                    )
                )
            }
        }
        return true
    }
}

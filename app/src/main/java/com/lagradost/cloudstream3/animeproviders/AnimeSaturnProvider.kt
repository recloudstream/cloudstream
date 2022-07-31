package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class AnimeSaturnProvider : MainAPI() {
    override var mainUrl = "https://www.animesaturn.cc"
    override var name = "AnimeSaturn"
    override var lang = "it"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "finito" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {

        var title = this.select("a.badge-archivio").first()!!.text()
        var isDubbed = false

        if (title.contains(" (ITA)")){
            title = title.replace(" (ITA)", "")
            isDubbed = true
        }

        val url = this.select("a.badge-archivio").first()!!.attr("href")

        val posterUrl = this.select("img.locandina-archivio[src]").first()!!.attr("src")

        return newAnimeSearchResponse(title, url, TvType.Anime) {
            addDubStatus(isDubbed)
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toEpisode(): Episode? {
        var episode = this.text().split(" ")[1]
        if(episode.contains(".")) return null
        if(episode.contains("-"))
            episode = episode.split("-")[0]

        return Episode(
            data = this.attr("href"),
            episode = episode.toInt()
        )

    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val list = ArrayList<HomePageList>()
        document.select("div.container:has(span.badge-saturn)").forEach {
            val tabName = it.select("span.badge-saturn").first()!!.text()
            if (tabName.equals("Ultimi episodi")) return@forEach
            val results = ArrayList<AnimeSearchResponse>()
            it.select(".main-anime-card").forEach { card ->
                var title = card.select("a[title]").first()!!.attr("title")
                var isDubbed = false
                if(title.contains(" (ITA)")){
                    title = title.replace(" (ITA)", "")
                    isDubbed = true
                }
                val posterUrl = card.select("img.new-anime").first()!!.attr("src")
                val url = card.select("a").first()!!.attr("href")

                results.add(newAnimeSearchResponse(title, url, TvType.Anime){
                    addDubStatus(isDubbed)
                    this.posterUrl = posterUrl
                })
            }
            list.add(HomePageList(tabName, results))
        }
        return HomePageResponse(list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/animelist?search=$query").document
        return document.select("div.item-archivio").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.select("img.cover-anime").first()!!.attr("alt")
        val japTitle = document.select("div.box-trasparente-alternativo").first()!!.text()
        val posterUrl = document.select("img.cover-anime[src]").first()!!.attr("src")
        var malId : Int? = null
        var aniListId : Int? = null

        document.select("[rel=\"noopener noreferrer\"]").forEach {
            if(it.attr("href").contains("myanimelist"))
                malId = it.attr("href").removeSuffix("/").split('/').last().toIntOrNull()
            else
                aniListId = it.attr("href").removeSuffix("/").split('/').last().toIntOrNull()
        }

        val plot = document.select("div#shown-trama").first()?.text()

        val tags = document.select("a.generi-as").map { it.text() }

        val details : List<String>? = document.select("div.container:contains(Stato: )").first()?.text()?.split(" ")
        var status : String? = null
        var duration : String? = null
        var year : String? = null
        var score : String? = null

        val isDubbed = document.select("div.anime-title-as").first()!!.text().contains("(ITA)")

        if (!details.isNullOrEmpty()) {
            details.forEach {
                val index = details.indexOf(it) +1
                when (it) {
                    "Stato:" -> status = details[index]
                    "episodi:" -> duration = details[index]
                    "uscita:" -> year = details[index + 2]
                    "Voto:" -> score = details[index].split("/")[0]
                    else -> return@forEach
                }
            }
        }

        val episodes = document.select("a.bottone-ep").mapNotNull{ it.toEpisode() }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.engName = title
            this.japName = japTitle
            this.year = year?.toIntOrNull()
            this.plot = plot
            this.tags = tags
            this.showStatus = getStatus(status)
            addPoster(posterUrl)
            addRating(score)
            addEpisodes(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            addMalId(malId)
            addAniListId(aniListId)
            addDuration(duration)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val page = app.get(data).document
        val episodeLink = page.select("div.card-body > a[href]").find {it1 ->
            it1.attr("href").contains("watch?")
        }?.attr("href")

        val episodePage = app.get(episodeLink!!).document
        val episodeUrl: String?
        var isM3U8 = false

        if(episodePage.select("video.afterglow > source").isNotEmpty()) //Old player
            episodeUrl = episodePage.select("video.afterglow > source").first()!!.attr("src")

        else{                                                                   //New player
            val script = episodePage.select("script").find {
                it.toString().contains("jwplayer('player_hls').setup({")
            }!!.toString()
            episodeUrl = script.split(" ").find { it.contains(".m3u8") and !it.contains(".replace") }!!.replace("\"","").replace(",", "")
            isM3U8 = true
        }


        callback.invoke(
            ExtractorLink(
                name,
                name,
                episodeUrl!!,
                isM3u8 = isM3U8,
                referer = "https://www.animesaturn.io/", //Some servers need the old host as referer, and the new ones accept it too
                quality = Qualities.Unknown.value
            )
        )
        return true
    }
}

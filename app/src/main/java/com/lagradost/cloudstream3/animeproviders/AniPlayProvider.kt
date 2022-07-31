package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities

class AniPlayProvider : MainAPI() {
    override var mainUrl = "https://aniplay.it"
    override var name = "AniPlay"
    override var lang = "it"
    override val hasMainPage = true
    private val dubIdentifier = " (ITA)"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "completato" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null // "annunciato"
            }
        }
        fun getType(t: String?): TvType {
            return when (t?.lowercase()) {
                "ona" -> TvType.OVA
                "movie" -> TvType.AnimeMovie
                else -> TvType.Anime //"serie", "special"
            }
        }
    }

    private fun isDub(title: String): Boolean{
        return title.contains(dubIdentifier)
    }

    data class ApiPoster(
        @JsonProperty("imageFull") val posterUrl: String
    )

    data class ApiMainPageAnime(
        @JsonProperty("animeId") val id: Int,
        @JsonProperty("episodeNumber") val episode: String?,
        @JsonProperty("animeTitle") val title: String,
        @JsonProperty("animeType") val type: String,
        @JsonProperty("fullHd") val fullHD: Boolean,
        @JsonProperty("animeVerticalImages") val posters: List<ApiPoster>
    )

    data class ApiSearchResult(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("verticalImages") val posters: List<ApiPoster>
    )

    data class ApiGenres(
        @JsonProperty("description") val name: String
    )
    data class ApiWebsite(
        @JsonProperty("listWebsiteId") val websiteId: Int,
        @JsonProperty("url") val url: String
    )

    data class ApiEpisode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("episodeNumber") val number: String,
    )

    private fun ApiEpisode.toEpisode() : Episode? {
        val number = this.number.toIntOrNull() ?: return null
        return Episode(
            data = "$mainUrl/api/episode/${this.id}",
            episode = number,
            name = this.title
        )
    }

    data class ApiSeason(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String
    )

    private suspend fun ApiSeason.toEpisodeList(url: String) : List<Episode> {
        return app.get("$url/season/${this.id}").parsed<List<ApiEpisode>>().mapNotNull { it.toEpisode() }
    }

    data class ApiAnime(
        @JsonProperty("title") val title: String,
        @JsonProperty("alternativeTitle") val japTitle: String?,
        @JsonProperty("episodeDuration") val duration: Int,
        @JsonProperty("storyline") val plot: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("genres") val genres: List<ApiGenres>,
        @JsonProperty("verticalImages") val posters: List<ApiPoster>,
        @JsonProperty("listWebsites") val websites: List<ApiWebsite>,
        @JsonProperty("episodes") val episodes: List<ApiEpisode>,
        @JsonProperty("seasons") val seasons: List<ApiSeason>?
    )

    data class ApiEpisodeUrl(
        @JsonProperty("videoUrl") val url: String
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/api/home/latest-episodes?page=0").parsed<List<ApiMainPageAnime>>()

        val results = response.map{
            val isDub = isDub(it.title)
            newAnimeSearchResponse(
                name = if (isDub) it.title.replace(dubIdentifier, "") else it.title,
                url = "$mainUrl/api/anime/${it.id}",
                type = getType(it.type),
            ){
                addDubStatus(isDub, it.episode?.toIntOrNull())
                this.posterUrl = it.posters.first().posterUrl
                this.quality = if (it.fullHD) SearchQuality.HD else null
            }
        }
        return HomePageResponse(listOf(HomePageList("Ultime uscite",results)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/api/anime/advanced-search?page=0&size=36&query=$query").parsed<List<ApiSearchResult>>()

        return response.map {
            val isDub = isDub(it.title)

            newAnimeSearchResponse(
                name = if (isDub) it.title.replace(dubIdentifier, "") else it.title,
                url = "$mainUrl/api/anime/${it.id}",
                type = getType(it.type),
            ){
                addDubStatus(isDub)
                this.posterUrl = it.posters.first().posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val response = app.get(url).parsed<ApiAnime>()

        val tags: List<String> = response.genres.map { it.name }

        val malId: Int? = response.websites.find { it.websiteId == 1 }?.url?.removePrefix("https://myanimelist.net/anime/")?.split("/")?.first()?.toIntOrNull()
        val aniListId: Int? = response.websites.find { it.websiteId == 4 }?.url?.removePrefix("https://anilist.co/anime/")?.split("/")?.first()?.toIntOrNull()

        val episodes = if (response.seasons.isNullOrEmpty()) response.episodes.mapNotNull { it.toEpisode() } else response.seasons.map{ it.toEpisodeList(url) }.flatten()
        val isDub = isDub(response.title)

        return newAnimeLoadResponse(response.title, url, getType(response.type)) {
            this.name = if (isDub) response.title.replace(dubIdentifier, "") else response.title
            this.japName = response.japTitle
            this.plot = response.plot
            this.tags = tags
            this.showStatus = getStatus(response.status)
            addPoster(response.posters.first().posterUrl)
            addEpisodes(if (isDub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            addMalId(malId)
            addAniListId(aniListId)
            addDuration(response.duration.toString())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val episode = app.get(data).parsed<ApiEpisodeUrl>()

        if(episode.url.contains(".m3u8")){
            val m3u8Helper = M3u8Helper()
            val streams = m3u8Helper.m3u8Generation(M3u8Helper.M3u8Stream(episode.url,Qualities.Unknown.value), false)

            streams.forEach {
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        it.streamUrl,
                        referer = mainUrl,
                        quality = it.quality ?: Qualities.Unknown.value,
                        isM3u8 = it.streamUrl.contains(".m3u8"))) }
            return true
        }

        callback.invoke(
            ExtractorLink(
                name,
                name,
                episode.url,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = false,
            )
        )
        return true
    }
}
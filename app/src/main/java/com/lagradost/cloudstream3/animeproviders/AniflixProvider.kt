package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URLDecoder

class AniflixProvider : MainAPI() {
    override var mainUrl = "https://aniflix.pro"
    override var name = "Aniflix"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    companion object {
        var token: String? = null
    }

    private suspend fun getToken(): String {
        return token ?: run {
            Regex("([^/]*)/_buildManifest\\.js").find(app.get(mainUrl).text)?.groupValues?.getOrNull(
                1
            )
                ?.also {
                    token = it
                }
                ?: throw ErrorLoadingException("No token found")
        }
    }

    private fun Anime.toSearchResponse(): SearchResponse? {
        return newAnimeSearchResponse(
            title?.english ?: title?.romaji ?: return null,
            "$mainUrl/anime/${id ?: return null}"
        ) {
            posterUrl = coverImage?.large ?: coverImage?.medium
        }
    }


    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val elements = listOf(
            Pair("Trending Now", "div:nth-child(3) > div a"),
            Pair("Popular", "div:nth-child(4) > div a"),
            Pair("Top Rated", "div:nth-child(5) > div a"),
        )

        elements.map { (name, element) ->
            val home = soup.select(element).map {
                val href = it.attr("href")
                val title = it.selectFirst("p.mt-2")!!.text()
                val image = it.selectFirst("img.rounded-md[sizes]")!!.attr("src").replace("/_next/image?url=","")
                    .replace(Regex("\\&.*\$"),"")
                val realposter = URLDecoder.decode(image, "UTF-8")
                newAnimeSearchResponse(title, fixUrl(href)) {
                    this.posterUrl = realposter
                }
            }
            items.add(HomePageList(name, home))
        }

        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val token = getToken()
        val url = "$mainUrl/_next/data/$token/search.json?keyword=$query"
        val response = app.get(url)
        val searchResponse =
            response.parsedSafe<Search>()
                ?: throw ErrorLoadingException("No Media")
        return searchResponse.pageProps?.searchResults?.Page?.media?.mapNotNull { media ->
            media.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val token = getToken()

        val id = Regex("$mainUrl/anime/([0-9]*)").find(url)?.groupValues?.getOrNull(1)
            ?: throw ErrorLoadingException("Error parsing link for id")

        val res = app.get("https://aniflix.pro/_next/data/$token/anime/$id.json?id=$id")
            .parsedSafe<AnimeResponsePage>()?.pageProps
            ?: throw ErrorLoadingException("Invalid Json reponse")
        val isMovie = res.anime.format == "MOVIE"
        return newAnimeLoadResponse(
            res.anime.title?.english ?: res.anime.title?.romaji
            ?: throw ErrorLoadingException("Invalid title reponse"),
            url, if (isMovie) TvType.AnimeMovie else TvType.Anime
        ) {
            recommendations = res.recommended.mapNotNull { it.toSearchResponse() }
            tags = res.anime.genres
            posterUrl = res.anime.coverImage?.large ?: res.anime.coverImage?.medium
            plot = res.anime.description
            showStatus = when (res.anime.status) {
                "FINISHED" -> ShowStatus.Completed
                "RELEASING" -> ShowStatus.Ongoing
                else -> null
            }
            addAniListId(id.toIntOrNull())

            // subbed because they are both subbed and dubbed
            if (isMovie)
                addEpisodes(
                    DubStatus.Subbed,
                    listOf(newEpisode("$mainUrl/api/anime/?id=$id&episode=1"))
                )
            else
                addEpisodes(DubStatus.Subbed, res.episodes.episodes?.nodes?.mapIndexed { index, node ->
                    val episodeIndex = node?.number ?: (index + 1)
                    //"$mainUrl/_next/data/$token/watch/$id.json?episode=${node.number ?: return@mapNotNull null}&id=$id"
                    newEpisode("$mainUrl/api/anime?id=$id&episode=${episodeIndex}") {
                        episode = episodeIndex
                        posterUrl = node?.thumbnail?.original?.url
                        name = node?.titles?.canonical
                    }
                })
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return app.get(data).parsed<AniLoadResponse>().let { res ->
            val dubReferer = res.dub?.Referer ?: ""
            res.dub?.sources?.forEach { source ->
                callback(
                    ExtractorLink(
                        name,
                        "${source.label ?: name} (DUB)",
                        source.file ?: return@forEach,
                        dubReferer,
                        getQualityFromName(source.label),
                        source.type == "hls"
                    )
                )
            }

            val subReferer = res.dub?.Referer ?: ""
            res.sub?.sources?.forEach { source ->
                callback(
                    ExtractorLink(
                        name,
                        "${source.label ?: name} (SUB)",
                        source.file ?: return@forEach,
                        subReferer,
                        getQualityFromName(source.label),
                        source.type == "hls"
                    )
                )
            }

            !res.dub?.sources.isNullOrEmpty() && !res.sub?.sources.isNullOrEmpty()
        }
    }

    data class AniLoadResponse(
        @JsonProperty("sub") val sub: DubSubSource?,
        @JsonProperty("dub") val dub: DubSubSource?,
        @JsonProperty("episodes") val episodes: Int?
    )

    data class Sources(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?
    )

    data class DubSubSource(
        @JsonProperty("Referer") var Referer: String?,
        @JsonProperty("sources") var sources: ArrayList<Sources> = arrayListOf()
    )

    data class PageProps(
        @JsonProperty("searchResults") val searchResults: SearchResults?
    )

    data class SearchResults(
        @JsonProperty("Page") val Page: Page?
    )

    data class Page(
        @JsonProperty("media") val media: ArrayList<Anime> = arrayListOf()
    )

    data class CoverImage(
        @JsonProperty("color") val color: String?,
        @JsonProperty("medium") val medium: String?,
        @JsonProperty("large") val large: String?,
    )

    data class Title(
        @JsonProperty("english") val english: String?,
        @JsonProperty("romaji") val romaji: String?,
    )

    data class Search(
        @JsonProperty("pageProps") val pageProps: PageProps?,
        @JsonProperty("__N_SSP") val _NSSP: Boolean?
    )

    data class Anime(
        @JsonProperty("status") val status: String?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: Title?,
        @JsonProperty("coverImage") val coverImage: CoverImage?,
        @JsonProperty("format") val format: String?,
        @JsonProperty("duration") val duration: Int?,
        @JsonProperty("meanScore") val meanScore: Int?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: String?,
        @JsonProperty("bannerImage") val bannerImage: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("genres") val genres: ArrayList<String>? = null,
        @JsonProperty("season") val season: String?,
        @JsonProperty("startDate") val startDate: StartDate?,
    )

    data class StartDate(
        @JsonProperty("year") val year: Int?
    )

    data class AnimeResponsePage(
        @JsonProperty("pageProps") val pageProps: AnimeResponse?,
    )

    data class AnimeResponse(
        @JsonProperty("anime") val anime: Anime,
        @JsonProperty("recommended") val recommended: ArrayList<Anime>,
        @JsonProperty("episodes") val episodes: EpisodesParent,
    )

    data class EpisodesParent(
        @JsonProperty("id") val id: String?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("startDate") val startDate: String?,
        @JsonProperty("episodeCount") val episodeCount: Int?,
        @JsonProperty("episodes") val episodes: Episodes?,
    )

    data class Episodes(
        @JsonProperty("nodes") val nodes: ArrayList<Nodes?> = arrayListOf()
    )

    data class Nodes(
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("titles") val titles: Titles?,
        @JsonProperty("thumbnail") val thumbnail: Thumbnail?,
    )

    data class Titles(
        @JsonProperty("canonical") val canonical: String?,
    )

    data class Original(
        @JsonProperty("url") val url: String?,
    )

    data class Thumbnail(
        @JsonProperty("original") val original: Original?,
    )
}
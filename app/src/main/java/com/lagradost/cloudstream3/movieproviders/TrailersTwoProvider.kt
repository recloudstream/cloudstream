package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper

class TrailersTwoProvider : TmdbProvider() {
    val user = "cloudstream"
    override val apiName = "Trailers.to"
    override var name = "Trailers.to"
    override var mainUrl = "https://trailers.to"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = true

    data class TrailersEpisode(
        // val tvShowItemID: Long?,
        //val tvShow: String,
        //val tvShowIMDB: String?,
        //val tvShowTMDB: Long?,
        @JsonProperty("ItemID")
        val itemID: Int,
        //val title: String,
        //@JsonProperty("IMDb")
        @JsonProperty("IMDb")
        val imdb: String?,
        //@JsonProperty("TMDb")
        @JsonProperty("TMDb")
        val tmdb: Int?,
        //val releaseDate: String,
        //val entryDate: String
    )

    data class TrailersMovie(
        @JsonProperty("ItemID")
        val itemID: Int,
        @JsonProperty("IMDb")
        val imdb: String?,
        @JsonProperty("TMDb")
        val tmdb: Int?,
        //@JsonProperty("Title")
        //val title: String?,
    )

    /*companion object {
        private var tmdbToIdMovies: HashMap<Int, Int> = hashMapOf()
        private var imdbToIdMovies: HashMap<String, Int> = hashMapOf()
        private var tmdbToIdTvSeries: HashMap<Int, Int> = hashMapOf()
        private var imdbToIdTvSeries: HashMap<String, Int> = hashMapOf()

        private const val startDate = 1900
        private const val endDate = 9999

        fun getEpisode(tmdb: Int?, imdb: String?): Int? {
            var currentId: Int? = null
            if (tmdb != null) {
                currentId = tmdbToIdTvSeries[tmdb]
            }
            if (imdb != null && currentId == null) {
                currentId = imdbToIdTvSeries[imdb]
            }
            return currentId
        }

        fun getMovie(tmdb: Int?, imdb: String?): Int? {
            var currentId: Int? = null
            if (tmdb != null) {
                currentId = tmdbToIdMovies[tmdb]
            }
            if (imdb != null && currentId == null) {
                currentId = imdbToIdMovies[imdb]
            }
            return currentId
        }

        suspend fun fillData(isMovie: Boolean) {
            if (isMovie) {
                if (tmdbToIdMovies.isNotEmpty() || imdbToIdMovies.isNotEmpty()) {
                    return
                }
                parseJson<List<TrailersMovie>>(
                    app.get(
                        "https://trailers.to/movies?from=$startDate-01-01&to=$endDate",
                        timeout = 30
                    ).text
                ).forEach { movie ->
                    movie.imdb?.let {
                        imdbToIdTvSeries[it] = movie.itemID
                    }
                    movie.tmdb?.let {
                        tmdbToIdTvSeries[it] = movie.itemID
                    }
                }
            } else {
                if (tmdbToIdTvSeries.isNotEmpty() || imdbToIdTvSeries.isNotEmpty()) {
                    return
                }
                parseJson<List<TrailersEpisode>>(
                    app.get(
                        "https://trailers.to/episodes?from=$startDate-01-01&to=$endDate",
                        timeout = 30
                    ).text
                ).forEach { episode ->
                    episode.imdb?.let {
                        imdbToIdTvSeries[it] = episode.itemID
                    }
                    episode.tmdb?.let {
                        tmdbToIdTvSeries[it] = episode.itemID
                    }
                }
            }
        }
    }*/

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        //TvType.AnimeMovie,
        //TvType.Anime,
        //TvType.Cartoon
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = parseJson<TmdbLink>(data)
        val (id, site) = if (mappedData.imdbID != null) listOf(
            mappedData.imdbID,
            "imdb"
        ) else listOf(mappedData.tmdbID.toString(), "tmdb")

        val isMovie = mappedData.episode == null && mappedData.season == null
        val (videoUrl, subtitleUrl) = if (isMovie) {
            val suffix = "$user/$site/$id"
            Pair(
                "https://trailers.to/video/$suffix",
                "https://trailers.to/subtitles/$suffix"
            )
        } else {
            val suffix = "$user/$site/$id/S${mappedData.season ?: 1}E${mappedData.episode ?: 1}"
            Pair(
                "https://trailers.to/video/$suffix",
                "https://trailers.to/subtitles/$suffix"
            )
        }

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                videoUrl,
                "https://trailers.to",
                Qualities.Unknown.value,
                false,
            )
        )

        argamap(
            {
                val subtitles =
                    app.get(subtitleUrl).text
                val subtitlesMapped = parseJson<List<TrailersSubtitleFile>>(subtitles)
                subtitlesMapped.forEach {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            SubtitleHelper.fromTwoLettersToLanguage(it.LanguageCode ?: "en")
                                ?: "English",
                            "https://trailers.to/subtitles/${it.ContentHash ?: return@forEach}/${it.LanguageCode ?: return@forEach}.vtt" // ${it.MetaInfo?.SubFormat ?: "srt"}"
                        )
                    )
                }
            }, {
                //https://trailers.to/en/quick-search?q=iron man
                val name = mappedData.movieName
                if (name != null && isMovie) {
                    app.get("https://trailers.to/en/quick-search?q=${name}").document.select("a.post-minimal")
                        .mapNotNull {
                            it?.attr("href")
                        }.map { Regex("""/movie/(\d+)/""").find(it)?.groupValues?.getOrNull(1) }
                        .firstOrNull()?.let { movieId ->
                            val correctUrl = app.get(videoUrl).url
                            callback.invoke(
                                ExtractorLink(
                                    this.name,
                                    "${this.name} Backup",
                                    correctUrl.replace("/$user/0/", "/$user/$movieId/"),
                                    "https://trailers.to",
                                    Qualities.Unknown.value,
                                    false,
                                )
                            )
                        }
                }
            }
        )

        /*
        // the problem with this code is that it tages ages and the json file is 50mb or so for movies
        fillData(isMovie)
        val movieId = if (isMovie) {
            getMovie(mappedData.tmdbID, mappedData.imdbID)
        } else {
            getEpisode(mappedData.tmdbID, mappedData.imdbID)
        } ?: return@argamap
        val request = app.get(data)
        val endUrl = request.url
        callback.invoke(
            ExtractorLink(
                this.name,
                "${this.name} Backup",
                endUrl.replace("/cloudstream/0/", "/cloudstream/$movieId/"),
                "https://trailers.to",
                Qualities.Unknown.value,
                false,
            )
        )
         */

        return true
    }
}

// Auto generated
data class TrailersSubtitleFile(
    @JsonProperty("SubtitleID") val SubtitleID: Int?,
    @JsonProperty("ItemID") val ItemID: Int?,
    @JsonProperty("ContentText") val ContentText: String?,
    @JsonProperty("ContentHash") val ContentHash: String?,
    @JsonProperty("LanguageCode") val LanguageCode: String?,
    @JsonProperty("MetaInfo") val MetaInfo: MetaInfo?,
    @JsonProperty("EntryDate") val EntryDate: String?,
    @JsonProperty("ItemSubtitleAdaptations") val ItemSubtitleAdaptations: List<ItemSubtitleAdaptations>?,
    @JsonProperty("ReleaseNames") val ReleaseNames: List<String>?,
    @JsonProperty("SubFileNames") val SubFileNames: List<String>?,
    @JsonProperty("Framerates") val Framerates: List<Int>?,
    @JsonProperty("IsRelevant") val IsRelevant: Boolean?
)

data class QueryParameters(
    @JsonProperty("imdbid") val imdbid: String?
)

data class MetaInfo(
    @JsonProperty("MatchedBy") val MatchedBy: String?,
    @JsonProperty("IDSubMovieFile") val IDSubMovieFile: String?,
    @JsonProperty("MovieHash") val MovieHash: String?,
    @JsonProperty("MovieByteSize") val MovieByteSize: String?,
    @JsonProperty("MovieTimeMS") val MovieTimeMS: String?,
    @JsonProperty("IDSubtitleFile") val IDSubtitleFile: String?,
    @JsonProperty("SubFileName") val SubFileName: String?,
    @JsonProperty("SubActualCD") val SubActualCD: String?,
    @JsonProperty("SubSize") val SubSize: String?,
    @JsonProperty("SubHash") val SubHash: String?,
    @JsonProperty("SubLastTS") val SubLastTS: String?,
    @JsonProperty("SubTSGroup") val SubTSGroup: String?,
    @JsonProperty("InfoReleaseGroup") val InfoReleaseGroup: String?,
    @JsonProperty("InfoFormat") val InfoFormat: String?,
    @JsonProperty("InfoOther") val InfoOther: String?,
    @JsonProperty("IDSubtitle") val IDSubtitle: String?,
    @JsonProperty("UserID") val UserID: String?,
    @JsonProperty("SubLanguageID") val SubLanguageID: String?,
    @JsonProperty("SubFormat") val SubFormat: String?,
    @JsonProperty("SubSumCD") val SubSumCD: String?,
    @JsonProperty("SubAuthorComment") val SubAuthorComment: String?,
    @JsonProperty("SubAddDate") val SubAddDate: String?,
    @JsonProperty("SubBad") val SubBad: String?,
    @JsonProperty("SubRating") val SubRating: String?,
    @JsonProperty("SubSumVotes") val SubSumVotes: String?,
    @JsonProperty("SubDownloadsCnt") val SubDownloadsCnt: String?,
    @JsonProperty("MovieReleaseName") val MovieReleaseName: String?,
    @JsonProperty("MovieFPS") val MovieFPS: String?,
    @JsonProperty("IDMovie") val IDMovie: String?,
    @JsonProperty("IDMovieImdb") val IDMovieImdb: String?,
    @JsonProperty("MovieName") val MovieName: String?,
    @JsonProperty("MovieNameEng") val MovieNameEng: String?,
    @JsonProperty("MovieYear") val MovieYear: String?,
    @JsonProperty("MovieImdbRating") val MovieImdbRating: String?,
    @JsonProperty("SubFeatured") val SubFeatured: String?,
    @JsonProperty("UserNickName") val UserNickName: String?,
    @JsonProperty("SubTranslator") val SubTranslator: String?,
    @JsonProperty("ISO639") val ISO639: String?,
    @JsonProperty("LanguageName") val LanguageName: String?,
    @JsonProperty("SubComments") val SubComments: String?,
    @JsonProperty("SubHearingImpaired") val SubHearingImpaired: String?,
    @JsonProperty("UserRank") val UserRank: String?,
    @JsonProperty("SeriesSeason") val SeriesSeason: String?,
    @JsonProperty("SeriesEpisode") val SeriesEpisode: String?,
    @JsonProperty("MovieKind") val MovieKind: String?,
    @JsonProperty("SubHD") val SubHD: String?,
    @JsonProperty("SeriesIMDBParent") val SeriesIMDBParent: String?,
    @JsonProperty("SubEncoding") val SubEncoding: String?,
    @JsonProperty("SubAutoTranslation") val SubAutoTranslation: String?,
    @JsonProperty("SubForeignPartsOnly") val SubForeignPartsOnly: String?,
    @JsonProperty("SubFromTrusted") val SubFromTrusted: String?,
    @JsonProperty("QueryCached") val QueryCached: Int?,
    @JsonProperty("SubTSGroupHash") val SubTSGroupHash: String?,
    @JsonProperty("SubDownloadLink") val SubDownloadLink: String?,
    @JsonProperty("ZipDownloadLink") val ZipDownloadLink: String?,
    @JsonProperty("SubtitlesLink") val SubtitlesLink: String?,
    @JsonProperty("QueryNumber") val QueryNumber: String?,
    @JsonProperty("QueryParameters") val QueryParameters: QueryParameters?,
    @JsonProperty("Score") val Score: Double?
)

data class ItemSubtitleAdaptations(
    @JsonProperty("ContentHash") val ContentHash: String?,
    @JsonProperty("OffsetMs") val OffsetMs: Int?,
    @JsonProperty("Framerate") val Framerate: Int?,
    @JsonProperty("Views") val Views: Int?,
    @JsonProperty("EntryDate") val EntryDate: String?,
    @JsonProperty("Subtitle") val Subtitle: String?
)
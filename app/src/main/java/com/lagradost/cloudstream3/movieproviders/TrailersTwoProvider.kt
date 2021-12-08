package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper

class TrailersTwoProvider : TmdbProvider() {
    val user = "cloudstream"
    override val apiName = "Trailers.to"
    override val name = "Trailers.to"
    override val mainUrl = "https://trailers.to"
    override val useMetaLoadResponse = true
    override val instantLinkLoading = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.Cartoon
    )

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = mapper.readValue<TmdbLink>(data)
        val (id, site) = if (mappedData.imdbID != null) listOf(
            mappedData.imdbID,
            "imdb"
        ) else listOf(mappedData.tmdbID.toString(), "tmdb")

        val isMovie = mappedData.episode == null && mappedData.season == null
        val subtitleUrl = if (isMovie) {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    "https://trailers.to/video/$user/$site/$id",
                    "https://trailers.to",
                    Qualities.Unknown.value,
                    false,
                )
            )
            "https://trailers.to/subtitles/$user/$site/$id"
        } else {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    "https://trailers.to/video/$user/$site/$id/S${mappedData.season ?: 1}E${mappedData.episode ?: 1}",
                    "https://trailers.to",
                    Qualities.Unknown.value,
                    false,
                )
            )
            "https://trailers.to/subtitles/$user/$site/$id/S${mappedData.season ?: 1}E${mappedData.episode ?: 1}"
        }

        val subtitles =
            app.get(subtitleUrl).text
        val subtitlesMapped = mapper.readValue<List<TrailersSubtitleFile>>(subtitles)
        subtitlesMapped.forEach {
            subtitleCallback.invoke(
                SubtitleFile(
                    SubtitleHelper.fromTwoLettersToLanguage(it.LanguageCode ?: "en") ?: "English",
                    "https://trailers.to/subtitles/${it.ContentHash ?: return@forEach}/${it.LanguageCode ?: return@forEach}.vtt" // ${it.MetaInfo?.SubFormat ?: "srt"}"
                )
            )
        }
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
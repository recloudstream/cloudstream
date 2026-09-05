package com.lagradost.cloudstream3.ui.result

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** TMDB supplies metadata only. Selected titles are searched through installed providers. */
internal class ActorFilmographyRepository(
    private val request: suspend (String, Map<String, String>) -> String = { path, params ->
        val response = app.get(
            url = "$TMDB_API_URL$path",
            params = params + ("api_key" to TMDB_API_KEY),
        )
        check(response.isSuccessful) { "TMDB request failed (${response.code})" }
        response.text
    },
) {
    private companion object {
        // Same application key as the existing TmdbProvider; no new credentials are needed.
        const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
        const val TMDB_API_URL = "https://api.themoviedb.org/3"
        const val TMDB_IMAGE_URL = "https://image.tmdb.org/t/p/w500"

        val cards = object : MainAPI() {
            override var name = "TMDB"
        }
    }

    @Serializable
    private data class TmdbPersonSearchResponse(
        @JsonProperty("results")
        @SerialName("results")
        val results: List<TmdbPerson>? = null,
    )

    @Serializable
    private data class TmdbPerson(
        @JsonProperty("id")
        @SerialName("id")
        val id: Int? = null,
        @JsonProperty("name")
        @SerialName("name")
        val name: String? = null,
        @JsonProperty("profile_path")
        @SerialName("profile_path")
        val profilePath: String? = null,
    )

    @Serializable
    private data class TmdbCombinedCredits(
        @JsonProperty("cast")
        @SerialName("cast")
        val cast: List<TmdbCredit>? = null,
    )

    @Serializable
    private data class TmdbCredit(
        @JsonProperty("id")
        @SerialName("id")
        val id: Int? = null,
        @JsonProperty("title")
        @SerialName("title")
        val title: String? = null,
        @JsonProperty("original_title")
        @SerialName("original_title")
        val originalTitle: String? = null,
        @JsonProperty("name")
        @SerialName("name")
        val name: String? = null,
        @JsonProperty("original_name")
        @SerialName("original_name")
        val originalName: String? = null,
        @JsonProperty("poster_path")
        @SerialName("poster_path")
        val posterPath: String? = null,
        @JsonProperty("vote_average")
        @SerialName("vote_average")
        val voteAverage: Double? = null,
        @JsonProperty("release_date")
        @SerialName("release_date")
        val releaseDate: String? = null,
        @JsonProperty("first_air_date")
        @SerialName("first_air_date")
        val firstAirDate: String? = null,
        @JsonProperty("media_type")
        @SerialName("media_type")
        val mediaType: String? = null,
        @JsonProperty("popularity")
        @SerialName("popularity")
        val popularity: Double? = null,
        @JsonProperty("adult")
        @SerialName("adult")
        val adult: Boolean? = null,
    ) {
        val displayTitle: String
            get() = listOf(title, name, originalTitle, originalName)
                .firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        val year: Int?
            get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()

        val isTv: Boolean
            get() = mediaType == "tv"
    }

    suspend fun load(actor: Actor): List<SearchResponse> {
        val actorName = actor.name.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
        val people = parseJson<TmdbPersonSearchResponse>(
            request(
                "/search/person",
                mapOf("query" to actorName, "language" to "en-US", "include_adult" to "false"),
            )
        ).results.orEmpty().filter { (it.id ?: 0) > 0 }

        // Image paths survive TMDB's image-size variations and help disambiguate names.
        val imageFile = actor.image.imageFileName()
        val person = people.firstOrNull {
            imageFile != null && it.profilePath.imageFileName() == imageFile
        } ?: people.firstOrNull {
            it.name.equals(actorName, ignoreCase = true)
        } ?: people.firstOrNull() ?: return emptyList()

        val credits = parseJson<TmdbCombinedCredits>(
            request("/person/${person.id}/combined_credits", mapOf("language" to "en-US"))
        ).cast.orEmpty()

        return credits.asSequence()
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .filter { it.adult != true && (it.id ?: 0) > 0 && it.displayTitle.isNotBlank() }
            .distinctBy { it.mediaType to it.id }
            .sortedWith(
                compareByDescending<TmdbCredit> { it.popularity ?: 0.0 }
                    .thenByDescending { it.year ?: 0 }
            )
            .map { it.toSearchResponse() }
            .toList()
    }

    private fun TmdbCredit.toSearchResponse(): SearchResponse = with(cards) {
        // SearchAdapter compares IDs without the media type. Keep TV and movie IDs distinct.
        val cardId = id?.let { if (isTv) -it else it }
        if (isTv) {
            newTvSeriesSearchResponse(
                name = displayTitle,
                url = "https://www.themoviedb.org/tv/$id",
                type = TvType.TvSeries,
                fix = false,
            ) {
                this.id = cardId
                posterUrl = posterPath?.takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGE_URL$it" }
                score = Score.from10(voteAverage)
                year = this@toSearchResponse.year
            }
        } else {
            newMovieSearchResponse(
                name = displayTitle,
                url = "https://www.themoviedb.org/movie/$id",
                type = TvType.Movie,
                fix = false,
            ) {
                this.id = cardId
                posterUrl = posterPath?.takeIf { it.isNotBlank() }?.let { "$TMDB_IMAGE_URL$it" }
                score = Score.from10(voteAverage)
                year = this@toSearchResponse.year
            }
        }
    }

    private fun String?.imageFileName(): String? = this
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
}

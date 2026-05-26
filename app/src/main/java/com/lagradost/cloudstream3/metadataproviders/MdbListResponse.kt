package com.lagradost.cloudstream3.metadataproviders

import com.fasterxml.jackson.annotation.JsonProperty

data class MdbListResponse(
    @JsonProperty("response") val response: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("year") val year: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("score") val score: Int? = null, // average MDBList score
    @JsonProperty("score_average") val scoreAverage: Double? = null,
    @JsonProperty("ratings") val ratings: List<MdbListRating>? = null
)

data class MdbListRating(
    @JsonProperty("source") val source: String? = null, // "imdb", "tomatoes", "tomatoesaudience", "metacritic"
    @JsonProperty("value") val value: Double? = null,
    @JsonProperty("score") val score: Int? = null,
    @JsonProperty("votes") val votes: Long? = null
)

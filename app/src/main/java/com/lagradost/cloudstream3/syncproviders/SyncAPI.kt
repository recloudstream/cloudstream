package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.*

interface SyncAPI : OAuth2API {
    val mainUrl: String

    /**
    -1 -> None
    0 -> Watching
    1 -> Completed
    2 -> OnHold
    3 -> Dropped
    4 -> PlanToWatch
    5 -> ReWatching
     */
    suspend fun score(id: String, status: SyncStatus): Boolean

    suspend fun getStatus(id: String): SyncStatus?

    suspend fun getResult(id: String): SyncResult?

    suspend fun search(name: String): List<SyncSearchResult>?

    fun getIdFromUrl(url : String) : String

    data class SyncSearchResult(
        override val name: String,
        override val apiName: String,
        var syncId: String,
        override val url: String,
        override var posterUrl: String?,
        override var type: TvType? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override var id: Int? = null,
    ) : SearchResponse

    data class SyncStatus(
        val status: Int,
        /** 1-10 */
        val score: Int?,
        val watchedEpisodes: Int?,
        var isFavorite: Boolean? = null,
        var maxEpisodes : Int? = null,
    )

    data class SyncResult(
        /**Used to verify*/
        var id: String,

        var totalEpisodes: Int? = null,

        var title: String? = null,
        /**1-1000*/
        var publicScore: Int? = null,
        /**In minutes*/
        var duration: Int? = null,
        var synopsis: String? = null,
        var airStatus: ShowStatus? = null,
        var nextAiring: NextAiring? = null,
        var studio: List<String>? = null,
        var genres: List<String>? = null,
        var synonyms: List<String>? = null,
        var trailers: List<String>? = null,
        var isAdult : Boolean? = null,
        var posterUrl: String? = null,
        var backgroundPosterUrl : String? = null,

        /** In unixtime */
        var startDate: Long? = null,
        /** In unixtime */
        var endDate: Long? = null,
        var recommendations: List<SyncSearchResult>? = null,
        var nextSeason: SyncSearchResult? = null,
        var prevSeason: SyncSearchResult? = null,
        var actors: List<ActorData>? = null,
    )
}
package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.ShowStatus

interface SyncAPI : OAuth2API {
    val icon: Int
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

    data class SyncSearchResult(
        val name: String,
        val syncApiName: String,
        val id: String,
        val url: String,
        val posterUrl: String?,
    )

    data class SyncNextAiring(
        val episode: Int,
        val unixTime: Long,
    )

    data class SyncActor(
        val name: String,
        val posterUrl: String?,
    )

    data class SyncCharacter(
        val name: String,
        val posterUrl: String?,
    )

    data class SyncStatus(
        val status: Int,
        /** 1-10 */
        val score: Int?,
        val watchedEpisodes: Int?,
        var isFavorite: Boolean? = null,
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
        var nextAiring: SyncNextAiring? = null,
        var studio: List<String>? = null,
        var genres: List<String>? = null,
        var trailerUrl: String? = null,

        /** In unixtime */
        var startDate: Long? = null,
        /** In unixtime */
        var endDate: Long? = null,
        var recommendations: List<SyncSearchResult>? = null,
        var nextSeason: SyncSearchResult? = null,
        var prevSeason: SyncSearchResult? = null,
        var actors: List<SyncActor>? = null,
        var characters: List<SyncCharacter>? = null,
    )
}
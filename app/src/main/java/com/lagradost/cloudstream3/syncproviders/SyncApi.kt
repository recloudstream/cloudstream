package com.lagradost.cloudstream3.syncproviders

import com.frosch2010.fuzzywuzzy_kotlin.FuzzySearch
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.UiText
import java.util.Date

interface SyncAPI : OAuth2API {
    /**
     * Set this to true if the user updates something on the list like watch status or score
     **/
    var requireLibraryRefresh: Boolean
    val mainUrl: String

    /**
     * Allows certain providers to open pages from
     * library links.
     **/
    val syncIdName: SyncIdName

    /**
    -1 -> None
    0 -> Watching
    1 -> Completed
    2 -> OnHold
    3 -> Dropped
    4 -> PlanToWatch
    5 -> ReWatching
     */
    suspend fun score(id: String, status: AbstractSyncStatus): Boolean

    suspend fun getStatus(id: String): AbstractSyncStatus?

    suspend fun getResult(id: String): SyncResult?

    suspend fun search(name: String): List<SyncSearchResult>?

    suspend fun getPersonalLibrary(): LibraryMetadata?

    fun getIdFromUrl(url: String): String

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

    abstract class AbstractSyncStatus {
        abstract var status: SyncWatchType

        /** 1-10 */
        abstract var score: Int?
        abstract var watchedEpisodes: Int?
        abstract var isFavorite: Boolean?
        abstract var maxEpisodes: Int?
    }


    data class SyncStatus(
        override var status: SyncWatchType,
        /** 1-10 */
        override var score: Int?,
        override var watchedEpisodes: Int?,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
    ) : AbstractSyncStatus()

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
        var isAdult: Boolean? = null,
        var posterUrl: String? = null,
        var backgroundPosterUrl: String? = null,

        /** In unixtime */
        var startDate: Long? = null,
        /** In unixtime */
        var endDate: Long? = null,
        var recommendations: List<SyncSearchResult>? = null,
        var nextSeason: SyncSearchResult? = null,
        var prevSeason: SyncSearchResult? = null,
        var actors: List<ActorData>? = null,
    )


    data class Page(
        val title: UiText, var items: List<LibraryItem>
    ) {
        fun sort(method: ListSorting?, query: String? = null) {
            items = when (method) {
                ListSorting.Query ->
                    if (query != null) {
                        items.sortedBy {
                            -FuzzySearch.partialRatio(
                                query.lowercase(), it.name.lowercase()
                            )
                        }
                    } else items
                ListSorting.RatingHigh -> items.sortedBy { -(it.personalRating ?: 0) }
                ListSorting.RatingLow -> items.sortedBy { (it.personalRating ?: 0) }
                ListSorting.AlphabeticalA -> items.sortedBy { it.name }
                ListSorting.AlphabeticalZ -> items.sortedBy { it.name }.reversed()
                ListSorting.UpdatedNew -> items.sortedBy { it.lastUpdatedUnixTime?.times(-1) }
                ListSorting.UpdatedOld -> items.sortedBy { it.lastUpdatedUnixTime }
                ListSorting.ReleaseDateNew -> items.sortedByDescending { it.releaseDate }
                ListSorting.ReleaseDateOld -> items.sortedBy { it.releaseDate }
                else -> items
            }
        }
    }

    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList>,
        val supportedListSorting: Set<ListSorting>
    )

    data class LibraryList(
        val name: UiText,
        val items: List<LibraryItem>
    )

    data class LibraryItem(
        override val name: String,
        override val url: String,
        /**
         * Unique unchanging string used for data storage.
         * This should be the actual id when you change scores and status
         * since score changes from library might get added in the future.
         **/
        val syncId: String,
        val episodesCompleted: Int?,
        val episodesTotal: Int?,
        /** Out of 100 */
        val personalRating: Int?,
        val lastUpdatedUnixTime: Long?,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var posterHeaders: Map<String, String>?,
        override var quality: SearchQuality?,
        val releaseDate: Date?,
        override var id: Int? = null,
        val plot : String? = null,
        val rating: Int? = null,
        val tags: List<String>? = null
    ) : SearchResponse
}
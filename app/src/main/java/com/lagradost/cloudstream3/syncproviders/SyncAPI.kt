package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.UiText
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.Date

/**
 * Stateless synchronization class, used for syncing status about a specific movie/show.
 *
 * All non-null `AuthToken` will be non-expired when each function is called.
 */
abstract class SyncAPI : AuthAPI() {
    /**
     * Set this to true if the user updates something on the list like watch status or score
     **/
    open var requireLibraryRefresh: Boolean = true
    open val mainUrl: String = "NONE"

    /** Currently unused, but will be used to correctly render the UI.
     * This should specify what sync watch types can be used with this service. */
    open val supportedWatchTypes: Set<SyncWatchType> = SyncWatchType.entries.toSet()
    /**
     * Allows certain providers to open pages from
     * library links.
     **/
    open val syncIdName: SyncIdName? = null

    /** Modify the current status of an item */
    @Throws
    @WorkerThread
    open suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean = throw NotImplementedError()

    /** Get the current status of an item */
    @Throws
    @WorkerThread
    open suspend fun status(auth: AuthData?, id: String): AbstractSyncStatus? =
        throw NotImplementedError()

    /** Get metadata about an item */
    @Throws
    @WorkerThread
    open suspend fun load(auth: AuthData?, id: String): SyncResult? = throw NotImplementedError()

    /** Search this service for any results for a given query */
    @Throws
    @WorkerThread
    open suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? =
        throw NotImplementedError()

    /** Get the current library/bookmarks of this service */
    @Throws
    @WorkerThread
    open suspend fun library(auth: AuthData?): LibraryMetadata? = throw NotImplementedError()

    /** Helper function, may be used in the future */
    @Throws
    open fun urlToId(url: String): String? = null

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
        override var score: Score? = null,
    ) : SearchResponse

    abstract class AbstractSyncStatus {
        abstract var status: SyncWatchType
        abstract var score: Score?
        abstract var watchedEpisodes: Int?
        abstract var isFavorite: Boolean?
        abstract var maxEpisodes: Int?
    }

    data class SyncStatus(
        override var status: SyncWatchType,
        override var score: Score?,
        override var watchedEpisodes: Int?,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
    ) : AbstractSyncStatus()

    data class SyncResult(
        /**Used to verify*/
        var id: String,

        var totalEpisodes: Int? = null,

        var title: String? = null,
        var publicScore: Score? = null,
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

                ListSorting.RatingHigh -> items.sortedBy { -(it.personalRating?.toInt(100) ?: 0) }
                ListSorting.RatingLow -> items.sortedBy { (it.personalRating?.toInt(100) ?: 0) }
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
        val personalRating: Score?,
        val lastUpdatedUnixTime: Long?,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var posterHeaders: Map<String, String>?,
        override var quality: SearchQuality?,
        val releaseDate: Date?,
        override var id: Int? = null,
        val plot: String? = null,
        override var score: Score? = null,
        val tags: List<String>? = null
    ) : SearchResponse
}
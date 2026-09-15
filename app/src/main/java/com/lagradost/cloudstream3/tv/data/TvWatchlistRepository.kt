package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.tv.model.TvWatchlistCatalog
import com.lagradost.cloudstream3.tv.model.TvWatchlistItem
import com.lagradost.cloudstream3.tv.model.TvWatchlistSection
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllFavorites
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAllWatchStateIds
import com.lagradost.cloudstream3.utils.DataStoreHelper.getBookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.getCurrentAccount
import com.lagradost.cloudstream3.utils.DataStoreHelper.getResultWatchState

/**
 * Pure read-only Watchlist / Library adapter (Local list only).
 *
 * Exact source APIs (same data [com.lagradost.cloudstream3.syncproviders.providers.LocalList] reads):
 * - [DataStoreHelper.getAllWatchStateIds]
 * - [DataStoreHelper.getResultWatchState]
 * - [DataStoreHelper.getBookmarkedData]
 * - [DataStoreHelper.getAllFavorites]
 * - [DataStoreHelper.getCurrentAccount] / [DataStoreHelper.currentAccount] (identity only)
 *
 * Intentionally does **not** call SyncRepo / MAL / AniList / Simkl / Kitsu (auth + network),
 * does **not** write LAST_SYNC_API_KEY or librarySortingMode, and does **not** mutate
 * requireLibraryRefresh. Subscriptions omitted (LocalList already hides them on TV).
 */
class TvWatchlistRepository {

    sealed interface LoadResult {
        data class Success(val catalog: TvWatchlistCatalog) : LoadResult
        data class Empty(val catalog: TvWatchlistCatalog) : LoadResult
        data class Failure(val message: String) : LoadResult
    }

    fun loadWatchlist(): LoadResult {
        return try {
            val account = getCurrentAccount()
            val accountKey = DataStoreHelper.currentAccount
            val accountName = account?.name

            val sections = buildSections()
            val catalog = TvWatchlistCatalog(
                sections = sections,
                accountKey = accountKey,
                accountName = accountName,
            )
            if (sections.isEmpty() || sections.all { it.items.isEmpty() }) {
                LoadResult.Empty(catalog)
            } else {
                LoadResult.Success(catalog)
            }
        } catch (t: Throwable) {
            LoadResult.Failure(t.message ?: "Failed to read Library / Watchlist")
        }
    }

    private fun buildSections(): List<TvWatchlistSection> {
        val watchStatusIds = getAllWatchStateIds()?.map { id ->
            id to getResultWatchState(id)
        }?.distinctBy { it.first }.orEmpty()

        val byType = linkedMapOf<WatchType, MutableList<TvWatchlistItem>>()
        WatchType.entries.filter { it != WatchType.NONE }.forEach { byType[it] = mutableListOf() }

        for ((id, type) in watchStatusIds) {
            if (type == WatchType.NONE) continue
            val data = getBookmarkedData(id) ?: continue
            val item = mapBookmark(data, statusLabel = watchTypeLabel(type)) ?: continue
            byType.getOrPut(type) { mutableListOf() }.add(item)
        }

        byType.values.forEach { list ->
            list.sortByDescending { it.latestUpdatedTime }
        }

        val watchSections = WatchType.entries
            .filter { it != WatchType.NONE }
            .mapNotNull { type ->
                val items = byType[type].orEmpty()
                if (items.isEmpty()) return@mapNotNull null
                TvWatchlistSection(
                    id = "watch-${type.internalId}",
                    title = watchTypeLabel(type),
                    items = items,
                )
            }

        val favorites = getAllFavorites().mapNotNull { fav ->
            val url = fav.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val apiName = fav.apiName.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = fav.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TvWatchlistItem(
                id = "fav-${fav.id ?: "${apiName}:$url"}",
                title = title,
                url = url,
                apiName = apiName,
                posterUrl = fav.posterUrl?.takeIf { it.isNotBlank() },
                year = fav.year,
                typeLabel = fav.type?.name,
                watchStatusLabel = "Favorites",
                latestUpdatedTime = fav.latestUpdatedTime,
                posterHeaders = fav.posterHeaders?.toMap(),
            )
        }.sortedByDescending { it.latestUpdatedTime }

        val favSection = if (favorites.isEmpty()) {
            null
        } else {
            TvWatchlistSection(
                id = "favorites",
                title = "Favorites",
                items = favorites,
            )
        }

        return watchSections + listOfNotNull(favSection)
    }

    private fun mapBookmark(
        data: DataStoreHelper.BookmarkedData,
        statusLabel: String,
    ): TvWatchlistItem? {
        val url = data.url.takeIf { it.isNotBlank() } ?: return null
        val apiName = data.apiName.takeIf { it.isNotBlank() } ?: return null
        val title = data.name.takeIf { it.isNotBlank() } ?: return null
        return TvWatchlistItem(
            id = "bm-${data.id ?: "${apiName}:$url"}",
            title = title,
            url = url,
            apiName = apiName,
            posterUrl = data.posterUrl?.takeIf { it.isNotBlank() },
            year = data.year,
            typeLabel = data.type?.name,
            watchStatusLabel = statusLabel,
            latestUpdatedTime = data.latestUpdatedTime,
            posterHeaders = data.posterHeaders?.toMap(),
        )
    }

    /** Labels match CloudStream strings.xml (type_* / favorites_list_name). */
    private fun watchTypeLabel(type: WatchType): String = when (type) {
        WatchType.WATCHING -> "Watching"
        WatchType.COMPLETED -> "Completed"
        WatchType.ONHOLD -> "On-Hold"
        WatchType.DROPPED -> "Dropped"
        WatchType.PLANTOWATCH -> "Plan to Watch"
        WatchType.NONE -> "None"
    }
}

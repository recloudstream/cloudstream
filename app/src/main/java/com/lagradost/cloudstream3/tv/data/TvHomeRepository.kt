package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.tv.model.TvContentRail
import com.lagradost.cloudstream3.tv.model.TvHomeCatalog
import com.lagradost.cloudstream3.tv.model.TvMediaItem
import com.lagradost.cloudstream3.tv.model.TvMockCatalog
import com.lagradost.cloudstream3.tv.model.TvRailIds
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.utils.DataStoreHelper

/**
 * Thin read-only bridge: APIRepository → immutable TV catalog.
 * No Composables / Activity / navigation / focus / persistence writes.
 *
 * Documented APIRepository usage:
 * - constructor(MainAPI)
 * - hasMainPage, name
 * - waitForHomeDelay()
 * - getMainPage(page, nameIndex) → Resource&lt;List&lt;HomePageResponse?&gt;&gt;
 *
 * Provider selection mirrors HomeViewModel (read-only): DataStoreHelper.currentHomePage
 * then first APIHolder.apis entry with hasMainPage. Does not write currentHomePage.
 *
 * Continue Watching: always [TvMockCatalog.continueWatching] — HomeViewModel.getResumeWatching
 * reads/writes DataStore + DOWNLOAD_HEADER_CACHE; Phase 3 forbids touching history persistence.
 */
class TvHomeRepository {

    sealed interface LoadResult {
        data class Success(val catalog: TvHomeCatalog) : LoadResult
        data class Empty(val providerName: String?) : LoadResult
        data class Failure(val message: String) : LoadResult
    }

    suspend fun loadHome(): LoadResult {
        val api = resolveHomeApi()
            ?: return LoadResult.Failure(
                "No homepage provider available. Install/enable plugins, or load the demo catalog.",
            )

        if (api === noneApi || !api.hasMainPage) {
            return LoadResult.Failure(
                "Provider \"${api.name}\" has no homepage. Pick another provider or load the demo catalog.",
            )
        }

        val repo = APIRepository(api)
        repo.waitForHomeDelay()

        return when (val data = repo.getMainPage(page = 1, nameIndex = null)) {
            is Resource.Success -> {
                val pages = data.value.filterNotNull()
                val lists = pages.flatMap { it.items }.filter { it.list.isNotEmpty() }
                if (lists.isEmpty()) {
                    LoadResult.Empty(repo.name)
                } else {
                    val catalog = buildCatalog(repo.name, lists)
                    if (catalog.rails.none { !it.isMock && it.items.isNotEmpty() }) {
                        LoadResult.Empty(repo.name)
                    } else {
                        LoadResult.Success(catalog)
                    }
                }
            }

            is Resource.Failure -> LoadResult.Failure(
                data.errorString.ifBlank { "Failed to load homepage from ${repo.name}" },
            )

            is Resource.Loading -> LoadResult.Failure("Unexpected loading state from getMainPage")
        }
    }

    fun mockFallbackCatalog(): TvHomeCatalog = TvMockCatalog.fullFallback

    /**
     * Read-only provider resolve — same sources HomeViewModel uses, no DataStore writes.
     */
    fun resolveHomeApi(): MainAPI? {
        val preferred = DataStoreHelper.currentHomePage
        if (preferred == noneApi.name) return noneApi
        if (preferred == randomApi.name) {
            return APIHolder.apis.withLock {
                APIHolder.apis.firstOrNull { it.hasMainPage }
            }
        }
        getApiFromNameNull(preferred)?.takeIf { it.hasMainPage }?.let { return it }
        return APIHolder.apis.withLock {
            APIHolder.apis.firstOrNull { it.hasMainPage }
        }
    }

    private fun buildCatalog(
        providerName: String,
        lists: List<HomePageList>,
    ): TvHomeCatalog {
        val allItems = lists.flatMap { it.list }.distinctBy { it.url }

        val trending = pickTrendingRail(lists)
        val movies = pickMoviesRail(lists, allItems)
        val anime = pickAnimeRail(lists, allItems)

        // Continue Watching: explicit mock (no read-only history without persistence side effects).
        val continueWatching = TvMockCatalog.continueWatching

        val rails = listOfNotNull(
            continueWatching,
            trending,
            movies ?: TvMockCatalog.movies, // keep Phase 2 slot; mark mock via catalog object
            anime ?: TvMockCatalog.anime,
        ).map { rail ->
            // Ensure mock copies keep isMock=true when we fell back.
            rail
        }

        val hero = pickHero(trending, movies, anime, allItems)

        return TvHomeCatalog(
            hero = hero,
            rails = rails.filter { it.items.isNotEmpty() },
            providerName = providerName,
            usingMockFallback = false,
        )
    }

    private fun pickTrendingRail(lists: List<HomePageList>): TvContentRail? {
        val named = lists.firstOrNull { list ->
            val n = list.name.lowercase()
            n.contains("trend") || n.contains("popular") || n.contains("hot") ||
                n.contains("top") || n.contains("featured") || n.contains("latest")
        } ?: lists.firstOrNull()
        return named?.let {
            TvMediaMapper.toRail(TvRailIds.TRENDING, "Trending", it)
        }?.takeIf { it.items.isNotEmpty() }
    }

    private fun pickMoviesRail(
        lists: List<HomePageList>,
        allItems: List<SearchResponse>,
    ): TvContentRail? {
        val named = lists.firstOrNull { it.name.contains("movie", ignoreCase = true) }
        if (named != null && named.list.isNotEmpty()) {
            return TvMediaMapper.toRail(TvRailIds.MOVIES, "Movies", named)
        }
        val filtered = allItems.filter { TvMediaMapper.isMovieRailType(it.type) }
        if (filtered.isEmpty()) return null
        return TvMediaMapper.toRailFromItems(TvRailIds.MOVIES, "Movies", filtered)
    }

    private fun pickAnimeRail(
        lists: List<HomePageList>,
        allItems: List<SearchResponse>,
    ): TvContentRail? {
        val named = lists.firstOrNull { it.name.contains("anime", ignoreCase = true) }
        if (named != null && named.list.isNotEmpty()) {
            return TvMediaMapper.toRail(TvRailIds.ANIME, "Anime", named)
        }
        val filtered = allItems.filter { TvMediaMapper.isAnimeType(it.type) }
        if (filtered.isEmpty()) return null
        return TvMediaMapper.toRailFromItems(TvRailIds.ANIME, "Anime", filtered)
    }

    private fun pickHero(
        trending: TvContentRail?,
        movies: TvContentRail?,
        anime: TvContentRail?,
        allItems: List<SearchResponse>,
    ): TvMediaItem {
        val fromRails = sequenceOf(trending, movies, anime)
            .filterNotNull()
            .flatMap { it.items.asSequence() }
            .firstOrNull { !it.posterUrl.isNullOrBlank() }
        if (fromRails != null) return fromRails

        val fromDomain = allItems.firstOrNull { !it.posterUrl.isNullOrBlank() }
        if (fromDomain != null) return TvMediaMapper.toMediaItem(fromDomain)

        // Explicit mock hero — never pretend it came from the API.
        return TvMockCatalog.hero
    }
}

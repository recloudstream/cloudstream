package com.lagradost.cloudstream3.ui.player

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.LinkLoadingLimitReached
import com.lagradost.cloudstream3.ui.result.buildResultEpisode
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class Cache(
    val linkCache: MutableSet<ExtractorLink>,
    val subtitleCache: MutableSet<SubtitleData>,
    /** When it was last updated */
    var lastCachedTimestamp: Long = unixTime,
    /** If it has fully loaded */
    var saturated: Boolean,
    /** If matching sources from other providers have fully loaded */
    var alternateSaturated: Boolean = false,
)

class RepoLinkGenerator(
    episodes: List<ResultEpisode>,
    val page: LoadResponse? = null,
    private val includeAllProviderSources: Boolean = false,
    private val allProviderSourceNames: Set<String> = emptySet(),
) : VideoGenerator<ResultEpisode>(episodes) {
    companion object {
        const val TAG = "RepoLink"
        private const val MAX_ALTERNATE_PROVIDER_COUNT = 5
        private const val MAX_ALTERNATE_LINKS_PER_PROVIDER = 5
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
        val cache: HashMap<Pair<String, Int>, Cache> =
            hashMapOf()

        private fun normalizeTitle(title: String?): String {
            return title
                ?.lowercase()
                ?.replace(NON_ALPHANUMERIC_REGEX, "")
                .orEmpty()
        }

        private fun titlesMatch(first: String?, second: String?): Boolean {
            val normalizedFirst = normalizeTitle(first)
            val normalizedSecond = normalizeTitle(second)
            if (normalizedFirst.isBlank() || normalizedSecond.isBlank()) return false
            val allowsPartialMatch = minOf(normalizedFirst.length, normalizedSecond.length) >= 6
            return normalizedFirst == normalizedSecond ||
                    (allowsPartialMatch && (
                            normalizedFirst.contains(normalizedSecond) ||
                                    normalizedSecond.contains(normalizedFirst)
                            ))
        }

        private fun typesMatch(first: TvType?, second: TvType?): Boolean {
            if (first == null || second == null) return true
            return first == second ||
                    (first.isMovieType() && second.isMovieType()) ||
                    (first.isEpisodeBased() && second.isEpisodeBased())
        }

        private fun yearsMatch(first: Int?, second: Int?): Boolean {
            return first == null || second == null || kotlin.math.abs(first - second) <= 1
        }

        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
        private fun ExtractorLink.withProviderDisplayName(providerName: String): ExtractorLink {
            val cleanProviderName = providerName.trim().takeIf { it.isNotBlank() } ?: return this
            if (name.startsWith("$cleanProviderName - ")) return this

            val cleanSourceName = name
                .removePrefix(cleanProviderName)
                .trimStart(' ', '-', ':')
                .ifBlank { name }
            val displayName = "$cleanProviderName - $cleanSourceName"

            return when (this) {
                is ExtractorLinkPlayList -> copy(name = displayName)
                is DrmExtractorLink -> DrmExtractorLink(
                    source = source,
                    name = displayName,
                    url = url,
                    referer = referer,
                    quality = quality,
                    type = type,
                    headers = headers,
                    extractorData = extractorData,
                    kid = kid,
                    key = key,
                    uuid = uuid,
                    kty = kty,
                    keyRequestParameters = keyRequestParameters,
                    licenseUrl = licenseUrl,
                )
                else -> ExtractorLink(
                    source = source,
                    name = displayName,
                    url = url,
                    referer = referer,
                    quality = quality,
                    headers = headers,
                    extractorData = extractorData,
                    type = type,
                    audioTracks = audioTracks,
                )
            }
        }
    }

    override val hasCache = true
    override val canSkipLoading = true
    override fun getId(index: Int): Int? = videos.getOrNull(index)?.id

    // this is a simple array that is used to instantly load links if they are already loaded
    //var linkCache = Array<Set<ExtractorLink>>(size = episodes.size, init = { setOf() })
    //var subsCache = Array<Set<SubtitleData>>(size = episodes.size, init = { setOf() })

    @Throws
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val current = videos.getOrNull(offset) ?: return false

        val currentCache = synchronized(cache) {
            cache[current.apiName to current.id] ?: Cache(
                mutableSetOf(),
                mutableSetOf(),
                unixTime,
                false
            ).also {
                cache[current.apiName to current.id] = it
            }
        }

        // These act as a general filter to prevent duplication of links or names
        // Avoid any possible ConcurrentModificationException
        val currentLinksUrls = ConcurrentHashMap.newKeySet<String>()
        val currentSubsUrls = ConcurrentHashMap.newKeySet<String>()
        // Use atomics as otherwise we get race conditions when incrementing, while rare it did actually happen!
        val lastCountedSuffix = ConcurrentHashMap<String, AtomicInteger>()

        synchronized(currentCache) {
            val outdatedCache =
                unixTime - currentCache.lastCachedTimestamp > 60 * 20 // 20 minutes

            if (outdatedCache || clearCache) {
                currentCache.linkCache.clear()
                currentCache.subtitleCache.clear()
                currentCache.saturated = false
                currentCache.alternateSaturated = false
            } else if (currentCache.linkCache.isNotEmpty()) {
                Log.d(
                    TAG,
                    "Resumed previous loading from ${unixTime - currentCache.lastCachedTimestamp}s ago"
                )
            }

            // call all callbacks
            currentCache.linkCache.forEach { link ->
                currentLinksUrls.add(link.url)
                if (sourceTypes.contains(link.type)) {
                    callback(link.withProviderDisplayName(link.source) to null)
                }
            }

            currentCache.subtitleCache.forEach { sub ->
                currentSubsUrls.add(sub.url)
                lastCountedSuffix.getOrPut(sub.originalName) { AtomicInteger(0) }.incrementAndGet()
                subtitleCallback(sub)
            }

            // this stops all execution if links are cached
            // no extra get requests
            if (currentCache.saturated && (!includeAllProviderSources || currentCache.alternateSaturated)) {
                return true
            }
        }

        suspend fun loadEpisodeLinks(episode: ResultEpisode, maxLinks: Int? = null): Boolean {
            coroutineContext.ensureActive()
            val api = getApiFromNameNull(episode.apiName) ?: return false
            val acceptedLinks = AtomicInteger(0)
            return APIRepository(api).loadLinks(
                episode.data,
                isCasting = isCasting,
                subtitleCallback = { file ->
                    Log.d(TAG, "Loaded SubtitleFile: $file")
                    val correctFile = PlayerSubtitleHelper.getSubtitleData(file)
                    if (correctFile.url.isBlank() || !currentSubsUrls.add(correctFile.url)) {
                        return@loadLinks
                    }

                    // Keep subtitle labels unique when multiple providers expose the same file name.
                    val nameDecoded = correctFile.originalName.html().toString()
                        .trim() // `%3Ch1%3Esub%20name…` → `<h1>sub name…` → `sub name…`
                    val suffixCount =
                        lastCountedSuffix.getOrPut(nameDecoded) { AtomicInteger(0) }.incrementAndGet()

                    val updatedFile =
                        correctFile.copy(originalName = nameDecoded, nameSuffix = "$suffixCount")

                    synchronized(currentCache) {
                        if (currentCache.subtitleCache.add(updatedFile)) {
                            subtitleCallback(updatedFile)
                            currentCache.lastCachedTimestamp = unixTime
                        }
                    }
                },
                callback = { link ->
                    if (maxLinks != null && acceptedLinks.get() >= maxLinks) {
                        throw LinkLoadingLimitReached()
                    }
                    val displayLink = link.withProviderDisplayName(link.source.ifBlank { episode.apiName })
                    Log.d(TAG, "Loaded ExtractorLink: $displayLink")
                    if (displayLink.url.isBlank() || !currentLinksUrls.add(displayLink.url)) {
                        return@loadLinks
                    }
                    acceptedLinks.incrementAndGet()

                    synchronized(currentCache) {
                        if (currentCache.linkCache.add(displayLink)) {
                            if (sourceTypes.contains(displayLink.type)) {
                                callback(Pair(displayLink, null))
                            }

                            currentCache.linkCache.add(displayLink)
                            currentCache.lastCachedTimestamp = unixTime
                        }
                    }
                }
            )
        }

        val result = loadEpisodeLinks(
            current,
            maxLinks = if (includeAllProviderSources) MAX_ALTERNATE_LINKS_PER_PROVIDER else null
        )
        var alternateResult = false
        if (includeAllProviderSources) {
            for (episode in getAlternateProviderEpisodes(current)) {
                coroutineContext.ensureActive()
                alternateResult = withTimeoutOrNull(20_000L) {
                    loadEpisodeLinks(
                        episode,
                        maxLinks = MAX_ALTERNATE_LINKS_PER_PROVIDER
                    )
                } == true || alternateResult
            }
        }

        synchronized(currentCache) {
            currentCache.saturated = currentCache.linkCache.isNotEmpty()
            if (includeAllProviderSources) {
                currentCache.alternateSaturated = true
            }
            currentCache.lastCachedTimestamp = unixTime
        }

        return result || alternateResult
    }

    private suspend fun getAlternateProviderEpisodes(current: ResultEpisode): List<ResultEpisode> {
        coroutineContext.ensureActive()
        val currentPage = page ?: return emptyList()
        if (allProviderSourceNames.isEmpty()) return emptyList()

        val providers = synchronized(APIHolder.apis) {
            APIHolder.apis.filter { api ->
                api.name != current.apiName &&
                        allProviderSourceNames.contains(api.name) &&
                        api.providerType != ProviderType.MetaProvider &&
                        !api.usesWebView &&
                        api.supportedTypes.any { typesMatch(it, currentPage.type) }
            }.take(MAX_ALTERNATE_PROVIDER_COUNT)
        }

        val matches = mutableListOf<ResultEpisode>()
        for (api in providers) {
            coroutineContext.ensureActive()
            withTimeoutOrNull(20_000L) {
                findAlternateProviderEpisode(api, current, currentPage)
            }?.let { matches += it }
        }
        return matches
    }

    private suspend fun findAlternateProviderEpisode(
        api: MainAPI,
        current: ResultEpisode,
        currentPage: LoadResponse,
    ): ResultEpisode? {
        coroutineContext.ensureActive()
        val repo = APIRepository(api)
        val search = repo.search(currentPage.name, 1) as? Resource.Success ?: return null
        coroutineContext.ensureActive()
        val match = search.value.items.firstOrNull { it.matchesCurrentPage(currentPage, strictTitle = true) }
            ?: search.value.items.firstOrNull { it.matchesCurrentPage(currentPage, strictTitle = false) }
            ?: return null
        val load = repo.load(match.url) as? Resource.Success ?: return null
        coroutineContext.ensureActive()
        val response = load.value
        if (!response.matchesCurrentPage(currentPage, strictTitle = false)) return null

        return response.toMatchingResultEpisode(current)
    }

    private fun SearchResponse.matchesCurrentPage(
        currentPage: LoadResponse,
        strictTitle: Boolean,
    ): Boolean {
        val responseType = this.type
        val titleMatches = if (strictTitle) {
            normalizeTitle(name) == normalizeTitle(currentPage.name)
        } else {
            titlesMatch(name, currentPage.name)
        }
        return titleMatches && typesMatch(responseType, currentPage.type) && yearsMatch(getSearchYear(), currentPage.year)
    }

    private fun SearchResponse.getSearchYear(): Int? {
        return when (this) {
            is com.lagradost.cloudstream3.MovieSearchResponse -> year
            is com.lagradost.cloudstream3.TvSeriesSearchResponse -> year
            is com.lagradost.cloudstream3.AnimeSearchResponse -> year
            else -> null
        }
    }

    private fun LoadResponse.matchesCurrentPage(
        currentPage: LoadResponse,
        strictTitle: Boolean,
    ): Boolean {
        val titleMatches = if (strictTitle) {
            normalizeTitle(name) == normalizeTitle(currentPage.name)
        } else {
            titlesMatch(name, currentPage.name)
        }
        return titleMatches && typesMatch(type, currentPage.type) && yearsMatch(year, currentPage.year)
    }

    private fun LoadResponse.toMatchingResultEpisode(current: ResultEpisode): ResultEpisode? {
        val mainId = getId()
        return when (this) {
            is MovieLoadResponse -> if (current.tvType.isMovieType()) {
                buildResultEpisode(
                    name,
                    name,
                    null,
                    0,
                    null,
                    null,
                    dataUrl,
                    apiName,
                    mainId,
                    0,
                    null,
                    null,
                    null,
                    type,
                    mainId,
                    null,
                )
            } else {
                null
            }

            is LiveStreamLoadResponse -> if (current.tvType.isMovieType()) {
                buildResultEpisode(
                    name,
                    name,
                    null,
                    0,
                    null,
                    null,
                    dataUrl,
                    apiName,
                    mainId,
                    0,
                    null,
                    null,
                    null,
                    type,
                    mainId,
                    null,
                )
            } else {
                null
            }

            is TorrentLoadResponse -> if (current.tvType.isMovieType()) {
                buildResultEpisode(
                    name,
                    name,
                    null,
                    0,
                    null,
                    null,
                    torrent ?: magnet ?: "",
                    apiName,
                    mainId,
                    0,
                    null,
                    null,
                    null,
                    type,
                    mainId,
                    null,
                )
            } else {
                null
            }

            is TvSeriesLoadResponse -> episodes.sortedByEpisode().withIndex()
                .firstOrNull { (_, episode) -> episode.matchesCurrentEpisode(current) }
                ?.let { (index, episode) -> buildEpisodeResult(this, episode, current, mainId, index) }

            is AnimeLoadResponse -> episodes.values.flatten().sortedByEpisode().withIndex()
                .firstOrNull { (_, episode) -> episode.matchesCurrentEpisode(current) }
                ?.let { (index, episode) -> buildEpisodeResult(this, episode, current, mainId, index) }

            else -> null
        }
    }

    private fun List<Episode>.sortedByEpisode(): List<Episode> {
        return sortedBy { (it.season ?: 0) * 100_000 + (it.episode ?: 0) }
    }

    private fun Episode.matchesCurrentEpisode(current: ResultEpisode): Boolean {
        val episodeMatches = episode == null || episode == current.episode
        val seasonMatches = season == null || current.seasonIndex == null ||
                season == current.seasonIndex || season == current.season
        return episodeMatches && seasonMatches
    }

    private fun buildEpisodeResult(
        response: LoadResponse,
        episode: Episode,
        current: ResultEpisode,
        mainId: Int,
        index: Int,
    ): ResultEpisode {
        val episodeIndex = episode.episode ?: current.episode
        val seasonIndex = episode.season ?: current.seasonIndex
        val id = mainId + (seasonIndex?.times(100_000) ?: 0) + episodeIndex + 1
        return buildResultEpisode(
            response.name,
            episode.name,
            episode.posterUrl,
            episodeIndex,
            seasonIndex,
            episode.season,
            episode.data,
            response.apiName,
            id,
            index,
            episode.score,
            episode.description,
            current.isFiller,
            response.type,
            mainId,
            current.totalEpisodeIndex,
            airDate = episode.date,
            runTime = episode.runTime,
        )
    }
}

package com.lagradost.cloudstream3.tv.playback

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getApiFromUrlNull
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.metaproviders.SyncRedirector
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.tv.model.TvPlaybackRequest
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.result.buildResultEpisode
import com.lagradost.cloudstream3.ui.result.getId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smallest Activity/Fragment boundary from Compose TV → existing CloudStream playback.
 *
 * Does **not** contain Compose UI, custom player controls, extractors, or a new network stack.
 *
 * ```
 * MovieLoadResponse | selected Episode fields
 *   → buildResultEpisode (same fields as ResultViewModel2)
 *   → RepoLinkGenerator(listOf(ep), page = loadResponse)
 *   → GeneratorPlayer.newInstance(generator, index=0, syncData)
 *   → FragmentTransaction into [R.id.tv_player_container]
 *   → GeneratorPlayer.loadLinks → RepoLinkGenerator.generateLinks → APIRepository.loadLinks
 *   → CS3IPlayer / Media3 (unchanged)
 * ```
 *
 * Phase 6: Movies (Watch Now) + TvSeries/Anime episode path. Live / Torrent rejected.
 * Mock / comingSoon never play. No second player or extractor path.
 */
object TvPlaybackBridge {
    private const val TAG = "TvPlaybackBridge"
    const val PLAYER_BACK_STACK = "tv_compose_generator_player"

    sealed interface LaunchResult {
        data object Launched : LaunchResult
        data object RejectedMock : LaunchResult
        data class Unsupported(val reason: String) : LaunchResult
        data class Failed(val message: String) : LaunchResult
    }

    private sealed interface PrepResult {
        data class Ready(
            val generator: RepoLinkGenerator,
            val syncData: HashMap<String, String>,
        ) : PrepResult

        data class Failed(val message: String) : PrepResult
    }

    private sealed interface LoadOutcome {
        data class Failed(val message: String) : LoadOutcome
        data class Loaded(val response: LoadResponse) : LoadOutcome
    }

    /**
     * Suspends for the same load path as details (APIRepository.load), then commits
     * [GeneratorPlayer] on the UI thread. Caller must be an Activity that hosts
     * [R.id.tv_player_container] (see activity_tv_compose_probe.xml).
     */
    suspend fun launch(
        activity: FragmentActivity,
        request: TvPlaybackRequest,
    ): LaunchResult {
        if (request.isMock) {
            Log.i(TAG, "Rejected mock/demo playback for ${request.title}")
            return LaunchResult.RejectedMock
        }
        if (request.comingSoon) {
            return LaunchResult.Unsupported("Coming soon — not released yet")
        }

        val supported = when {
            request.isMovie -> true
            request.isEpisodePlayback &&
                (request.variantLabel == "TvSeries" || request.variantLabel == "Anime") -> true
            else -> false
        }
        if (!supported) {
            val reason = unsupportedReason(request.variantLabel)
            Log.i(TAG, "Unsupported variant ${request.variantLabel}: $reason")
            return LaunchResult.Unsupported(reason)
        }

        if (!request.isMovie && request.episodeData.isNullOrBlank()) {
            return LaunchResult.Failed("Selected episode has no playable data")
        }

        val prepared = withContext(Dispatchers.IO) {
            runCatching {
                if (request.isMovie) prepareMovieGenerator(request)
                else prepareEpisodeGenerator(request)
            }
                .onFailure { logError(it) }
                .getOrElse {
                    PrepResult.Failed(it.message ?: "Failed to prepare playback")
                }
        }

        return when (prepared) {
            is PrepResult.Failed -> LaunchResult.Failed(prepared.message)
            is PrepResult.Ready -> {
                commitPlayer(activity, prepared)
                LaunchResult.Launched
            }
        }
    }

    fun unsupportedReason(variantLabel: String): String = when (variantLabel) {
        "TvSeries", "Anime" -> "Select a playable episode first"
        "LiveStream" -> "Live playback is not supported in Compose TV yet"
        "Torrent" -> "Torrent playback is not supported in Compose TV yet"
        else -> "Playback for $variantLabel is not supported"
    }

    fun report(activity: FragmentActivity, result: LaunchResult) {
        when (result) {
            LaunchResult.Launched -> Unit
            LaunchResult.RejectedMock ->
                showToast(activity, "Demo items cannot start real playback", null)
            is LaunchResult.Unsupported ->
                showToast(activity, result.reason, null)
            is LaunchResult.Failed ->
                showToast(activity, result.message, null)
        }
    }

    private suspend fun resolveLoad(request: TvPlaybackRequest): LoadOutcome {
        if (APIRepository.isInvalidData(request.url)) {
            return LoadOutcome.Failed("Invalid content URL")
        }
        val api = getApiFromNameNull(request.apiName) ?: getApiFromUrlNull(request.url)
            ?: return LoadOutcome.Failed(
                "This provider does not exist (${request.apiName}). Retry after plugins finish loading.",
            )

        val validUrlResource = safeApiCall { SyncRedirector.redirect(request.url, api) }
        val validUrl = when (validUrlResource) {
            is Resource.Success -> validUrlResource.value
            is Resource.Failure -> {
                return LoadOutcome.Failed(
                    validUrlResource.errorString.ifBlank {
                        "Failed to resolve content URL for ${request.apiName}"
                    },
                )
            }
            is Resource.Loading -> request.url
        }

        return when (val load = APIRepository(api).load(validUrl)) {
            is Resource.Success -> LoadOutcome.Loaded(load.value)
            is Resource.Failure -> LoadOutcome.Failed(
                load.errorString.ifBlank { "Failed to load ${request.title}" },
            )
            is Resource.Loading -> LoadOutcome.Failed(
                "Unexpected loading state from APIRepository.load",
            )
        }
    }

    private suspend fun prepareMovieGenerator(request: TvPlaybackRequest): PrepResult {
        return when (val outcome = resolveLoad(request)) {
            is LoadOutcome.Failed -> PrepResult.Failed(outcome.message)
            is LoadOutcome.Loaded -> {
                val movie = outcome.response as? MovieLoadResponse
                    ?: return PrepResult.Failed(
                        "Expected MovieLoadResponse, got ${outcome.response::class.simpleName}",
                    )
                if (movie.dataUrl.isBlank()) {
                    return PrepResult.Failed("Movie has no playable data URL")
                }
                val episode = movieToResultEpisode(movie)
                val generator = RepoLinkGenerator(listOf(episode), page = movie)
                Log.i(TAG, "Prepared RepoLinkGenerator for movie id=${episode.id} api=${movie.apiName}")
                PrepResult.Ready(generator, HashMap(movie.syncData))
            }
        }
    }

    /**
     * Series / Anime — same GeneratorPlayer entry as movies, with episode ResultEpisode.
     * Request carries Episode.data + metadata; page = reloaded LoadResponse.
     */
    private suspend fun prepareEpisodeGenerator(request: TvPlaybackRequest): PrepResult {
        return when (val outcome = resolveLoad(request)) {
            is LoadOutcome.Failed -> PrepResult.Failed(outcome.message)
            is LoadOutcome.Loaded -> {
                val response = outcome.response
                if (response !is TvSeriesLoadResponse && response !is AnimeLoadResponse) {
                    return PrepResult.Failed(
                        "Expected series/anime LoadResponse, got ${response::class.simpleName}",
                    )
                }
                val data = request.episodeData?.takeIf { it.isNotBlank() }
                    ?: return PrepResult.Failed("Episode has no playable data")
                val episodeId = request.episodeId
                    ?: return PrepResult.Failed("Episode id missing")
                val episodeNumber = request.episodeNumber
                    ?: return PrepResult.Failed("Episode number missing")
                val parentId = response.getId()

                val episode = buildResultEpisode(
                    headerName = response.name,
                    name = request.episodeName,
                    poster = request.episodePoster,
                    episode = episodeNumber,
                    seasonIndex = request.seasonIndex,
                    season = request.displaySeason,
                    data = data,
                    apiName = response.apiName,
                    id = episodeId,
                    index = request.episodeIndex ?: 0,
                    rating = null,
                    description = request.episodeDescription,
                    isFiller = null,
                    tvType = response.type,
                    parentId = parentId,
                    totalEpisodeIndex = request.totalEpisodeIndex,
                    airDate = request.airDate,
                    runTime = request.runTime,
                    seasonData = null,
                )
                val generator = RepoLinkGenerator(listOf(episode), page = response)
                Log.i(
                    TAG,
                    "Prepared RepoLinkGenerator for episode id=$episodeId " +
                        "S${request.seasonIndex ?: "-"}E$episodeNumber api=${response.apiName}",
                )
                PrepResult.Ready(generator, HashMap(response.syncData))
            }
        }
    }

    private fun movieToResultEpisode(loadResponse: MovieLoadResponse) =
        buildResultEpisode(
            headerName = loadResponse.name,
            name = loadResponse.name,
            poster = null,
            episode = 0,
            seasonIndex = null,
            season = null,
            data = loadResponse.dataUrl,
            apiName = loadResponse.apiName,
            id = loadResponse.getId(),
            index = 0,
            rating = null,
            description = null,
            isFiller = null,
            tvType = loadResponse.type,
            parentId = loadResponse.getId(),
            totalEpisodeIndex = null,
        )

    private fun commitPlayer(activity: FragmentActivity, ready: PrepResult.Ready) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            val containerId = R.id.tv_player_container
            if (activity.findViewById<android.view.View>(containerId) == null) {
                Log.e(TAG, "Missing R.id.tv_player_container — cannot host GeneratorPlayer")
                showToast(activity, "Player host missing in Activity layout", null)
                return@runOnUiThread
            }
            val args = GeneratorPlayer.newInstance(ready.generator, 0, ready.syncData)
            val fragment = GeneratorPlayer().apply { arguments = args }
            val fm = activity.supportFragmentManager
            if (fm.findFragmentByTag(PLAYER_BACK_STACK) != null) {
                fm.popBackStack(
                    PLAYER_BACK_STACK,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE,
                )
            }
            fm.beginTransaction()
                .replace(containerId, fragment, PLAYER_BACK_STACK)
                .addToBackStack(PLAYER_BACK_STACK)
                .commit()
            Log.i(TAG, "Committed GeneratorPlayer into tv_player_container")
        }
    }
}

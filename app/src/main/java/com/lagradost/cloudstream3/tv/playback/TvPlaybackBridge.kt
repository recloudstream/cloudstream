package com.lagradost.cloudstream3.tv.playback

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getApiFromUrlNull
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.R
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
 * Adapts an immutable [TvPlaybackRequest] into the same path ResultViewModel2 uses for movies:
 *
 * ```
 * MovieLoadResponse
 *   → buildResultEpisode (same fields as ResultViewModel2)
 *   → RepoLinkGenerator(listOf(ep), page = loadResponse)
 *   → GeneratorPlayer.newInstance(generator, index=0, syncData)
 *   → FragmentTransaction into [R.id.tv_player_container]
 *   → GeneratorPlayer.loadLinks → RepoLinkGenerator.generateLinks → APIRepository.loadLinks
 *   → CS3IPlayer / Media3 (unchanged)
 * ```
 *
 * Phase 5: movies only. Series / Anime / Live / Torrent are rejected with a clear reason.
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
        if (!request.isMovie) {
            val reason = unsupportedReason(request.variantLabel)
            Log.i(TAG, "Unsupported variant ${request.variantLabel}: $reason")
            return LaunchResult.Unsupported(reason)
        }

        val prepared = withContext(Dispatchers.IO) {
            runCatching { prepareMovieGenerator(request) }
                .onFailure { logError(it) }
                .getOrElse {
                    return@withContext PrepResult.Failed(
                        it.message ?: "Failed to prepare movie playback",
                    )
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
        "TvSeries" -> "Series episode picker is Phase 6"
        "Anime" -> "Anime episode / dub selection is Phase 6"
        "LiveStream" -> "Live playback wiring deferred (Phase 6)"
        "Torrent" -> "Torrent playback wiring deferred (Phase 6)"
        else -> "Playback for $variantLabel is not supported in Phase 5"
    }

    /** Show a short toast for non-launch outcomes (Activity-level UX only). */
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

    private sealed interface PrepResult {
        data class Ready(
            val generator: RepoLinkGenerator,
            val syncData: HashMap<String, String>,
        ) : PrepResult

        data class Failed(val message: String) : PrepResult
    }

    /**
     * Mirrors ResultViewModel2 movie branch:
     * resolve API → SyncRedirector → APIRepository.load → MovieLoadResponse
     * → buildResultEpisode → RepoLinkGenerator.
     */
    private suspend fun prepareMovieGenerator(request: TvPlaybackRequest): PrepResult {
        if (APIRepository.isInvalidData(request.url)) {
            return PrepResult.Failed("Invalid content URL")
        }
        val api = getApiFromNameNull(request.apiName) ?: getApiFromUrlNull(request.url)
            ?: return PrepResult.Failed(
                "This provider does not exist (${request.apiName}). Retry after plugins finish loading.",
            )

        val validUrlResource = safeApiCall { SyncRedirector.redirect(request.url, api) }
        val validUrl = when (validUrlResource) {
            is Resource.Success -> validUrlResource.value
            is Resource.Failure -> {
                return PrepResult.Failed(
                    validUrlResource.errorString.ifBlank {
                        "Failed to resolve content URL for ${request.apiName}"
                    },
                )
            }
            is Resource.Loading -> request.url
        }

        val load = APIRepository(api).load(validUrl)
        val response = when (load) {
            is Resource.Success -> load.value
            is Resource.Failure -> {
                return PrepResult.Failed(
                    load.errorString.ifBlank { "Failed to load ${request.title}" },
                )
            }
            is Resource.Loading -> {
                return PrepResult.Failed("Unexpected loading state from APIRepository.load")
            }
        }

        val movie = response as? MovieLoadResponse
            ?: return PrepResult.Failed(
                "Expected MovieLoadResponse, got ${response::class.simpleName}",
            )

        if (movie.dataUrl.isBlank()) {
            return PrepResult.Failed("Movie has no playable data URL")
        }

        val episode = movieToResultEpisode(movie)
        val generator = RepoLinkGenerator(listOf(episode), page = movie)
        val syncData = HashMap(movie.syncData)
        Log.i(TAG, "Prepared RepoLinkGenerator for movie id=${episode.id} api=${movie.apiName}")
        return PrepResult.Ready(generator, syncData)
    }

    /** Exact field mapping used by ResultViewModel2 for MovieLoadResponse. */
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
            // Same entry as ResultViewModel2 ACTION_PLAY_EPISODE_IN_PLAYER:
            // GeneratorPlayer.newInstance(generator, index, syncData)
            val args = GeneratorPlayer.newInstance(ready.generator, 0, ready.syncData)
            val fragment = GeneratorPlayer().apply { arguments = args }
            val fm = activity.supportFragmentManager
            // Replace any leftover player, then push so Back / exitPlayer pops cleanly.
            if (fm.findFragmentByTag(PLAYER_BACK_STACK) != null) {
                fm.popBackStack(PLAYER_BACK_STACK, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            fm.beginTransaction()
                .replace(containerId, fragment, PLAYER_BACK_STACK)
                .addToBackStack(PLAYER_BACK_STACK)
                .commit()
            Log.i(TAG, "Committed GeneratorPlayer into tv_player_container")
        }
    }
}

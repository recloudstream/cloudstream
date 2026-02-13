package com.lagradost.cloudstream3.ui.car

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.utils.CarHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.PlayerCarHelper
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerCarScreen(
    carContext: CarContext,
    val item: SearchResponse? = null,
    val loadResponse: LoadResponse? = null,
    val selectedEpisode: Episode? = null,
    val playlist: List<Episode>? = null,
    var startTime: Long = 0L,
    val fileUri: String? = null,
    val videoId: Int? = null,
    val parentId: Int? = null,
    val preSelectedSource: ExtractorLink? = null
) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG = "PlayerCarScreen"
    }

    private var activeEpisode: Episode? = selectedEpisode
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isPlaying = true
    private var isFillMode = false

    private var currentEpisodeId: Int? = null
    private var currentParentId: Int? = null
    private var saveProgressJob: Job? = null

    // Shared callbacks for strategy-driven template building
    private val templateCallbacks = TemplateCallbacks(
        carContext = carContext,
        getIsPlaying = { isPlaying },
        getIsFillMode = { isFillMode },
        isNextEnabled = { playlist != null && activeEpisode != null && playlist.indexOf(activeEpisode) < playlist.size - 1 },
        onExit = { screenManager.pop() },
        onPlayPause = {
            if (isPlaying) { player?.pause(); saveProgress() } else { player?.play() }
        },
        onSeekBack = ::seekBack,
        onSeekForward = ::seekForward,
        onToggleFillMode = {
            isFillMode = !isFillMode
            surfaceStrategy.applyVideoScale(isFillMode)
            showToast(
                if (!isFillMode) CarStrings.get(R.string.car_fit_to_screen)
                else CarStrings.get(R.string.car_fill_screen)
            )
        },
        onNextEpisode = { scope.launch { loadNextEpisode() } },
        onInvalidate = ::invalidate
    )

    // Surface rendering strategy (Advanced or Simple)
    private val surfaceStrategy: PlayerSurfaceStrategy = if (DataStoreHelper.carPlayerMode == 0) {
        PresentationSurfaceStrategy(
            context = carContext,
            callbacks = templateCallbacks,
            getPlayer = { player },
            getIsPlaying = { isPlaying },
            onSeekBack = ::seekBack,
            onSeekForward = ::seekForward,
            onSaveProgress = ::saveProgress,
            getTitle = { item?.name ?: activeEpisode?.name ?: "Unknown" },
            getSubtitle = { activeEpisode?.let { ep -> "S${ep.season} E${ep.episode} - ${ep.name}" } },
            onError = ::showToast
        )
    } else {
        DirectSurfaceStrategy(callbacks = templateCallbacks, getPlayer = { player })
    }

    init {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this@PlayerCarScreen)
                        player?.playWhenReady = true
                    }
                    Lifecycle.Event.ON_STOP -> {
                        player?.playWhenReady = false
                        saveProgress()
                        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        surfaceStrategy.release()
                        mediaSession?.release()
                        mediaSession = null
                        player?.release()
                        player = null
                        scope.cancel()
                    }
                    else -> {}
                }
            }
        })

        if (fileUri != null) {
            currentEpisodeId = videoId
            currentParentId = parentId
            if (activeEpisode == null) {
                scope.launch(Dispatchers.IO) {
                    populateEpisodeFromDownloadCache()
                    withContext(Dispatchers.Main) { startPlayback(fileUri) }
                }
            } else {
                scope.launch { startPlayback(fileUri) }
            }
        } else {
            loadMedia(item?.url)
        }
    }

    // --- SurfaceCallback ---

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surfaceStrategy.onSurfaceAvailable(surfaceContainer)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surfaceStrategy.onSurfaceDestroyed(surfaceContainer)
    }

    override fun onClick(x: Float, y: Float) {
        surfaceStrategy.onClick(x, y)
    }

    // --- Template ---

    override fun onGetTemplate(): Template = surfaceStrategy.buildTemplate()

    // --- Media Loading ---

    private fun loadMedia(url: String?) {
        updateStatus(CarStrings.get(R.string.car_loading))
        scope.launch {
            val data = getLoadResponse()

            if (data == null) {
                if (item?.type == TvType.Live) {
                    Log.d(TAG, "Details load failed, attempting direct playback for Live content")
                    withContext(Dispatchers.Main) { startPlayback(item.url) }
                    return@launch
                }
                showToast(CarStrings.get(R.string.car_unable_to_load_details))
                return@launch
            }

            resolveIds(data)
            loadLinks(data)
        }
    }

    private suspend fun getLoadResponse(): LoadResponse? {
        if (loadResponse != null) return loadResponse
        val searchItem = item ?: return null
        val api = getApiFromNameNull(searchItem.apiName) ?: return null
        return try {
            when (val result = APIRepository(api).load(searchItem.url)) {
                is Resource.Success -> result.value
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading details for ${searchItem.url}", e)
            null
        }
    }

    private fun resolveIds(data: LoadResponse) {
        currentParentId = item?.id ?: data.getId()
        PlayerCarHelper.saveHeaderCache(item, data, currentParentId)

        currentEpisodeId = if (data is TvSeriesLoadResponse && activeEpisode != null) {
            CarHelper.generateConsistentEpisodeId(activeEpisode!!, data)
        } else {
            currentParentId
        }

        Log.d(TAG, "Resolved IDs - Name: ${data.name} | Parent: $currentParentId | Episode: $currentEpisodeId")
    }

    private suspend fun loadLinks(data: LoadResponse) {
        val api = getApiFromNameNull(data.apiName) ?: return
        val links = mutableListOf<ExtractorLink>()
        try {
            val urlToLoad = when {
                activeEpisode != null -> activeEpisode!!.data
                data is TvSeriesLoadResponse -> data.episodes.firstOrNull()?.data
                data is AnimeLoadResponse -> data.episodes.values.flatten().firstOrNull()?.data
                data is MovieLoadResponse -> data.dataUrl
                else -> data.url
            }

            if (urlToLoad == null) {
                showToast(CarStrings.get(R.string.car_no_playable_content))
                return
            }

            if (preSelectedSource != null) {
                startPlayback(preSelectedSource)
            } else {
                api.loadLinks(urlToLoad, false, {}, { link -> links.add(link) })
                if (links.isNotEmpty()) {
                    startPlayback(links.sortedByDescending { it.quality }.first())
                } else {
                    showToast(CarStrings.get(R.string.car_no_link_found))
                }
            }
        } catch (e: Exception) {
            showToast("${CarStrings.get(R.string.car_error_loading_links)}: ${e.message}")
            e.printStackTrace()
        }
    }

    // --- Playback ---

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun startPlayback(link: Any) {
        val url = when (link) {
            is ExtractorLink -> link.url
            is String -> link
            else -> return
        }

        withContext(Dispatchers.Main) {
            updateStatus(CarStrings.get(R.string.car_starting_playback))
            if (player == null) {
                player = ExoPlayer.Builder(carContext).build().apply {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
                    setAudioAttributes(audioAttributes, true)
                }
            }
            player?.let { p ->
                setupMediaSession(p)
                setupPlaybackListener(p)

                surfaceStrategy.attachPlayer(p)
                surfaceStrategy.applyVideoScale(isFillMode)

                val mediaMetadata = PlayerCarHelper.createMediaMetadata(carContext, item, activeEpisode)
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(mediaMetadata)

                if (link is ExtractorLink && link.isM3u8) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }

                p.setMediaItem(mediaItemBuilder.build())
                p.prepare()
                if (startTime > 0L) p.seekTo(startTime)
                p.play()
                updateStatus(CarStrings.get(R.string.car_playing))
            }
        }
    }

    private fun setupMediaSession(p: ExoPlayer) {
        if (mediaSession != null) return

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(p) {
            override fun seekToNext() {
                scope.launch { loadNextEpisode() }
            }
            override fun seekToPrevious() {
                seekBack()
            }
        }

        mediaSession = MediaSession.Builder(carContext, forwardingPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(commands)
                        .build()
                }
            })
            .build()
    }

    private fun setupPlaybackListener(p: ExoPlayer) {
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                invalidate()
                surfaceStrategy.updatePlayPauseState()

                saveProgressJob?.cancel()
                if (playing) {
                    saveProgressJob = scope.launch {
                        while (true) {
                            delay(1000)
                            withContext(Dispatchers.Main) {
                                player?.let { surfaceStrategy.updateProgress(it.currentPosition, it.duration) }
                                if (System.currentTimeMillis() % 10000 < 1000) saveProgress()
                            }
                        }
                    }
                } else {
                    saveProgress()
                }
            }
        })
    }

    // --- Seek & Episode Navigation ---

    private fun seekBack() {
        player?.let { p ->
            val newPos = (p.currentPosition - 30_000).coerceAtLeast(0)
            p.seekTo(newPos)
            updateStatus("-30s")
        }
    }

    private fun seekForward() {
        player?.let { p ->
            val newPos = (p.currentPosition + 30_000).coerceAtMost(p.duration)
            p.seekTo(newPos)
            updateStatus("+30s")
        }
    }

    private suspend fun loadNextEpisode() {
        if (playlist == null || activeEpisode == null) {
            showToast("No playlist available")
            return
        }

        val currentIndex = playlist.indexOfFirst { it.data == activeEpisode!!.data }
        if (currentIndex != -1 && currentIndex < playlist.size - 1) {
            val nextEp = playlist[currentIndex + 1]
            activeEpisode = nextEp
            val data = getLoadResponse()
            currentEpisodeId = CarHelper.generateConsistentEpisodeId(nextEp, data) ?: nextEp.data.hashCode()
            startTime = 0L

            showToast("Loading: ${nextEp.name}")
            withContext(Dispatchers.Main) { loadMedia(nextEp.data) }
        } else {
            showToast("No more episodes")
        }
    }

    // --- Progress ---

    private fun saveProgress() {
        val p = player ?: return
        PlayerCarHelper.saveProgress(p.currentPosition, p.duration, item, activeEpisode, currentEpisodeId, currentParentId)
    }



    // --- Helpers ---

    private fun populateEpisodeFromDownloadCache() {
        try {
            val cachedEp = carContext.getKey<VideoDownloadHelper.DownloadEpisodeCached>(
                DOWNLOAD_EPISODE_CACHE, videoId.toString()
            )
            if (cachedEp != null) {
                @Suppress("DEPRECATION_ERROR")
                activeEpisode = Episode(
                    data = "",
                    name = cachedEp.name,
                    season = cachedEp.season,
                    episode = cachedEp.episode,
                    posterUrl = cachedEp.poster
                )
                Log.d(TAG, "Populated activeEpisode from download: S${cachedEp.season} E${cachedEp.episode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading download metadata", e)
        }
    }

    private fun updateStatus(msg: String) {
        if (msg != "Playing") showToast(msg)
    }

    private fun showToast(msg: String) {
        CarToast.makeText(carContext, msg, CarToast.LENGTH_LONG).show()
    }
}

package com.lagradost.cloudstream3.ui.car

import com.lagradost.cloudstream3.utils.CarHelper
import com.lagradost.cloudstream3.utils.PlayerCarHelper

import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
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
import androidx.car.app.model.CarIcon

import androidx.core.graphics.drawable.IconCompat
import com.lagradost.cloudstream3.R
import androidx.media3.common.MediaMetadata
import android.net.Uri

import androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
import androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.ExtractorLink

import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.Episode

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper.setVideoWatchState
import com.lagradost.cloudstream3.ui.player.NEXT_WATCH_EPISODE_PERCENTAGE
import kotlinx.coroutines.delay

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

    private var activeEpisode: Episode? = selectedEpisode
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var mediaSession: MediaSession? = null
    private var isPlaying = true

    private var resizeMode = VIDEO_SCALING_MODE_SCALE_TO_FIT

    private var currentEpisodeId: Int? = null
    private var currentParentId: Int? = null
    private var saveProgressJob: Job? = null
    private var showSeekControls = false

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
            
            // Fix: Populate selectedEpisode metadata for Downloads so saveProgress works correctly
            if (this.activeEpisode == null) {
                scope.launch(Dispatchers.IO) {
                   try {
                       val cachedEp = carContext.getKey<VideoDownloadHelper.DownloadEpisodeCached>(
                           DOWNLOAD_EPISODE_CACHE,
                           videoId.toString()
                       )
                       if (cachedEp != null) {
                           @Suppress("DEPRECATION_ERROR")
                           val ep = Episode(
                               data = "", // URL not available in cache, but ID is used from videoId so acceptable
                               name = cachedEp.name,
                               season = cachedEp.season,
                               episode = cachedEp.episode,
                               posterUrl = cachedEp.poster
                           )
                           this@PlayerCarScreen.activeEpisode = ep
                           Log.d("PlayerCarScreen", "Populated activeEpisode from Download: S${cachedEp.season} E${cachedEp.episode}")
                       }
                   } catch (e: Exception) {
                       Log.e("PlayerCarScreen", "Error loading download metadata", e)
                   }
                   withContext(Dispatchers.Main) {
                       startPlayback(fileUri)
                   }
                }
            } else {
                 scope.launch { startPlayback(fileUri) }
            }
        } else {
            loadMedia(item?.url)
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val newSurface = surfaceContainer.surface
        if (newSurface != null) {
            surface = newSurface
            player?.setVideoSurface(newSurface)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surface = null
        player?.setVideoSurface(null)
    }

    private fun loadMedia(url: String?) {
        updateStatus(CarStrings.get(R.string.car_loading))
        scope.launch {
             val data = getLoadResponse()
             
             if (data == null) {
                 if (item?.type == TvType.Live) {
                     Log.d("PlayerCarScreen", "Details load failed, attempting direct playback for Live content")
                     withContext(Dispatchers.Main) {
                         startPlayback(item!!.url)
                     }
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
        // If we already have the LoadResponse (passed from Details), use it.
        // Otherwise, fetch it using item.url
        return if (loadResponse != null) {
            loadResponse
        } else if (item != null) {
            val apiName = item.apiName
            val api = getApiFromNameNull(apiName) ?: return null
            val repo = APIRepository(api)
            try {
                when(val result = repo.load(item.url)) {
                    is Resource.Success -> result.value
                    else -> null
                }
            } catch (e: Exception) {
                Log.e("PlayerCarScreen", "Error loading details for ${item.url}", e)
                null
            }
        } else {
            null
        }
    }


    private fun resolveIds(data: LoadResponse) {
         val apiName = data.apiName
         val api = getApiFromNameNull(apiName) ?: return
         
         // Priority: 1. Item ID (if available from Home/Search and valid)
         //           2. Data URL Hashcode (using strict cleaning logic)
         currentParentId = item?.id ?: data.getId()
         PlayerCarHelper.saveHeaderCache(item, data, currentParentId)

         if (data is TvSeriesLoadResponse && activeEpisode != null) {
             currentEpisodeId = CarHelper.generateConsistentEpisodeId(activeEpisode!!, data)
         } else {
             // For movies, we use the parent ID
             currentEpisodeId = currentParentId
         }
         
         Log.d("PlayerCarScreen", "Resolved IDs (STRICT) - Name: ${data.name} | Parent: $currentParentId | Episode: $currentEpisodeId | Type: ${data.javaClass.simpleName}")
    }

    private suspend fun loadLinks(data: LoadResponse) {
         val apiName = data.apiName
         val api = getApiFromNameNull(apiName) ?: return
         val links = mutableListOf<ExtractorLink>()
         try {
             val urlToLoad = when {
                 activeEpisode != null -> activeEpisode!!.data
                 data is com.lagradost.cloudstream3.TvSeriesLoadResponse -> {
                     // Auto-select first episode if none selected
                     data.episodes.firstOrNull()?.data
                 }
                 data is com.lagradost.cloudstream3.AnimeLoadResponse -> {
                     // Auto-select first episode from first available category
                     data.episodes.values.flatten().firstOrNull()?.data
                 }
                 data is com.lagradost.cloudstream3.MovieLoadResponse -> {
                     data.dataUrl
                 }
                 else -> data.url
             }

             if (urlToLoad == null) {
                 showToast(CarStrings.get(R.string.car_no_playable_content))
                 return
             }

             // Use pre-selected source if provided, otherwise load and auto-select best
             if (preSelectedSource != null) {
                 startPlayback(preSelectedSource)
             } else {
                 // Load links using the API
                 val success = api.loadLinks(urlToLoad, false, {}, { link ->
                     links.add(link)
                 })
                 
                 if(links.isNotEmpty()) {
                     val bestLink = links.sortedByDescending { it.quality }.first()
                     startPlayback(bestLink)
                 } else {
                     showToast(CarStrings.get(R.string.car_no_link_found))
                 }
             }
         } catch (e: Exception) {
             showToast("${CarStrings.get(R.string.car_error_loading_links)}: ${e.message}")
             e.printStackTrace()
         }
    }

    // saveHeaderCache moved to PlayerCarHelper

    private fun saveProgress() {
        val p = player ?: return
        PlayerCarHelper.saveProgress(
            p.currentPosition,
            p.duration,
            item,
            activeEpisode,
            currentEpisodeId,
            currentParentId
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun startPlayback(link: Any) {
        val url = when(link) {
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
                 // Wrap in ForwardingPlayer to intercept commands
                 val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(p) {
                     override fun seekToNext() {
                         Log.d("PlayerCarScreen", "ForwardingPlayer seekToNext")
                         scope.launch { loadNextEpisode() }
                     }
                     
                     override fun seekToPrevious() {
                        Log.d("PlayerCarScreen", "ForwardingPlayer seekToPrevious")
                         seekBack()
                     }
                 }

                 // Initialize MediaSession with ForwardingPlayer
                 if (mediaSession == null) {
                     mediaSession = MediaSession.Builder(carContext, forwardingPlayer)
                        .setCallback(object : MediaSession.Callback {
                            override fun onConnect(
                                session: MediaSession,
                                controller: MediaSession.ControllerInfo
                            ): MediaSession.ConnectionResult {
                                val availableCommands = androidx.media3.common.Player.Commands.Builder()
                                    .add(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                                    .add(androidx.media3.common.Player.COMMAND_STOP)
                                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                                    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                                    .build()
                                    
                                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                    .setAvailablePlayerCommands(availableCommands)
                                    .build()
                            }
                        })
                        .build()
                 }
                 
                 p.addListener(object : Player.Listener {
                     override fun onIsPlayingChanged(isPlaying: Boolean) {
                         this@PlayerCarScreen.isPlaying = isPlaying
                         invalidate()
                         
                         this@PlayerCarScreen.saveProgressJob?.cancel()
                         if (isPlaying) {
                             this@PlayerCarScreen.saveProgressJob = scope.launch {
                                 while(true) {
                                     kotlinx.coroutines.delay(10_000)
                                     withContext(Dispatchers.Main) {
                                         saveProgress()
                                     }
                                 }
                             }
                         } else {
                             saveProgress()
                         }
                     }
                 })

                 surface?.let { p.setVideoSurface(it) }
                 p.videoScalingMode = resizeMode
                 
                 // Create MediaMetadata using Helper (runs on IO)
                 val mediaMetadata = PlayerCarHelper.createMediaMetadata(carContext, item, activeEpisode)

                 val mediaItemBuilder = MediaItem.Builder()
                     .setUri(url)
                     .setMediaMetadata(mediaMetadata)
                     
                 if (link is ExtractorLink && link.isM3u8) {
                     mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                 }
                 
                 p.setMediaItem(mediaItemBuilder.build())
                 p.prepare()
                 if (startTime > 0L) {
                     p.seekTo(startTime)
                 }
                 p.play()
                 updateStatus(CarStrings.get(R.string.car_playing))
 
                 // Start periodic save loop
                 // startSaveLoop()
            }
        }
    }

    private fun startSaveLoop() {
        saveProgressJob?.cancel()
        saveProgressJob = scope.launch {
            Log.d("PlayerCarScreen", "Starting Save Loop")
            while (true) {
                delay(15_000) // Save every 15 seconds
                if (isPlaying) {
                    withContext(Dispatchers.Main) {
                        saveProgress()
                    }
                }
            }
        }
    }
    
    private fun showToast(msg: String) {
        androidx.car.app.CarToast.makeText(carContext, msg, androidx.car.app.CarToast.LENGTH_LONG).show()
    }
    
    // Using a minimal Navigation Template to get the surface
    // Using a minimal Navigation Template to get the surface
    override fun onGetTemplate(): Template {
        val playPauseAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext, 
                        if (isPlaying) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24
                    )
                ).build()
            )
            .setOnClickListener {
                if (isPlaying) {
                    player?.pause()
                    saveProgress() // Save immediately when pausing
                } else {
                    player?.play()
                }
            }
            .build()

        val actionStripBuilder = ActionStrip.Builder()

        if (showSeekControls) {
            // SEEK MODE: [Back (to menu)], [-10], [Play], [+30]
            
            val backToMenuAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_arrow_back_24)).build())
                .setOnClickListener {
                    showSeekControls = false
                    invalidate()
                }
                .build()

            val seekBackAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.go_back_30)).build())
                .setOnClickListener { seekBack() }
                .build()

            val seekForwardAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.go_forward_30)).build())
                .setOnClickListener { seekForward() }
                .build()

            val resizeAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_aspect_ratio_24)).build())
                .setOnClickListener {
                    resizeMode = if (resizeMode == VIDEO_SCALING_MODE_SCALE_TO_FIT) {
                        VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                    player?.videoScalingMode = resizeMode
                    showToast(if (resizeMode == VIDEO_SCALING_MODE_SCALE_TO_FIT) CarStrings.get(R.string.car_fit_to_screen) else CarStrings.get(R.string.car_fill_screen))
                }
                .build()

            actionStripBuilder.addAction(backToMenuAction)
                .addAction(seekBackAction)
                .addAction(seekForwardAction)
                .addAction(resizeAction)
                
        } else {
            // DEFAULT MODE: [Back (Exit)], [Play], [Resize], [Seek Controls]
            
            val exitAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_arrow_back_24)).build())
                .setOnClickListener { screenManager.pop() }
                .build()


                
            val seekBackAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.go_back_30)).build())
            .setOnClickListener {
                seekBack()
            }
            .build()
                
            val openSeekAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_tune_24)).build())
                .setOnClickListener {
                    showSeekControls = true
                    invalidate()
                }
                .build()

            val nextAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_skip_next_24)).build())
                .setOnClickListener {
                     scope.launch { loadNextEpisode() }
                }
                .setEnabled(playlist != null && activeEpisode != null && playlist.indexOf(activeEpisode) < playlist.size - 1)
                .build()

            actionStripBuilder.addAction(exitAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .addAction(openSeekAction)
        }

        return NavigationTemplate.Builder()
            .setActionStrip(actionStripBuilder.build())
            .setBackgroundColor(CarColor.createCustom(android.graphics.Color.BLACK, android.graphics.Color.BLACK))
            .build()
    }
    
    private fun seekBack() {
        player?.let { p ->
            val newPos = p.currentPosition - 30_000 // -30 seconds
            p.seekTo(if (newPos < 0) 0 else newPos)
            updateStatus("-30s")
        }
    }

    private suspend fun loadNextEpisode() {
        if (playlist == null || activeEpisode == null) {
             showToast("No playlist available")
             return
        }
        
         
         // Fix: Episode class does not have 'id'. Use 'data' or calculate hash.
         val currentIndex = playlist.indexOfFirst { it.data == activeEpisode!!.data } // Comparing by data url is safest
         if (currentIndex != -1 && currentIndex < playlist.size - 1) {
             val nextEp = playlist[currentIndex + 1]
             activeEpisode = nextEp
             // Generate a consistent ID 
             // We try to get the full loadResponse if available to calculate accurate ID
             val data = getLoadResponse() 
             val nextId = CarHelper.generateConsistentEpisodeId(nextEp, data) ?: nextEp.data.hashCode()
             currentEpisodeId = nextId
             // Force start from beginning for next episode
             startTime = 0L

             showToast("Loading: ${nextEp.name}")
             
             // Re-use logic to load media
              withContext(Dispatchers.Main) {
                 // Determine if we have a direct URL (download) or need to fetch links
                  val downloadEp = carContext.getKey<VideoDownloadHelper.DownloadEpisodeCached>(
                      DOWNLOAD_EPISODE_CACHE,
                      nextId.toString()
                  )
                 
                 if (downloadEp != null) {
                     // Verify file exists? For now assume yes if cached.
                     // We need the file URI. The original PlayerCarScreen invocation usually gets a fileUri passed in.
                     // But here we are switching. We might need to find the file path.
                     // Simplified: If in download mode, try to find the download.
                     // If streaming, just fetch links.
                     // For now, assume streaming or that loadMedia handles it via APIRepository if needed.
                     // But wait, loadMedia takes a URL. Episode.data is the url for load().
                     
                     loadMedia(nextEp.data)
                 } else {
                     loadMedia(nextEp.data)
                 }
            }
        } else {
            showToast("No more episodes")
        }
    }

    private fun seekForward() {
        player?.let { p ->
            val newPos = p.currentPosition + 30_000 // +30 seconds
            val duration = p.duration
            p.seekTo(if (newPos > duration) duration else newPos)
            updateStatus("+30s")
        }
    }
    
    private fun updateStatus(status: String) {
         if(status != "Playing") {
             showToast(status)
         }
    }
    

}

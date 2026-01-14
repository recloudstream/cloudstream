package com.lagradost.cloudstream3.ui.car

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
import androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
import androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.Episode

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.fixVisual
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.delay

class PlayerCarScreen(
    carContext: CarContext,
    val item: SearchResponse? = null,
    val loadResponse: LoadResponse? = null,
    val selectedEpisode: Episode? = null,
    val playlist: List<Episode>? = null,
    val startTime: Long = 0L,
    val fileUri: String? = null,
    val videoId: Int? = null,
    val parentId: Int? = null
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
        updateStatus("Caricamento...")
        scope.launch {
             // If we already have the LoadResponse (passed from Details), use it.
             // Otherwise, fetch it using item.url
             val data: LoadResponse? = if (loadResponse != null) {
                 loadResponse
             } else if (item != null) {
                 val apiName = item.apiName
                 val api = getApiFromNameNull(apiName) ?: return@launch
                 val repo = APIRepository(api)
                 when(val result = repo.load(item.url)) {
                     is Resource.Success -> result.value
                     else -> null
                 }
             } else {
                 null
             }

             if (data == null) {
                 showToast("Impossibile caricare i dettagli")
                 return@launch
             }

             val apiName = data.apiName
             val api = getApiFromNameNull(apiName) ?: return@launch

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
                     showToast("Nessun contenuto riproducibile trovato")
                     return@launch
                 }

                 // Load links using the API
                 val success = api.loadLinks(urlToLoad, false, {}, { link ->
                     links.add(link)
                 })
                 
                 if(links.isNotEmpty()) {
                     val bestLink = links.sortedByDescending { it.quality }.first()
                     startPlayback(bestLink)
                 } else {
                     showToast("Nessun link trovato")
                 }
             } catch (e: Exception) {
                 showToast("Errore caricamento link: ${e.message}")
                 e.printStackTrace()
             }

             // Resolve IDs for syncing (Exact match to Main App's ResultViewModel2.getLoadResponseIdFromUrl)
             // Logic: url.replace(mainUrl, "").replace("/", "").hashCode()
             
             val mainUrl = api.mainUrl
             val idFromUrl = data.url.replace(mainUrl, "").replace("/", "").hashCode()
             
             // Priority: 1. Item ID (if available from Home/Search and valid)
             //           2. Data URL Hashcode (using strict cleaning logic)
             currentParentId = item?.id ?: idFromUrl

             if (data is TvSeriesLoadResponse && activeEpisode != null) {
                 // Episode doesn't have an ID property, so we use the data (url) hashcode
                 // Note: Episodes might not use the mainUrl cleanup in the same way, but usually depend on their own data string.
                 // In PlayerGeneratorViewModel/RepoLinkGenerator, episodes use 'current.id' which comes from ResultEpisode.
                 // ResultEpisode is built using 'id' from LoadResponse.
                 // So we should apply the same cleaning to episode data if it's a URL.
                 // However, activeEpisode.data is usually the direct link. 
                 // Let's stick to .hashCode() of the data string for episodes as a reasonable unique key if explicit ID is known to be missing.
                 currentEpisodeId = activeEpisode!!.data.hashCode()
             } else {
                 // For movies, we use the parent ID
                 currentEpisodeId = currentParentId
             }
             
             Log.d("PlayerCarScreen", "Resolved IDs (STRICT) - Name: ${data.name} | Parent: $currentParentId | Episode: $currentEpisodeId | Type: ${data.javaClass.simpleName} | ItemID: ${item?.id} | IdFromUrl: $idFromUrl")
        }
    }

    private fun saveProgress() {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        
        Log.d("PlayerCarScreen", "saveProgress - Pos: $pos, Dur: $dur, IsPlaying: $isPlaying")

        if (dur <= 0) return

        // 1. Save detailed position (setViewPos)
        // Only if we have a valid ID. For Movies, currentEpisodeId might be enough.
        // For Episodes, we need the specific episode ID.
        if (currentEpisodeId != null) {
            DataStoreHelper.setViewPos(currentEpisodeId, pos, dur)
            Log.d("PlayerCarScreen", "Called setViewPos for EpisodeID: $currentEpisodeId")
        } else {
             Log.d("PlayerCarScreen", "Skipping setViewPos (EpisodeID is null)")
        }

        // 2. Save "Last Watched" for Continue Watching list
        // This requires parentId.
        if (currentParentId != null) {
             // Logic to check if finished (95% rule)
             val percentage = pos * 100L / dur
             if (percentage > 95) {
                 // Mark as finished / remove from resume
                 DataStoreHelper.removeLastWatched(currentParentId)
                 Log.d("PlayerCarScreen", "Removed Last Watched (Finished)")
             } else {
                 // Update resume state
                 val epNum = activeEpisode?.episode
                 val seasonNum = activeEpisode?.season
                 
                 DataStoreHelper.setLastWatched(
                     parentId = currentParentId,
                     episodeId = currentEpisodeId,
                     episode = epNum,
                     season = seasonNum,
                     isFromDownload = false,
                     updateTime = System.currentTimeMillis()
                 )
                 Log.d("PlayerCarScreen", "Set Last Watched for ParentID: $currentParentId")
             }
        } else {
            Log.d("PlayerCarScreen", "Skipping setLastWatched (ParentID is null)")
        }
    }

    private suspend fun startPlayback(link: Any) {
        val url = when(link) {
            is ExtractorLink -> link.url
            is String -> link
            else -> return
        }

        withContext(Dispatchers.Main) {
            updateStatus("Avvio riproduzione...")
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
                 // Initialize MediaSession
                 if (mediaSession == null) {
                     mediaSession = MediaSession.Builder(carContext, p)
                         .setCallback(object : MediaSession.Callback {
                             override fun onConnect(
                                 session: MediaSession,
                                 controller: MediaSession.ControllerInfo
                             ): MediaSession.ConnectionResult {
                                 return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                             }
                         })
                         .build()
                 }
                 
                 p.addListener(object : Player.Listener {
                     override fun onIsPlayingChanged(isPlaying: Boolean) {
                         this@PlayerCarScreen.isPlaying = isPlaying
                         invalidate()
                     }
                 })

                 surface?.let { p.setVideoSurface(it) }
                 p.videoScalingMode = resizeMode
                 
                 val mediaItemBuilder = MediaItem.Builder().setUri(url)
                 if (link is ExtractorLink && link.isM3u8) {
                     mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                 }
                 
                 p.setMediaItem(mediaItemBuilder.build())
                 p.prepare()
                 if (startTime > 0L) {
                     p.seekTo(startTime)
                 }
                 p.play()
                 updateStatus("In riproduzione")
 
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

            actionStripBuilder.addAction(backToMenuAction)
                .addAction(seekBackAction)
                .addAction(seekForwardAction)
                
        } else {
            // DEFAULT MODE: [Back (Exit)], [Play], [Resize], [Seek Controls]
            
            val exitAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_baseline_arrow_back_24)).build())
                .setOnClickListener { screenManager.pop() }
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
                    showToast(if (resizeMode == VIDEO_SCALING_MODE_SCALE_TO_FIT) "Adatta allo schermo" else "Riempi schermo")
                }
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

            actionStripBuilder.addAction(exitAction)
                .addAction(playPauseAction)
                .addAction(resizeAction)
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

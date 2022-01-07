package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.SubtitleView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.MimeTypes
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import java.io.File
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession

const val TAG = "CS3ExoPlayer"

class CS3IPlayer : IPlayer {
    private var isPlaying = false
    private var exoPlayer: ExoPlayer? = null

    /** Cache */
    private val cacheSize = 300L * 1024L * 1024L // 300 mb TODO MAKE SETTING
    private val seekActionTime = 30000L

    private var ignoreSSL: Boolean = true
    private var simpleCache: SimpleCache? = null
    private var playBackSpeed: Float = 1.0f

    private var lastMuteVolume: Float = 1.0f

    private var currentLink: ExtractorLink? = null
    private var currentDownloadedFile: ExtractorUri? = null
    private var hasUsedFirstRender = false

    private var currentWindow: Int = 0
    private var playbackPosition: Long = 0

    private val subtitleHelper = PlayerSubtitleHelper()

    override fun getDuration(): Long? = exoPlayer?.duration
    override fun getPosition(): Long? = exoPlayer?.currentPosition
    override fun getIsPlaying(): Boolean = isPlaying
    override fun getPlaybackSpeed(): Float = playBackSpeed

    /**
     * Tracks reported to be used by exoplayer, since sometimes it has a mind of it's own when selecting subs.
     * String = lowercase language as set by .setLanguage("_$langId")
     * Boolean = if it's active
     * */
    private var exoPlayerSelectedTracks = listOf<Pair<String, Boolean>>()

    /** isPlaying */
    private var updateIsPlaying: ((Pair<CSPlayerLoading, CSPlayerLoading>) -> Unit)? = null
    private var requestAutoFocus: (() -> Unit)? = null
    private var playerError: ((Exception) -> Unit)? = null

    /** width x height */
    private var playerDimensionsLoaded: ((Pair<Int, Int>) -> Unit)? = null

    /** used for playerPositionChanged */
    private var requestedListeningPercentages: List<Int>? = null

    /** Fired when seeking the player or on requestedListeningPercentages,
     * used to make things appear on que
     * position, duration */
    private var playerPositionChanged: ((Pair<Long, Long>) -> Unit)? = null

    private var nextEpisode: (() -> Unit)? = null
    private var prevEpisode: (() -> Unit)? = null

    private var playerUpdated: ((Any?) -> Unit)? = null

    override fun initCallbacks(
        playerUpdated: (Any?) -> Unit,
        updateIsPlaying: ((Pair<CSPlayerLoading, CSPlayerLoading>) -> Unit)?,
        requestAutoFocus: (() -> Unit)?,
        playerError: ((Exception) -> Unit)?,
        playerDimensionsLoaded: ((Pair<Int, Int>) -> Unit)?,
        requestedListeningPercentages: List<Int>?,
        playerPositionChanged: ((Pair<Long, Long>) -> Unit)?,
        nextEpisode: (() -> Unit)?,
        prevEpisode: (() -> Unit)?
    ) {
        this.playerUpdated = playerUpdated
        this.updateIsPlaying = updateIsPlaying
        this.requestAutoFocus = requestAutoFocus
        this.playerError = playerError
        this.playerDimensionsLoaded = playerDimensionsLoaded
        this.requestedListeningPercentages = requestedListeningPercentages
        this.playerPositionChanged = playerPositionChanged
        this.nextEpisode = nextEpisode
        this.prevEpisode = prevEpisode
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleHelper.initSubtitles(subView, subHolder, style)
    }

    override fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink?,
        data: ExtractorUri?,
        startPosition: Long?,
        subtitles: Set<SubtitleData>
    ) {
        Log.i(TAG, "loadPlayer")
        if (sameEpisode) {
            saveData()
        } else {
            currentSubtitles = null
            playbackPosition = 0
        }

        startPosition?.let {
            playbackPosition = it
        }

        // we want autoplay because of TV and UX
        isPlaying = true

        // release the current exoplayer and cache
        releasePlayer()
        if (link != null) {
            loadOnlinePlayer(context, link)
        } else if (data != null) {
            loadOfflinePlayer(context, data)
        }
    }

    override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
        Log.i(TAG, "setActiveSubtitles ${subtitles.size}")
        subtitleHelper.setAllSubtitles(subtitles)
    }

    var currentSubtitles : SubtitleData? = null
    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        Log.i(TAG,"setPreferredSubtitles init $subtitle")
        currentSubtitles = subtitle
        return (exoPlayer?.trackSelector as? DefaultTrackSelector?)?.let { trackSelector ->
            val name = subtitle?.name
            if (name.isNullOrBlank()) {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setPreferredTextLanguage(null)
                )
            } else {
                when (subtitleHelper.subtitleStatus(subtitle)) {
                    SubtitleStatus.REQUIRES_RELOAD -> {
                        Log.i(TAG,"setPreferredSubtitles REQUIRES_RELOAD")
                        return@let true
                        // reloadPlayer(context)
                    }
                    SubtitleStatus.IS_ACTIVE -> {
                        Log.i(TAG,"setPreferredSubtitles IS_ACTIVE")

                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setPreferredTextLanguage("_$name")
                        )
                    }
                    SubtitleStatus.NOT_FOUND -> {
                        // not found
                        Log.i(TAG,"setPreferredSubtitles NOT_FOUND")
                        return@let true
                    }
                }
            }
            return false
        } ?: false
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        return subtitleHelper.getAllSubtitles().firstOrNull { sub ->
            exoPlayerSelectedTracks.any {
                // The replace is needed as exoplayer translates _ to -
                // Also we prefix the languages with _
                it.second && it.first.replace("-", "") .equals(
                    sub.name.replace("-", ""),
                    ignoreCase = true
                )
            }
        }
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        subtitleHelper.setSubStyle(style)
    }

    private fun saveData() {
        Log.i(TAG, "saveData")
        updatedTime()

        exoPlayer?.let { exo ->
            playbackPosition = exo.currentPosition
            currentWindow = exo.currentWindowIndex
            isPlaying = exo.isPlaying
        }
    }

    private fun releasePlayer() {
        Log.i(TAG, "releasePlayer")

        updatedTime()

        exoPlayer?.release()
        simpleCache?.release()

        exoPlayer = null
        simpleCache = null
    }

    override fun onStop() {
        Log.i(TAG, "onStop")

        saveData()
        exoPlayer?.pause()
        releasePlayer()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        saveData()
        exoPlayer?.pause()
        releasePlayer()
    }

    override fun onResume(context: Context) {
        if (exoPlayer == null)
            reloadPlayer(context)
    }

    override fun release() {
        releasePlayer()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        playBackSpeed = speed
    }

    companion object {
        private fun createOnlineSource(link: ExtractorLink): DataSource.Factory {
            return DefaultHttpDataSource.Factory().apply {
                setUserAgent(USER_AGENT)
                val headers = mapOf(
                    "referer" to link.referer,
                    "accept" to "*/*",
                    "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
                    "sec-ch-ua-mobile" to "?0",
                    "sec-fetch-user" to "?1",
                    "sec-fetch-mode" to "navigate",
                    "sec-fetch-dest" to "video"
                ) + link.headers // Adds the headers from the provider, e.g Authorization
                setDefaultRequestProperties(headers)

                //https://stackoverflow.com/questions/69040127/error-code-io-bad-http-status-exoplayer-android
                setAllowCrossProtocolRedirects(true)
            }
        }

        private fun Context.createOfflineSource(): DataSource.Factory {
            return DefaultDataSourceFactory(this, USER_AGENT)
        }

        private fun getSubSources(
            onlineSourceFactory: DataSource.Factory?,
            offlineSourceFactory: DataSource.Factory?,
            subHelper: PlayerSubtitleHelper,
        ): Pair<List<SingleSampleMediaSource>, List<SubtitleData>> {
            val activeSubtitles = ArrayList<SubtitleData>()
            val subSources = subHelper.getAllSubtitles().mapNotNull { sub ->
                val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(sub.mimeType)
                    .setLanguage("_${sub.name}")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                when (sub.origin) {
                    SubtitleOrigin.DOWNLOADED_FILE -> {
                        if (offlineSourceFactory != null) {
                            activeSubtitles.add(sub)
                            SingleSampleMediaSource.Factory(offlineSourceFactory)
                                .createMediaSource(subConfig, C.TIME_UNSET)
                        } else {
                            null
                        }
                    }
                    SubtitleOrigin.URL -> {
                        if (onlineSourceFactory != null) {
                            activeSubtitles.add(sub)
                            SingleSampleMediaSource.Factory(onlineSourceFactory)
                                .createMediaSource(subConfig, C.TIME_UNSET)
                        } else {
                            null
                        }
                    }
                    SubtitleOrigin.OPEN_SUBTITLES -> {
                        // TODO
                        throw NotImplementedError()
                    }
                }
            }
            println("SUBSRC: ${subSources.size} activeSubtitles : ${activeSubtitles.size} of ${subHelper.getAllSubtitles().size} ")
            return Pair(subSources, activeSubtitles)
        }

        private fun getCache(context: Context, cacheSize: Long): SimpleCache? {
            return try {
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(
                    File(
                        context.cacheDir, "exoplayer"
                    ).also { it.deleteOnExit() }, // Ensures always fresh file
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    databaseProvider
                )
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        private fun getMediaItemBuilder(mimeType: String):
                MediaItem.Builder {
            return MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(mimeType)
        }

        private fun getMediaItem(mimeType: String, uri: Uri): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(uri).build()
        }

        private fun getMediaItem(mimeType: String, url: String): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(url).build()
        }

        private fun getTrackSelector(context: Context): TrackSelector {
            val trackSelector = DefaultTrackSelector(context)
            trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(context)
                // .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                .clearSelectionOverrides()
                .build()
            return trackSelector
        }

        private fun buildExoPlayer(
            context: Context,
            mediaItem: MediaItem,
            subSources: List<SingleSampleMediaSource>,
            currentWindow: Int,
            playbackPosition: Long,
            playBackSpeed: Float,
            playWhenReady: Boolean = true,
            cacheFactory: CacheDataSource.Factory? = null,
            trackSelector: TrackSelector? = null,
        ): ExoPlayer {
            val exoPlayerBuilder =
                ExoPlayer.Builder(context)
                    .setTrackSelector(trackSelector ?: getTrackSelector(context))

            val videoMediaSource =
                (if (cacheFactory == null) DefaultMediaSourceFactory(context) else DefaultMediaSourceFactory(
                    cacheFactory
                )).createMediaSource(
                    mediaItem
                )

            return exoPlayerBuilder.build().apply {
                setPlayWhenReady(playWhenReady)
                seekTo(currentWindow, playbackPosition)
                setMediaSource(
                    MergingMediaSource(
                        videoMediaSource, *subSources.toTypedArray()
                    ),
                    playbackPosition
                )
                setHandleAudioBecomingNoisy(true)
                setPlaybackSpeed(playBackSpeed)
            }
        }
    }

    fun updatedTime() {
        val position = exoPlayer?.currentPosition
        val duration = exoPlayer?.contentDuration
        if (duration != null && position != null) {
            playerPositionChanged?.invoke(Pair(position, duration))
        }
    }

    override fun seekTime(time: Long) {
        exoPlayer?.seekTime(time)
    }

    override fun seekTo(time: Long) {
        updatedTime()
        exoPlayer?.seekTo(time)
    }

    private fun ExoPlayer.seekTime(time: Long) {
        updatedTime()
        seekTo(currentPosition + time)
    }

    override fun handleEvent(event: CSPlayerEvent) {
        Log.i(TAG, "handleEvent ${event.name}")
        try {
            exoPlayer?.apply {
                when (event) {
                    CSPlayerEvent.Play -> {
                        play()
                    }
                    CSPlayerEvent.Pause -> {
                        pause()
                    }
                    CSPlayerEvent.ToggleMute -> {
                        if (volume <= 0) {
                            //is muted
                            volume = lastMuteVolume
                        } else {
                            // is not muted
                            lastMuteVolume = volume
                            volume = 0f
                        }
                    }
                    CSPlayerEvent.PlayPauseToggle -> {
                        if (isPlaying) {
                            pause()
                        } else {
                            play()
                        }
                    }
                    CSPlayerEvent.SeekForward -> seekTime(seekActionTime)
                    CSPlayerEvent.SeekBack -> seekTime(-seekActionTime)
                    CSPlayerEvent.NextEpisode -> nextEpisode?.invoke()
                    CSPlayerEvent.PrevEpisode -> prevEpisode?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleEvent error", e)
            playerError?.invoke(e)
        }
    }

    private fun loadExo(
        context: Context,
        mediaItem: MediaItem,
        subSources: List<SingleSampleMediaSource>,
        cacheFactory: CacheDataSource.Factory? = null
    ) {
        Log.i(TAG, "loadExo")
        try {
            hasUsedFirstRender = false

            // ye this has to be a val for whatever reason
            // this makes no sense
            exoPlayer = buildExoPlayer(
                context,
                mediaItem,
                subSources,
                currentWindow,
                playbackPosition,
                playBackSpeed,
                playWhenReady = isPlaying, // this keep the current state of the player
                cacheFactory = cacheFactory
            )

            playerUpdated?.invoke(exoPlayer)
            exoPlayer?.prepare()

            exoPlayer?.let { exo ->
                updateIsPlaying?.invoke(
                    Pair(
                        CSPlayerLoading.IsBuffering,
                        CSPlayerLoading.IsBuffering
                    )
                )
                isPlaying = exo.isPlaying
            }
            exoPlayer?.addListener(object : Player.Listener {
                /**
                 * Records the current used subtitle/track. Needed as exoplayer seems to have loose track language selection.
                 * */
                override fun onTracksInfoChanged(tracksInfo: TracksInfo) {
                    exoPlayerSelectedTracks =
                        tracksInfo.trackGroupInfos.mapNotNull { it.trackGroup.getFormat(0).language?.let { lang -> lang to it.isSelected } }
                    super.onTracksInfoChanged(tracksInfo)
                }

                override fun onCues(cues: MutableList<Cue>) {
                    Log.i(TAG, "CUES: ${cues.size}")
                    if(cues.size > 0) {
                        Log.i(TAG, "CUES SAY: ${cues.first().text}")
                    }
                    super.onCues(cues)
                }

                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    exoPlayer?.let { exo ->
                        updateIsPlaying?.invoke(
                            Pair(
                                if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused,
                                if (playbackState == Player.STATE_BUFFERING) CSPlayerLoading.IsBuffering else if (exo.isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                            )
                        )
                        isPlaying = exo.isPlaying
                    }

                    if (playWhenReady) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                requestAutoFocus?.invoke()
                            }
                            Player.STATE_ENDED -> {
                                handleEvent(CSPlayerEvent.NextEpisode)
                            }
                            Player.STATE_BUFFERING -> {
                                updatedTime()
                            }
                            Player.STATE_IDLE -> {
                                // IDLE
                            }
                            else -> Unit
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerError?.invoke(error)

                    super.onPlayerError(error)
                }

                override fun onRenderedFirstFrame() {
                    updatedTime()
                    if (!hasUsedFirstRender) { // this insures that we only call this once per player load
                        Log.i(TAG, "Rendered first frame")
                        setPreferredSubtitles(currentSubtitles)
                        hasUsedFirstRender = true
                        val format = exoPlayer?.videoFormat
                        val width = format?.width
                        val height = format?.height
                        if (height != null && width != null) {
                            playerDimensionsLoaded?.invoke(Pair(width, height))
                            updatedTime()
                            exoPlayer?.apply {
                                requestedListeningPercentages?.forEach { percentage ->
                                    createMessage { _, _ ->
                                        updatedTime()
                                    }
                                        .setLooper(Looper.getMainLooper())
                                        .setPosition( /* positionMs= */contentDuration * percentage / 100)
                                        //   .setPayload(customPayloadData)
                                        .setDeleteAfterDelivery(false)
                                        .send()
                                }
                            }
                        }
                    }
                    super.onRenderedFirstFrame()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "loadExo error", e)
            playerError?.invoke(e)
        }
    }

    private fun loadOfflinePlayer(context: Context, data: ExtractorUri) {
        Log.i(TAG, "loadOfflinePlayer")
        try {
            currentDownloadedFile = data

            val mediaItem = getMediaItem(MimeTypes.VIDEO_MP4, data.uri)
            val offlineSourceFactory = context.createOfflineSource()
            val (subSources, activeSubtitles) = getSubSources(
                offlineSourceFactory,
                offlineSourceFactory,
                subtitleHelper
            )
            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())
            loadExo(context, mediaItem, subSources)
        } catch (e: Exception) {
            Log.e(TAG, "loadOfflinePlayer error", e)
            playerError?.invoke(e)
        }
    }

    private fun loadOnlinePlayer(context: Context, link: ExtractorLink) {
        Log.i(TAG, "loadOnlinePlayer")
        try {
            currentLink = link

            if (ignoreSSL) {
                // Disables ssl check
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(SSLTrustManager()), java.security.SecureRandom())
                sslContext.createSSLEngine()
                HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                    true
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            }

            val mime = if (link.isM3u8) {
                MimeTypes.APPLICATION_M3U8
            } else {
                MimeTypes.VIDEO_MP4
            }
            val mediaItem = getMediaItem(mime, link.url)

            val onlineSourceFactory = createOnlineSource(link)
            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                onlineSourceFactory,
                offlineSourceFactory,
                subtitleHelper
            )
            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())

            simpleCache = getCache(context, cacheSize)

            val cacheFactory = CacheDataSource.Factory().apply {
                simpleCache?.let { setCache(it) }
                setUpstreamDataSourceFactory(onlineSourceFactory)
            }

            loadExo(context, mediaItem, subSources, cacheFactory)
        } catch (e: Exception) {
            Log.e(TAG, "loadOnlinePlayer error", e)
            playerError?.invoke(e)
        }
    }

    override fun reloadPlayer(context: Context) {
        Log.i(TAG, "reloadPlayer")

        exoPlayer?.release()
        currentLink?.let {
            loadOnlinePlayer(context, it)
        } ?: currentDownloadedFile?.let {
            loadOfflinePlayer(context, it)
        }
    }
}
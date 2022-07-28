package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.text.TextRenderer
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
import com.google.android.exoplayer2.video.VideoSize
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTwoLettersToLanguage
import java.io.File
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession

const val TAG = "CS3ExoPlayer"

/** Cache */

class CS3IPlayer : IPlayer {
    private var isPlaying = false
    private var exoPlayer: ExoPlayer? = null
    var cacheSize = 0L
    var simpleCacheSize = 0L
    var videoBufferMs = 0L

    private val seekActionTime = 30000L

    private var ignoreSSL: Boolean = true
    private var playBackSpeed: Float = 1.0f

    private var lastMuteVolume: Float = 1.0f

    private var currentLink: ExtractorLink? = null
    private var currentDownloadedFile: ExtractorUri? = null
    private var hasUsedFirstRender = false

    private var currentWindow: Int = 0
    private var playbackPosition: Long = 0

    private val subtitleHelper = PlayerSubtitleHelper()

    /**
     * This is a way to combine the MediaItem and its duration for the concatenating MediaSource.
     * @param durationUs does not matter if only one slice is present, since it will not concatenate
     * */
    data class MediaItemSlice(
        val mediaItem: MediaItem,
        val durationUs: Long
    )

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
    private var subtitlesUpdates: (() -> Unit)? = null

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
    private var embeddedSubtitlesFetched: ((List<SubtitleData>) -> Unit)? = null

    override fun releaseCallbacks() {
        playerUpdated = null
        updateIsPlaying = null
        requestAutoFocus = null
        playerError = null
        playerDimensionsLoaded = null
        requestedListeningPercentages = null
        playerPositionChanged = null
        nextEpisode = null
        prevEpisode = null
        subtitlesUpdates = null
        embeddedSubtitlesFetched = null
        requestSubtitleUpdate = null
    }

    override fun initCallbacks(
        playerUpdated: (Any?) -> Unit,
        updateIsPlaying: ((Pair<CSPlayerLoading, CSPlayerLoading>) -> Unit)?,
        requestAutoFocus: (() -> Unit)?,
        playerError: ((Exception) -> Unit)?,
        playerDimensionsLoaded: ((Pair<Int, Int>) -> Unit)?,
        requestedListeningPercentages: List<Int>?,
        playerPositionChanged: ((Pair<Long, Long>) -> Unit)?,
        nextEpisode: (() -> Unit)?,
        prevEpisode: (() -> Unit)?,
        subtitlesUpdates: (() -> Unit)?,
        embeddedSubtitlesFetched: ((List<SubtitleData>) -> Unit)?,
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
        this.subtitlesUpdates = subtitlesUpdates
        this.embeddedSubtitlesFetched = embeddedSubtitlesFetched
    }

    // I know, this is not a perfect solution, however it works for fixing subs
    private fun reloadSubs() {
        exoPlayer?.applicationLooper?.let {
            try {
                Handler(it).post {
                    try {
                        seekTime(1L)
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
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
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean?
    ) {
        Log.i(TAG, "loadPlayer")
        if (sameEpisode) {
            saveData()
        } else {
            currentSubtitles = subtitle
            playbackPosition = 0
        }

        startPosition?.let {
            playbackPosition = it
        }

        // we want autoplay because of TV and UX
        isPlaying = autoPlay ?: isPlaying

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

    var currentSubtitles: SubtitleData? = null

    /**
     * @return True if the player should be reloaded
     * */
    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        Log.i(TAG, "setPreferredSubtitles init $subtitle")
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
                        Log.i(TAG, "setPreferredSubtitles REQUIRES_RELOAD")
                        return@let true
                    }
                    SubtitleStatus.IS_ACTIVE -> {
                        Log.i(TAG, "setPreferredSubtitles IS_ACTIVE")

                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .apply {
                                    if (subtitle.origin == SubtitleOrigin.EMBEDDED_IN_VIDEO)
                                    // The real Language (two letter) is in the url
                                    // No underscore as the .url is the actual exoplayer designated language
                                        setPreferredTextLanguage(subtitle.url)
                                    else
                                        setPreferredTextLanguage("_$name")
                                }
                        )

                        // ugliest code I have written, it seeks 1ms to *update* the subtitles
                        //exoPlayer?.applicationLooper?.let {
                        //    Handler(it).postDelayed({
                        //        seekTime(1L)
                        //    }, 1)
                        //}
                    }
                    SubtitleStatus.NOT_FOUND -> {
                        Log.i(TAG, "setPreferredSubtitles NOT_FOUND")
                        return@let true
                    }
                }
            }
            return false
        } ?: false
    }

    var currentSubtitleOffset: Long = 0

    override fun setSubtitleOffset(offset: Long) {
        currentSubtitleOffset = offset
        currentTextRenderer?.setRenderOffsetMs(offset)
    }

    override fun getSubtitleOffset(): Long {
        return currentSubtitleOffset //currentTextRenderer?.getRenderOffsetMs() ?: currentSubtitleOffset
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        return subtitleHelper.getAllSubtitles().firstOrNull { sub ->
            exoPlayerSelectedTracks.any {
                // When embedded the real language is in .url as the real name is a two letter code
                val realName =
                    if (sub.origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) sub.url else sub.name

                // The replace is needed as exoplayer translates _ to -
                // Also we prefix the languages with _
                it.second && it.first.replace("-", "").equals(
                    realName.replace("-", ""),
                    ignoreCase = true
                )
            }
        }
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        subtitleHelper.setSubStyle(style)
    }

    override fun saveData() {
        Log.i(TAG, "saveData")
        updatedTime()

        exoPlayer?.let { exo ->
            playbackPosition = exo.currentPosition
            currentWindow = exo.currentWindowIndex
            isPlaying = exo.isPlaying
        }
    }

    private fun releasePlayer(saveTime: Boolean = true) {
        Log.i(TAG, "releasePlayer")

        if (saveTime)
            updatedTime()

        exoPlayer?.release()
        //simpleCache?.release()
        currentTextRenderer = null

        exoPlayer = null
        //simpleCache = null
    }

    override fun onStop() {
        Log.i(TAG, "onStop")

        saveData()
        exoPlayer?.pause()
        //releasePlayer()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        saveData()
        exoPlayer?.pause()
        //releasePlayer()
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
        private var simpleCache: SimpleCache? = null

        var requestSubtitleUpdate: (() -> Unit)? = null

        private fun createOnlineSource(link: ExtractorLink): DataSource.Factory {
            val provider = getApiFromNameNull(link.source)
            val interceptor = provider?.getVideoInterceptor(link)

            val source = if (interceptor == null) {
                DefaultHttpDataSource.Factory() //TODO USE app.baseClient
                    .setUserAgent(USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)   //https://stackoverflow.com/questions/69040127/error-code-io-bad-http-status-exoplayer-android
            } else {
                val client = app.baseClient.newBuilder()
                    .addInterceptor(interceptor)
                    .build()
                OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)
            }

            val headers = mapOf(
                "referer" to link.referer,
                "accept" to "*/*",
                "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-fetch-user" to "?1",
                "sec-fetch-mode" to "navigate",
                "sec-fetch-dest" to "video"
            ) + link.headers // Adds the headers from the provider, e.g Authorization

            return source.apply {
                setDefaultRequestProperties(headers)
            }
        }

        private fun Context.createOfflineSource(): DataSource.Factory {
            return DefaultDataSourceFactory(this, USER_AGENT)
        }

        /*private fun getSubSources(
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
        }*/

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

        var currentTextRenderer: CustomTextRenderer? = null

        private fun buildExoPlayer(
            context: Context,
            mediaItemSlices: List<MediaItemSlice>,
            subSources: List<SingleSampleMediaSource>,
            currentWindow: Int,
            playbackPosition: Long,
            playBackSpeed: Float,
            subtitleOffset: Long,
            cacheSize: Long,
            videoBufferMs: Long,
            playWhenReady: Boolean = true,
            cacheFactory: CacheDataSource.Factory? = null,
            trackSelector: TrackSelector? = null,
        ): ExoPlayer {
            val exoPlayerBuilder =
                ExoPlayer.Builder(context)
                    .setRenderersFactory { eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput ->
                        DefaultRenderersFactory(context).createRenderers(
                            eventHandler,
                            videoRendererEventListener,
                            audioRendererEventListener,
                            textRendererOutput,
                            metadataRendererOutput
                        ).map {
                            if (it is TextRenderer) {
                                currentTextRenderer = CustomTextRenderer(
                                    subtitleOffset,
                                    textRendererOutput,
                                    eventHandler.looper,
                                    CustomSubtitleDecoderFactory()
                                )
                                currentTextRenderer!!
                            } else it
                        }.toTypedArray()
                    }
                    .setTrackSelector(trackSelector ?: getTrackSelector(context))
                    .setLoadControl(
                        DefaultLoadControl.Builder()
                            .setTargetBufferBytes(
                                if (cacheSize <= 0) {
                                    DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES
                                } else {
                                    if (cacheSize > Int.MAX_VALUE) Int.MAX_VALUE else cacheSize.toInt()
                                }
                            )
                            .setBufferDurationsMs(
                                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                                if (videoBufferMs <= 0) {
                                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
                                } else {
                                    videoBufferMs.toInt()
                                },
                                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                            ).build()
                    )


            val factory =
                if (cacheFactory == null) DefaultMediaSourceFactory(context)
                else DefaultMediaSourceFactory(cacheFactory)

            // If there is only one item then treat it as normal, if multiple: concatenate the items.
            val videoMediaSource = if (mediaItemSlices.size == 1) {
                factory.createMediaSource(mediaItemSlices.first().mediaItem)
            } else {
                val source = ConcatenatingMediaSource()
                mediaItemSlices.map {
                    source.addMediaSource(
                        // The duration MUST be known for it to work properly, see https://github.com/google/ExoPlayer/issues/4727
                        ClippingMediaSource(
                            factory.createMediaSource(it.mediaItem),
                            it.durationUs
                        )
                    )
                }
                source
            }

            println("PLAYBACK POS $playbackPosition")
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
        mediaSlices: List<MediaItemSlice>,
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
                mediaSlices,
                subSources,
                currentWindow,
                playbackPosition,
                playBackSpeed,
                cacheSize = cacheSize,
                videoBufferMs = videoBufferMs,
                playWhenReady = isPlaying, // this keep the current state of the player
                cacheFactory = cacheFactory,
                subtitleOffset = currentSubtitleOffset
            )

            requestSubtitleUpdate = ::reloadSubs

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
                    fun Format.isSubtitle(): Boolean {
                        return this.sampleMimeType?.contains("video/") == false &&
                                this.sampleMimeType?.contains("audio/") == false
                    }

                    normalSafeApiCall {
                        exoPlayerSelectedTracks =
                            tracksInfo.trackGroupInfos.mapNotNull {
                                val format = it.trackGroup.getFormat(0)
                                if (format.isSubtitle())
                                    format.language?.let { lang -> lang to it.isSelected }
                                else null
                            }

                        val exoPlayerReportedTracks = tracksInfo.trackGroupInfos.mapNotNull {
                            // Filter out unsupported tracks
                            if (it.isSupported)
                                it.trackGroup.getFormat(0)
                            else
                                null
                        }.mapNotNull {
                            // Filter out non subs, already used subs and subs without languages
                            if (!it.isSubtitle() ||
                                // Anything starting with - is not embedded
                                it.language?.startsWith("-") == true ||
                                it.language == null
                            ) return@mapNotNull null
                            return@mapNotNull SubtitleData(
                                // Nicer looking displayed names
                                fromTwoLettersToLanguage(it.language!!) ?: it.language!!,
                                // See setPreferredTextLanguage
                                it.language!!,
                                SubtitleOrigin.EMBEDDED_IN_VIDEO,
                                it.sampleMimeType ?: MimeTypes.APPLICATION_SUBRIP
                            )
                        }

                        embeddedSubtitlesFetched?.invoke(exoPlayerReportedTracks)
                        subtitlesUpdates?.invoke()
                    }
                    super.onTracksInfoChanged(tracksInfo)
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

                    when (playbackState) {
                        Player.STATE_READY -> {
                            onRenderFirst()
                        }
                        else -> {}
                    }


                    if (playWhenReady) {
                        when (playbackState) {
                            Player.STATE_READY -> {

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

                //override fun onCues(cues: MutableList<Cue>) {
                //    super.onCues(cues.map { cue -> cue.buildUpon().setText("Hello world").setSize(Cue.DIMEN_UNSET).build() })
                //}

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    if (isPlaying) {
                        requestAutoFocus?.invoke()
                        onRenderFirst()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    when (playbackState) {
                        Player.STATE_READY -> {

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

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    super.onVideoSizeChanged(videoSize)
                    playerDimensionsLoaded?.invoke(Pair(videoSize.width, videoSize.height))
                }

                override fun onRenderedFirstFrame() {
                    updatedTime()
                    super.onRenderedFirstFrame()
                    onRenderFirst()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "loadExo error", e)
            playerError?.invoke(e)
        }
    }

    fun onRenderFirst() {
        if (!hasUsedFirstRender) { // this insures that we only call this once per player load
            Log.i(TAG, "Rendered first frame")
            val invalid = exoPlayer?.duration?.let { duration ->
                // Only errors short playback when not playing downloaded files
                duration < 20_000L && currentDownloadedFile == null
                        // Concatenated sources (non 1 periodCount) bypasses the invalid check as exoPlayer.duration gives only the current period
                        // If you can get the total time that'd be better, but this is already niche.
                        && exoPlayer?.currentTimeline?.periodCount == 1
                        && exoPlayer?.isCurrentMediaItemLive != true
            } ?: false

            if (invalid) {
                releasePlayer(saveTime = false)
                playerError?.invoke(InvalidFileException("Too short playback"))
                return
            }

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
    }

    private fun loadOfflinePlayer(context: Context, data: ExtractorUri) {
        Log.i(TAG, "loadOfflinePlayer")
        try {
            currentDownloadedFile = data

            val mediaItem = getMediaItem(MimeTypes.VIDEO_MP4, data.uri)
            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                onlineSourceFactory = offlineSourceFactory,
                offlineSourceFactory = offlineSourceFactory,
                subtitleHelper,
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())
            loadExo(context, listOf(MediaItemSlice(mediaItem, Long.MIN_VALUE)), subSources)
        } catch (e: Exception) {
            Log.e(TAG, "loadOfflinePlayer error", e)
            playerError?.invoke(e)
        }
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
                SubtitleOrigin.EMBEDDED_IN_VIDEO -> {
                    if (offlineSourceFactory != null) {
                        activeSubtitles.add(sub)
                        SingleSampleMediaSource.Factory(offlineSourceFactory)
                            .createMediaSource(subConfig, C.TIME_UNSET)
                    } else {
                        null
                    }
                }
            }
        }
        return Pair(subSources, activeSubtitles)
    }

    override fun isActive(): Boolean {
        return exoPlayer != null
    }

    private fun loadOnlinePlayer(context: Context, link: ExtractorLink) {
        Log.i(TAG, "loadOnlinePlayer $link")
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

            val mediaItems = if (link is ExtractorLinkPlayList) {
                link.playlist.map {
                    MediaItemSlice(getMediaItem(mime, it.url), it.durationUs)
                }
            } else {
                listOf(
                    // Single sliced list with unset length
                    MediaItemSlice(getMediaItem(mime, link.url), Long.MIN_VALUE)
                )
            }

            val onlineSourceFactory = createOnlineSource(link)
            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                onlineSourceFactory = onlineSourceFactory,
                offlineSourceFactory = offlineSourceFactory,
                subtitleHelper
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())

            if (simpleCache == null)
                simpleCache = getCache(context, simpleCacheSize)

            val cacheFactory = CacheDataSource.Factory().apply {
                simpleCache?.let { setCache(it) }
                setUpstreamDataSourceFactory(onlineSourceFactory)
            }

            loadExo(context, mediaItems, subSources, cacheFactory)
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